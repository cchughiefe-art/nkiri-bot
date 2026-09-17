require("dotenv").config();

const http = require("http");
const { URL } = require("url");
const { searchNkiri, getLatest } = require("../nkiri/search");
const { parseNkiriPage } = require("../nkiri/parser");
const { parseSeriesPage } = require("../nkiri/series");
const {
  getMovieXInfo,
  getMovieXSources,
  extractSeasons,
  formatSize,
  parseMovieXUrl
} = require("../providers/moviex");
const { resolveDownload } = require("../resolvers");

const HOST = process.env.API_HOST || "0.0.0.0";
const PORT = Number(process.env.API_PORT || process.env.PORT || 3001);
const API_NAME = process.env.API_NAME || "TheNkiri App API";
const MAX_REQUESTS = Math.max(20, Number(process.env.API_RATE_LIMIT || 120));
const WINDOW_MS = 60_000;
const rateMap = new Map();
const cache = new Map();
const CACHE_TTL = 10 * 60 * 1000;

function send(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET,OPTIONS",
    "access-control-allow-headers": "content-type",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    "x-frame-options": "DENY"
  });
  res.end(body);
}

function ok(res, data, meta) {
  send(res, 200, { status: "success", data, ...(meta ? { meta } : {}) });
}

function fail(res, status, message, code = "ERROR") {
  send(res, status, { status: "error", error: { code, message } });
}

function clientKey(req) {
  return String(
    req.headers["cf-connecting-ip"] ||
    req.headers["x-forwarded-for"] ||
    req.socket?.remoteAddress ||
    "unknown"
  ).split(",")[0].trim();
}

function allowed(req) {
  const key = clientKey(req);
  const now = Date.now();
  const state = rateMap.get(key);
  if (!state || now - state.startedAt >= WINDOW_MS) {
    rateMap.set(key, { startedAt: now, count: 1 });
    return true;
  }
  state.count += 1;
  return state.count <= MAX_REQUESTS;
}

setInterval(() => {
  const cutoff = Date.now() - WINDOW_MS * 2;
  for (const [key, value] of rateMap) {
    if (value.startedAt < cutoff) rateMap.delete(key);
  }
}, WINDOW_MS).unref();

function getCached(key) {
  const item = cache.get(key);
  if (!item) return null;
  if (item.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }
  return item.value;
}

function setCached(key, value) {
  cache.set(key, { value, expiresAt: Date.now() + CACHE_TTL });
  return value;
}

function encodeNkiri(url, type) {
  const payload = Buffer.from(JSON.stringify({ url, type }), "utf8").toString("base64url");
  return `nk_${payload}`;
}

function decodeId(id) {
  const value = String(id || "");
  if (/^\d+$/.test(value)) {
    return { provider: "moviex", id: value };
  }
  if (!value.startsWith("nk_")) throw new Error("Invalid media ID");
  let data;
  try {
    data = JSON.parse(Buffer.from(value.slice(3), "base64url").toString("utf8"));
  } catch {
    throw new Error("Invalid TheNkiri media ID");
  }
  if (!data?.url || !String(data.url).startsWith("https://thenkiri.com/")) {
    throw new Error("Invalid TheNkiri URL");
  }
  return {
    provider: "thenkiri",
    id: value,
    url: data.url,
    type: data.type === "series" ? "series" : "movie"
  };
}

function toSearchItem(item) {
  const isMovieX = item.provider === "moviex" || String(item.url || "").startsWith("moviex://");
  if (isMovieX) {
    const parsed = parseMovieXUrl(item.url);
    const id = String(item.moviexId || parsed?.id || "");
    if (!id) return null;
    return {
      id,
      title: item.title || item.cleanTitle || "Untitled",
      displayTitle: item.cleanTitle || item.title || "Untitled",
      year: Number(item.year || 0) || null,
      type: item.type === "series" ? "series" : "movie",
      poster: item.image || null,
      rating: Number(item.rating || 0) || null,
      genre: item.genre || "",
      provider: "moviex"
    };
  }

  const type = item.type === "series" ? "series" : "movie";
  return {
    id: encodeNkiri(item.url, type),
    title: item.cleanTitle || item.title || "Untitled",
    displayTitle: item.cleanTitle || item.title || "Untitled",
    year: Number(item.year || 0) || null,
    type,
    poster: item.image || null,
    rating: null,
    genre: "",
    provider: "thenkiri"
  };
}

function movieXTitle(info) {
  const subject = info?.subject || {};
  return {
    id: String(subject.subjectId || ""),
    provider: "moviex",
    type: Number(subject.subjectType) === 2 ? "series" : "movie",
    title: subject.title || "Unknown title",
    description: subject.description || "",
    year: Number(String(subject.releaseDate || "").slice(0, 4)) || null,
    genre: subject.genre || "",
    country: subject.countryName || null,
    rating: Number(subject.imdbRatingValue || 0) || null,
    poster: subject?.cover?.url || subject.thumbnail || null,
    subtitles: subject.subtitles || "",
    trailer: subject?.trailer?.videoAddress?.url || null,
    seasons: extractSeasons(info)
  };
}

async function titleFor(id) {
  const media = decodeId(id);
  const cacheKey = `title:${media.id}`;
  const cached = getCached(cacheKey);
  if (cached) return cached;

  if (media.provider === "moviex") {
    return setCached(cacheKey, movieXTitle(await getMovieXInfo(media.id)));
  }

  if (media.type === "series") {
    const series = await parseSeriesPage(media.url);
    const episodes = (series.episodes || []).map((ep, index) => ({
      season: Number(ep.season || 1),
      episode: Number(ep.episode || index + 1),
      label: ep.label || `Episode ${index + 1}`,
      downloadUrl: ep.downloadUrl
    }));
    const groups = new Map();
    for (const ep of episodes) {
      const season = ep.season || 1;
      if (!groups.has(season)) groups.set(season, []);
      groups.get(season).push(ep);
    }
    const seasons = [...groups.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([season, list]) => ({ season, maxEp: list.length }));

    return setCached(cacheKey, {
      id: media.id,
      provider: "thenkiri",
      type: "series",
      title: series.title || "Series",
      description: series.description || "",
      year: Number(series.year || 0) || null,
      genre: "",
      country: null,
      rating: null,
      poster: series.poster || null,
      subtitles: "",
      trailer: null,
      seasons,
      episodes,
      quality: Number(series.quality || 0) || 0
    });
  }

  const movie = await parseNkiriPage(media.url);
  return setCached(cacheKey, {
    id: media.id,
    provider: "thenkiri",
    type: "movie",
    title: movie.title || "Movie",
    description: movie.description || "",
    year: Number(movie.year || 0) || null,
    genre: "",
    country: null,
    rating: null,
    poster: movie.poster || null,
    subtitles: "",
    trailer: null,
    seasons: [],
    quality: Number(movie.quality || 0) || 0,
    size: Number(movie.size || 0) || 0,
    downloadUrls: movie.downloadUrls || []
  });
}

function summarizeSource(source) {
  return {
    quality: Number(source.quality || 0),
    size: Number(source.size || 0),
    sizeText: source.sizeText || (source.size ? formatSize(source.size) : ""),
    format: source.format || "mp4",
    url: source.directUrl || source.downloadUrl || source.url || null
  };
}

async function resolveNkiri(downloadUrl, fallback = {}) {
  const resolved = await resolveDownload(downloadUrl);
  return {
    quality: Number(resolved.quality || fallback.quality || 0),
    size: Number(resolved.size || fallback.size || 0),
    sizeText: resolved.sizeText || (resolved.size ? formatSize(resolved.size) : ""),
    format: resolved.format || "mp4",
    url: resolved.directUrl || resolved.pageUrl || null
  };
}

async function sourcesFor(id, season = 0, episode = 0, quality = 0) {
  const media = decodeId(id);

  if (media.provider === "moviex") {
    const result = await getMovieXSources(media.id, season, episode);
    const sources = result.sources.map(summarizeSource).filter(x => x.url);
    const selected = quality
      ? sources.find(x => x.quality === quality) || sources[0] || null
      : [...sources].sort((a, b) => b.quality - a.quality)[0] || null;
    return { provider: "moviex", sources, selected };
  }

  const title = await titleFor(media.id);
  if (title.type === "series") {
    const match = title.episodes.find(ep => ep.season === season && ep.episode === episode);
    if (!match) throw new Error("Episode not found");
    const selected = await resolveNkiri(match.downloadUrl, { quality: title.quality });
    return { provider: "thenkiri", sources: [selected], selected };
  }

  const resolved = [];
  for (const url of (title.downloadUrls || []).slice(0, 4)) {
    try {
      const item = await resolveNkiri(url, { quality: title.quality, size: title.size });
      if (item.url && !resolved.some(x => x.url === item.url)) resolved.push(item);
    } catch (error) {
      console.error("NKIRI RESOLVE ERROR:", error.message);
    }
  }
  if (!resolved.length) throw new Error("No working download source found");
  const selected = quality
    ? resolved.find(x => x.quality === quality) || resolved[0]
    : resolved[0];
  return { provider: "thenkiri", sources: resolved, selected };
}

async function handle(req, res) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,OPTIONS",
      "access-control-allow-headers": "content-type",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    "x-frame-options": "DENY"
    });
    return res.end();
  }
  if (req.method !== "GET") return fail(res, 405, "Only GET is supported.", "METHOD_NOT_ALLOWED");
  if (!allowed(req)) return fail(res, 429, "Too many requests. Try again shortly.", "RATE_LIMITED");

  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const path = url.pathname.replace(/\/+$/, "") || "/";

  if (path === "/" || path === "/health") {
    return ok(res, {
      name: API_NAME,
      version: "1.0.0",
      online: true,
      providers: ["thenkiri", "moviex"],
      time: new Date().toISOString()
    });
  }

  if (path === "/api/search") {
    const q = String(url.searchParams.get("q") || "").trim();
    if (q.length < 2) return fail(res, 400, "Query must contain at least 2 characters.", "BAD_QUERY");
    const page = Math.max(1, Number(url.searchParams.get("page") || 1));
    const perPage = Math.max(1, Math.min(20, Number(url.searchParams.get("perPage") || 12)));
    const result = await searchNkiri(q, { page, perPage });
    return ok(res, result.results.map(toSearchItem).filter(Boolean), {
      query: q,
      total: result.total,
      page: result.page,
      pages: result.pages,
      hasPrevious: result.hasPrevious,
      hasNext: result.hasNext
    });
  }

  if (path === "/api/latest") {
    const type = String(url.searchParams.get("type") || "all").toLowerCase();
    if (!["all", "movie", "series", "drama"].includes(type)) {
      return fail(res, 400, "Invalid latest type.", "BAD_TYPE");
    }
    const page = Math.max(1, Number(url.searchParams.get("page") || 1));
    const perPage = Math.max(1, Math.min(20, Number(url.searchParams.get("perPage") || 12)));
    const result = await getLatest(type, { page, perPage });
    return ok(res, result.results.map(toSearchItem).filter(Boolean), {
      type,
      total: result.total,
      page: result.page,
      pages: result.pages,
      hasPrevious: result.hasPrevious,
      hasNext: result.hasNext
    });
  }

  let match = path.match(/^\/api\/title\/([^/]+)$/);
  if (match) {
    const title = await titleFor(decodeURIComponent(match[1]));
    const clean = { ...title };
    delete clean.episodes;
    delete clean.downloadUrls;
    delete clean.quality;
    delete clean.size;
    return ok(res, clean);
  }

  match = path.match(/^\/api\/title\/([^/]+)\/episodes$/);
  if (match) {
    const id = decodeURIComponent(match[1]);
    const season = Number(url.searchParams.get("season") || 0);
    if (!season) return fail(res, 400, "season is required.", "SEASON_REQUIRED");
    const title = await titleFor(id);
    if (title.type !== "series") return fail(res, 400, "This title is not a series.", "NOT_SERIES");

    let episodes;
    if (title.provider === "moviex") {
      const found = title.seasons.find(x => Number(x.season) === season);
      if (!found) return fail(res, 404, "Season not found.", "SEASON_NOT_FOUND");
      episodes = Array.from({ length: Number(found.maxEp || 0) }, (_, index) => ({
        season,
        episode: index + 1,
        label: `S${String(season).padStart(2, "0")}E${String(index + 1).padStart(2, "0")}`
      }));
    } else {
      episodes = title.episodes
        .filter(ep => Number(ep.season) === season)
        .map(ep => ({ season: ep.season, episode: ep.episode, label: ep.label }));
    }
    return ok(res, { id, title: title.title, season, episodes });
  }

  match = path.match(/^\/api\/source\/([^/]+)$/);
  if (match) {
    const id = decodeURIComponent(match[1]);
    const season = Number(url.searchParams.get("season") || 0);
    const episode = Number(url.searchParams.get("episode") || 0);
    const quality = Number(url.searchParams.get("quality") || 0);
    if ((season && !episode) || (!season && episode)) {
      return fail(res, 400, "season and episode must be supplied together.", "BAD_EPISODE_REQUEST");
    }
    const result = await sourcesFor(id, season, episode, quality);
    if (!result.selected?.url) return fail(res, 404, "No downloadable source found.", "NO_SOURCE");
    return ok(res, result);
  }


  if (
    path === "/api/config"
  ) {
    return ok(res, {
      latestVersionCode:
        Math.max(
          1,
          Number(
            process.env.APP_LATEST_VERSION_CODE ||
            1
          )
        ),
      latestVersionName:
        process.env.APP_LATEST_VERSION_NAME ||
        "1.0.0",
      updateUrl:
        process.env.APP_UPDATE_URL ||
        null,
      forceUpdate:
        String(
          process.env.APP_FORCE_UPDATE ||
          "false"
        ).toLowerCase() ===
        "true",
      notice:
        process.env.APP_NOTICE ||
        null,
      channelUrl:
        process.env.APP_CHANNEL_URL ||
        "https://t.me/voidupdatezone",
      botUrl:
        process.env.APP_BOT_URL ||
        "https://t.me/nkiridownbot"
    });
  }

  return fail(res, 404, "Route not found.", "NOT_FOUND");
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(error => {
    console.error("API ERROR:", error);
    if (!res.headersSent) fail(res, 502, error?.message || "Provider request failed.", "PROVIDER_ERROR");
    else res.end();
  });
});

server.listen(PORT, HOST, () => {
  console.log(`${API_NAME} listening on http://${HOST}:${PORT}`);
});

module.exports = { server, encodeNkiri, decodeId };
