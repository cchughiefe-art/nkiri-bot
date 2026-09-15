const { parseNkiriPage } =
  require("./nkiri/parser");

const {
  resolveDownloadWella
} = require("./resolvers/downloadwella");

const TEST_URL =
  "https://thenkiri.com/awareness-2023-download-spanish-movie/";

async function main() {
  console.log("\nNKIRI RESOLVER\n");

  const movie =
    await parseNkiriPage(TEST_URL);

  console.log("Movie:");
  console.log(movie.title);

  console.log("\nResolving file...");

  const resolved =
    await resolveDownloadWella(
      movie.downloadUrl
    );

  console.log("\nSUCCESS");
  console.log("Host:", resolved.host);
  console.log("File:", resolved.directUrl);

  console.log(
    "\nResolver is ready for Telegram."
  );
}

main().catch(error => {
  console.error("\nFAILED:");
  console.error(error);
  process.exit(1);
});
