const {
  searchFzMovies,
  latestFzMovies,
  getFzMovie
} = require("../providers/fzmovies");

const {
  resolveDownload
} = require("../resolvers");

function toBotItem(item) {
  return {
    provider: "fzmovies",
    fzId: String(item.fzId || item.id),
    title: item.title,
    cleanTitle:
      item.cleanTitle ||
      item.title,
    year:
      item.year || null,
    type:
      item.type || "movie",
    image:
      item.image || null,
    url:
      `fz://${item.fzId || item.id}`
  };
}

async function searchFzForBot(query, limit = 8) {
  const items =
    await searchFzMovies(
      query,
      limit
    );

  return items.map(
    toBotItem
  );
}

async function latestFzForBot(limit = 8) {
  const items =
    await latestFzMovies({
      limit
    });

  return items.map(
    toBotItem
  );
}

function isFzUrl(url) {
  return /^fz:\/\/\d+$/.test(
    String(url || "")
  );
}

function fzIdFromUrl(url) {
  if (!isFzUrl(url))
    return null;

  return String(url)
    .replace(/^fz:\/\//, "");
}

async function getFzBotTitle(id) {
  return await getFzMovie(
    String(id)
  );
}

async function resolveFzSource(source) {
  return await resolveDownload(
    source.url
  );
}

module.exports = {
  searchFzForBot,
  latestFzForBot,
  isFzUrl,
  fzIdFromUrl,
  getFzBotTitle,
  resolveFzSource
};
