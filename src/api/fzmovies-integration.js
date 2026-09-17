const {
  searchFzMovies,
  latestFzMovies,
  getFzMovie
} = require("../providers/fzmovies");

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

async function resolveOne(source) {
  const resolved =
    await resolveDownload(
      source.url
    );

  return {
    quality: 0,
    size: 0,
    sizeText: "",
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
        resolved.temporary
      ),
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
