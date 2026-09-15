const {
  searchNkiri,
  getLatest
} = require("./nkiri/search");

(async () => {
  const query =
    process.argv
      .slice(2)
      .join(" ") ||
    "Avatar";

  console.log(
    "\nSEARCH TEST\n"
  );

  const search =
    await searchNkiri(
      query,
      {
        page: 1,
        perPage: 6
      }
    );

  console.log(
    `${search.total} results`
  );

  console.log(
    `Page ${search.page}/${search.pages}\n`
  );

  search.results.forEach(
    (item, index) => {
      console.log(
        `${index + 1}.`,
        item.cleanTitle
      );

      console.log(
        "   Type:",
        item.type
      );

      console.log(
        "   Year:",
        item.year || "unknown"
      );

      console.log(
        "   URL:",
        item.url
      );
    }
  );

  console.log(
    "\nLATEST MOVIES\n"
  );

  const movies =
    await getLatest(
      "movie",
      {
        perPage: 5
      }
    );

  movies.results.forEach(
    item =>
      console.log(
        "-",
        item.cleanTitle
      )
  );

  console.log(
    "\nLATEST SERIES\n"
  );

  const series =
    await getLatest(
      "series",
      {
        perPage: 5
      }
    );

  series.results.forEach(
    item =>
      console.log(
        "-",
        item.cleanTitle
      )
  );
})().catch(error => {
  console.error(error);
  process.exit(1);
});
