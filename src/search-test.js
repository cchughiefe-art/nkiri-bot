const {
  searchNkiri
} = require("./nkiri/search");

async function main() {
  const query =
    process.argv.slice(2).join(" ") ||
    "Awareness";

  console.log(
    `\nSearching for: ${query}\n`
  );

  const results =
    await searchNkiri(query);

  console.log(
    `Found ${results.length} result(s)\n`
  );

  results.forEach((movie, index) => {
    console.log(
      `${index + 1}. ${movie.title}`
    );

    console.log(movie.url);
    console.log();
  });
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
