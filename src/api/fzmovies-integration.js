const {
  searchFzMovies,
  latestFzMovies,
  getFzMovie
} = require("../providers/fzmovies");

const { fetch } = require("undici");

const {
  resolveDownload
} = require("../resolvers");

function encodeFz(id) {
  return `fz_${id}`;
}

function isFzId(id) {
  return /^fz_\d+$/.test(
    String(id || "")
  );
}

function normalizeItem(item) {
  return {
    id: encodeFz(item.fzId || item.id),
    title: item.title,
    displayTitle: item.cleanTitle || item.title,
    year: item.year || null,
    type: item.type,
    poster: item.image || null,
    rating: null,
    genre: "",
    provider: "fzmovies"
  };
}

async function searchFz(query, limit = 20) {
  return (
    await searchFzMovies(
      query,
      limit
    )
  ).map(normalizeItem);
}

async function latestFz(options = {}) {
  return (
    await latestFzMovies(options)
  ).map(normalizeItem);
}

async function titleFz(encodedId) {
  const numericId =
    String(encodedId)
      .replace(/^fz_/, "");

  const item =
    await getFzMovie(
      numericId
    );

  return {
    id: encodeFz(numericId),
    provider: "fzmovies",
    type: item.type,
    title: item.title,
    description: item.description || "",
    year: item.year || null,
    genre: "",
    country: null,
    rating: null,
    poster: item.image || null,
    subtitles: "",
    trailer: null,

    seasons:
      item.type === "series"
        ? [
            ...new Set(
              item.episodes.map(
                ep =>
                  Number(ep.season || 1)
              )
            )
          ].map(season => ({
            season,
            maxEp:
              item.episodes.filter(
                ep =>
                  Number(ep.season || 1) ===
                  season
              ).length
          }))
        : [],

    episodes:
      item.episodes || [],

    downloadLinks:
      item.downloadLinks || []
  };
}


function formatBytes(bytes) {
  const n = Number(bytes || 0);

  if (!n)
    return "";

  const units = [
    "B",
    "KB",
    "MB",
    "GB",
    "TB"
  ];

  let value = n;
  let unit = 0;

  while (
    value >= 1024 &&
    unit < units.length - 1
  ) {
    value /= 1024;
    unit++;
  }

  return `${value.toFixed(
    value >= 100 || unit === 0
      ? 0
      : value >= 10
        ? 1
        : 2
  )} ${units[unit]}`;
}

async function probeMedia(url) {
  try {
    const response =
      await fetch(url, {
        redirect: "follow",
        headers: {
          "user-agent":
            "Mozilla/5.0 (Android 15; Mobile)",
          range: "bytes=0-0"
        }
      });

    const range =
      response.headers.get(
        "content-range"
      );

    const length =
      response.headers.get(
        "content-length"
      );

    const type =
      response.headers.get(
        "content-type"
      );

    let totalBytes = 0;

    if (range) {
      const match =
        range.match(
          /\/(\d+)$/
        );

      if (match) {
        totalBytes =
          Number(match[1]) || 0;
      }
    }

    if (
      !totalBytes &&
      length &&
      response.status !== 206
    ) {
      totalBytes =
        Number(length) || 0;
    }

    return {
      totalBytes,
      sizeText:
        formatBytes(totalBytes),
      mimeType:
        type || null,
      resumable:
        response.status === 206 ||
        Boolean(range)
    };
  } catch (error) {
    console.error(
      "MEDIA PROBE ERROR:",
      error.message
    );

    return {
      totalBytes: 0,
      sizeText: "",
      mimeType: null,
      resumable: false
    };
  }
}

async function resolveOne(source) {
  const resolved =
    await resolveDownload(
      source.url
    );

  const media =
    await probeMedia(
      resolved.directUrl ||
      resolved.pageUrl ||
      source.url
    );

  return {
    quality: 0,
    size:
      media.totalBytes || 0,
    sizeText:
      media.sizeText || "",
    format:
      /\.mkv(?:$|\?)/i.test(
        resolved.directUrl || source.url
      )
        ? "mkv"
        : "mp4",
    url:
      resolved.directUrl ||
      resolved.pageUrl ||
      source.url,
    type:
      resolved.type ||
      "direct",
    external:
      Boolean(
        resolved.external
      ),
    pageUrl:
      resolved.pageUrl ||
      source.url,
    label:
      source.label || null,
    temporary:
      Boolean(
        resolved.temporary ||
        resolved.type === "downloadwella" ||
        resolved.type === "wideshares" ||
        resolved.type === "sabishares"
      ),
    resumable:
      Boolean(
        media.resumable
      ),
    mimeType:
      media.mimeType,
    host:
      resolved.host || null
  };
}

async function sourcesFz(
  encodedId,
  season = 0,
  episode = 0
) {
  const title =
    await titleFz(
      encodedId
    );

  let candidates = [];

  if (
    title.type === "series"
  ) {
    const match =
      title.episodes.find(
        ep =>
          Number(ep.season) ===
            Number(season) &&
          Number(ep.episode) ===
            Number(episode)
      );

    if (!match) {
      throw new Error(
        "FZMovies episode not found"
      );
    }

    candidates =
      match.sources || [];
  } else {
    candidates =
      title.downloadLinks || [];
  }

  if (!candidates.length) {
    throw new Error(
      "FZMovies has no download source"
    );
  }

  const resolved = [];

  for (
    const source
    of candidates.slice(0, 6)
  ) {
    try {
      const item =
        await resolveOne(
          source
        );

      if (
        item.url &&
        !resolved.some(
          x => x.url === item.url
        )
      ) {
        resolved.push(
          item
        );
      }
    } catch (error) {
      console.error(
        "FZMOVIES SOURCE ERROR:",
        source.url,
        error.message
      );
    }
  }

  if (!resolved.length) {
    throw new Error(
      "No working FZMovies source"
    );
  }

  return {
    provider: "fzmovies",
    sources: resolved,
    selected: resolved[0]
  };
}

module.exports = {
  encodeFz,
  isFzId,
  searchFz,
  latestFz,
  titleFz,
  sourcesFz
};
