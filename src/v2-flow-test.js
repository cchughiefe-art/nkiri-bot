const { searchNkiri, getLatest } = require("./nkiri/search");
const { parseNkiriPage } = require("./nkiri/parser");
const { parseSeriesPage } = require("./nkiri/series");
const { resolveDownloadWella } = require("./resolvers/downloadwella");

(async () => {
  console.log("\n=== SEARCH ===");

  const search = await searchNkiri("Avatar", {
    page: 1,
    perPage: 6
  });

  console.log("Results:", search.total);

  for (const item of search.results) {
    console.log("-", item.cleanTitle, `[${item.type}]`);
  }

  console.log("\n=== LATEST MOVIES ===");

  const movies = await getLatest("movie", {
    page: 1,
    perPage: 5
  });

  movies.results.forEach(x =>
    console.log("-", x.cleanTitle)
  );

  console.log("\n=== LATEST SERIES ===");

  const seriesLatest = await getLatest("series", {
    page: 1,
    perPage: 5
  });

  seriesLatest.results.forEach(x =>
    console.log("-", x.cleanTitle)
  );

  console.log("\n=== MOVIE PARSER ===");

  const movieUrl =
    "https://thenkiri.com/awareness-2023-download-spanish-movie/";

  const movie = await parseNkiriPage(movieUrl);

  console.log("Title:", movie.title);
  console.log("Size:", movie.size);
  console.log("Poster:", !!movie.poster);
  console.log("Download page:", movie.downloadUrl);

  if (!movie.downloadUrl) {
    throw new Error("Movie download page missing");
  }

  console.log("\n=== DOWNLOAD RESOLVER ===");

  const resolved =
    await resolveDownloadWella(movie.downloadUrl);

  console.log("Host:", resolved.host);
  console.log("Direct URL:", !!resolved.directUrl);

  console.log("\n=== SERIES PARSER ===");

  const seriesUrl =
    "https://thenkiri.com/avatar-the-last-airbender-s02-complete-tv-series-2/";

  const series =
    await parseSeriesPage(seriesUrl);

  console.log("Title:", series.title);
  console.log("Series:", series.isSeries);
  console.log("Episodes:", series.episodes.length);

  series.episodes.forEach(ep => {
    console.log(
      "-",
      ep.label,
      ep.downloadUrl
    );
  });

  if (!series.episodes.length) {
    throw new Error("No episodes detected");
  }

  console.log("\n=== FIRST EPISODE RESOLVER ===");

  const episode =
    await resolveDownloadWella(
      series.episodes[0].downloadUrl
    );

  console.log("Direct URL:", !!episode.directUrl);

  console.log("\nV2 FLOW TEST PASSED");
})().catch(error => {
  console.error("\nV2 FLOW TEST FAILED");
  console.error(error);
  process.exit(1);
});
