const { fetch, Agent } = require("undici");
const { getCache, setCache } = require("../core/store");

const SEARCH_BASE =
  process.env.MOVIEX_SEARCH_BASE ||
  "https://moviexdownloadv3.abrahamdw882.workers.dev/api";

const CONTENT_BASE =
  process.env.MOVIEX_CONTENT_BASE ||
  "https://moviexdownload.koyeb.app";

const dispatcher = new Agent({
  connect: { timeout: 15000 },
  headersTimeout: 30000,
  bodyTimeout: 30000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  accept: "application/json,text/plain,*/*",
  "accept-language": "en-US,en;q=0.9"
};

const SEARCH_CACHE_TTL = 10 * 60 * 1000;
const INFO_CACHE_TTL = 10 * 60 * 1000;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

function baseSeriesTitle(value) {
  return clean(value)
    .replace(/\s+S\d{1,3}(?:\s*-\s*S\d{1,3})?$/i, "")
    .replace(/\s+Season\s+\d{1,3}$/i, "")
    .trim();
}

function formatSize(bytes) {
  let value = Number(bytes || 0);
  if (!Number.isFinite(value) || value <= 0) return "";

  const units = ["B", "KB", "MB", "GB", "TB"];
  let index = 0;

  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }

  return `${value >= 100 ? value.toFixed(0) : value >= 10 ? value.toFixed(1) : value.toFixed(2)} ${units[index]}`;
}

async function requestJson(url, options = {}) {
  const retries = Math.max(1, Number(options.retries || 3));
  let lastError;

  for (let attempt = 1; attempt <= retries; attempt++) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30000);

    try {
      const response = await fetch(url, {
        dispatcher,
        headers: HEADERS,
        redirect: "follow",
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error(`MovieX returned HTTP ${response.status}`);
      }

      const json = await response.json();

      if (json?.status !== "success") {
        throw new Error(
          json?.message ||
          `MovieX API returned status ${json?.status || "unknown"}`
        );
      }

      return json;
    } catch (error) {
      lastError = error;
      if (attempt < retries) {
        await sleep(attempt * 800);
      }
    } finally {
      clearTimeout(timeout);
    }
  }

  throw lastError || new Error("MovieX request failed");
}

function makeTitleUrl(id, type = "movie") {
  return `moviex://title/${encodeURIComponent(String(id))}?type=${encodeURIComponent(type)}`;
}

function makeMediaUrl(
  id,
  {
    kind = "movie",
    season = 0,
    episode = 0,
    quality = 0,
    step = "final"
  } = {}
) {
  const params = new URLSearchParams();
  params.set("kind", kind);
  if (season) params.set("season", String(season));
  if (episode) params.set("episode", String(episode));
  if (quality) params.set("quality", String(quality));
  if (step) params.set("step", step);

  return `moviex://media/${encodeURIComponent(String(id))}?${params.toString()}`;
}

function parseMovieXUrl(value) {
  try {
    const url = new URL(String(value || ""));
    if (url.protocol !== "moviex:") return null;

    return {
      section: url.hostname,
      id: decodeURIComponent(url.pathname.replace(/^\//, "")),
      type: url.searchParams.get("type") || null,
      kind: url.searchParams.get("kind") || null,
      season: Number(url.searchParams.get("season") || 0),
      episode: Number(url.searchParams.get("episode") || 0),
      quality: Number(url.searchParams.get("quality") || 0),
      step: url.searchParams.get("step") || "final"
    };
  } catch {
    return null;
  }
}

function isMovieXTitleUrl(value) {
  const parsed = parseMovieXUrl(value);
  return Boolean(parsed && parsed.section === "title" && parsed.id);
}

function isMovieXMediaUrl(value) {
  const parsed = parseMovieXUrl(value);
  return Boolean(parsed && parsed.section === "media" && parsed.id);
}

async function searchMovieX(query) {
  const normalized = clean(query).toLowerCase();
  const cacheKey = `moviex:search:${normalized}:v1`;
  const cached = getCache(cacheKey);
  if (cached) return cached;

  const json = await requestJson(
    `${SEARCH_BASE}/search/${encodeURIComponent(clean(query))}`
  );

  const items = Array.isArray(json?.data?.items)
    ? json.data.items
    : [];

  const deduped = [];
  const seenIds = new Set();

  for (const item of items) {
    const id = String(item?.subjectId || item?.id || "");
    if (!id || seenIds.has(id)) continue;

    seenIds.add(id);

    const type = Number(item.subjectType) === 2 ? "series" : "movie";
    const title = type === "series"
      ? baseSeriesTitle(item.title)
      : clean(item.title);

    if (!title) continue;

    const year = Number(item.year || String(item.releaseDate || "").slice(0, 4)) || null;
    const displayTitle = `${title}${year ? ` (${year})` : ""} • MovieX`;

    deduped.push({
      title,
      cleanTitle: displayTitle,
      year,
      type,
      url: makeTitleUrl(id, type === "series" ? "tv" : "movie"),
      image: item.thumbnail || item?.cover?.url || null,
      provider: "moviex",
      moviexId: id,
      rating: item.imdbRatingValue || null,
      genre: item.genre || null
    });
  }

  const words = normalized
    .replace(/[^a-z0-9]+/g, " ")
    .split(/\s+/)
    .filter(word => word.length >= 2);

  let results = deduped;

  if (words.length) {
    const strong = deduped.filter(item => {
      const title = item.title
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, " ")
        .split(/\s+/);

      return words.every(word => title.includes(word));
    });

    if (strong.length) results = strong;
  }

  setCache(cacheKey, results, SEARCH_CACHE_TTL);
  return results;
}

async function getMovieXInfo(id) {
  const key = `moviex:info:${id}:v1`;
  const cached = getCache(key);
  if (cached) return cached;

  const json = await requestJson(
    `${CONTENT_BASE}/api/info/${encodeURIComponent(String(id))}`
  );

  const value = json?.data || null;
  if (!value?.subject) {
    throw new Error("MovieX returned no title information");
  }

  setCache(key, value, INFO_CACHE_TTL);
  return value;
}

function normalizeSources(payload) {
  const processed = Array.isArray(payload?.processedSources)
    ? payload.processedSources
    : [];

  const downloads = Array.isArray(payload?.downloads)
    ? payload.downloads
    : [];

  const raw = processed.length
    ? processed
    : downloads.map(source => ({
        id: source.id,
        quality: source.resolution,
        size: source.size,
        directUrl: source.url,
        downloadUrl: source.url,
        url: source.url,
        format: "mp4"
      }));

  return raw
    .map(source => ({
      id: source.id || null,
      quality: Number(source.quality || source.resolution || 0),
      size: Number(source.size || 0),
      directUrl: source.directUrl || source.url || null,
      streamUrl: source.streamUrl || null,
      downloadUrl: source.downloadUrl || source.directUrl || source.url || null,
      format: source.format || "mp4"
    }))
    .filter(source => source.downloadUrl || source.directUrl)
    .sort((a, b) => a.quality - b.quality);
}

async function getMovieXSources(id, season = 0, episode = 0) {
  let url = `${CONTENT_BASE}/api/sources/${encodeURIComponent(String(id))}`;

  if (season > 0 && episode > 0) {
    const params = new URLSearchParams({
      season: String(season),
      episode: String(episode)
    });
    url += `?${params.toString()}`;
  }

  const json = await requestJson(url, { retries: 3 });
  const data = json?.data || {};

  return {
    sources: normalizeSources(data),
    captions: Array.isArray(data.captions) ? data.captions : [],
    limited: data.limited === true,
    hasResource: data.hasResource !== false
  };
}

function extractSeasons(info) {
  const candidates =
    info?.resource?.seasons ||
    info?.seasons ||
    info?.subject?.seasons ||
    [];

  if (!Array.isArray(candidates)) return [];

  return candidates
    .map((season, index) => ({
      season: Number(season?.se || season?.season || season?.season_number || index + 1),
      maxEp: Number(
        season?.maxEp ||
        season?.count ||
        (Array.isArray(season?.episodes) ? season.episodes.length : season?.episodes) ||
        0
      )
    }))
    .filter(item => item.season > 0 && item.maxEp > 0)
    .sort((a, b) => a.season - b.season);
}

function pickSource(sources, quality = 0) {
  if (!Array.isArray(sources) || !sources.length) {
    throw new Error("MovieX returned no downloadable sources");
  }

  const wanted = Number(quality || 0);

  if (!wanted) {
    return [...sources].sort((a, b) => b.quality - a.quality)[0];
  }

  const exact = sources.find(source => source.quality === wanted);
  if (exact) return exact;

  return [...sources].sort(
    (a, b) => Math.abs(a.quality - wanted) - Math.abs(b.quality - wanted)
  )[0];
}

async function resolveMovieXUrl(value) {
  const parsed = parseMovieXUrl(value);

  if (!parsed || parsed.section !== "media" || !parsed.id) {
    throw new Error("Invalid MovieX media URL");
  }

  const result = await getMovieXSources(
    parsed.id,
    parsed.season,
    parsed.episode
  );

  const source = pickSource(result.sources, parsed.quality);
  const directUrl = source.downloadUrl || source.directUrl;

  if (!/^https?:\/\//i.test(String(directUrl || ""))) {
    throw new Error("MovieX returned an invalid download URL");
  }

  return {
    host: "moviexdownload.koyeb.app",
    type: "moviex",
    sourceType: "moviex",
    pageUrl: directUrl,
    directUrl,
    external: false,
    quality: source.quality || null,
    size: source.size || null,
    sizeText: formatSize(source.size)
  };
}

module.exports = {
  SEARCH_BASE,
  CONTENT_BASE,
  formatSize,
  makeTitleUrl,
  makeMediaUrl,
  parseMovieXUrl,
  isMovieXTitleUrl,
  isMovieXMediaUrl,
  searchMovieX,
  getMovieXInfo,
  getMovieXSources,
  extractSeasons,
  resolveMovieXUrl
};
