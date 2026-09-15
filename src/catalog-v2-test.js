const {
  parseNkiriPage
} = require("./nkiri/parser");

const {
  parseSeriesPage
} = require("./nkiri/series");

const {
  classifyDownload
} = require("./resolvers");

const {
  movieText,
  seriesText,
  sourceLabel
} = require("./core/catalog-ui");

(async () => {
  console.log(
    "\n=== MOVIE ==="
  );

  const movie =
    await parseNkiriPage(
      "https://thenkiri.com/awareness-2023-download-spanish-movie/"
    );

  console.log(
    movieText(movie)
  );

  if (!movie.description)
    throw new Error(
      "Movie description missing"
    );

  if (!movie.downloadUrl)
    throw new Error(
      "Movie download missing"
    );

  console.log(
    "Source:",
    classifyDownload(
      movie.downloadUrl
    )
  );

  console.log(
    "\n=== MODERN SERIES ==="
  );

  const avatar =
    await parseSeriesPage(
      "https://thenkiri.com/avatar-the-last-airbender-s02-complete-tv-series-2/"
    );

  console.log(
    seriesText(avatar)
  );

  if (avatar.episodes.length !== 7)
    throw new Error(
      "Avatar episode regression"
    );

  console.log(
    "\n=== LEGACY DIRECT ==="
  );

  const reacher =
    await parseSeriesPage(
      "https://thenkiri.com/suspicion-s01-episode-1-and-2-tv-series/"
    );

  console.log(
    "Episodes:",
    reacher.episodes.length
  );

  if (reacher.episodes.length !== 8)
    throw new Error(
      "Reacher legacy regression"
    );

  if (
    !reacher.episodes.every(
      e =>
        classifyDownload(
          e.downloadUrl
        ) === "direct"
    )
  ) {
    throw new Error(
      "Legacy direct classification failed"
    );
  }

  console.log(
    "\n=== PROVIDERS ==="
  );

  const providers = [
    [
      "downloadwella",
      "https://downloadwella.com/vvrv8yqfi66r/file.mkv.html"
    ],
    [
      "direct",
      "https://ds2.nkiserv.com/Series/Test/file.mkv"
    ],
    [
      "wetafiles",
      "https://wetafiles.com/example/file.mkv.html"
    ]
  ];

  for (
    const [expected, url]
    of providers
  ) {
    const actual =
      classifyDownload(url);

    console.log(
      expected,
      "=>",
      actual,
      "=>",
      sourceLabel(actual)
    );

    if (actual !== expected)
      throw new Error(
        `${expected} classification failed`
      );
  }

  console.log(
    "\nCATALOG V2 TEST PASSED"
  );
})().catch(error => {
  console.error(error);
  process.exit(1);
});
