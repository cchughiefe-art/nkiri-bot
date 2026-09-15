const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");
const { classifyDownload } = require("../resolvers");
const {
  extractDescription,
  extractYear,
  extractSize,
  extractQuality
} = require("./metadata");

const dispatcher = new Agent({
  connect: {
    timeout: 30000
  },
  headersTimeout: 60000,
  bodyTimeout: 60000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  "accept":
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "accept-language": "en-US,en;q=0.9",
  "connection": "close"
};

const sleep = ms =>
  new Promise(resolve => setTimeout(resolve, ms));

async function request(url, options = {}) {
  let lastError;

  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      console.log(
        `TheNkiri request ${attempt}/4...`
      );

      const response = await fetch(url, {
        ...options,
        dispatcher
      });

      return response;

    } catch (error) {
      lastError = error;

      console.log(
        `TheNkiri request ${attempt} failed:`,
        error.cause?.code || error.message
      );

      if (attempt < 4) {
        await sleep(attempt * 2000);
      }
    }
  }

  throw lastError;
}

async function parseNkiriPage(url) {
  const response = await request(url, {
    headers: HEADERS,
    redirect: "follow"
  });

  if (!response.ok) {
    throw new Error(
      `TheNkiri returned HTTP ${response.status}`
    );
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  const title =
    $("h1").first().text().trim() ||
    $("title").text().trim();

  const downloadUrls = [];
  const seenDownloads = new Set();

  $("a").each((_, element) => {
    const href = $(element).attr("href");

    const text = $(element)
      .text()
      .replace(/\s+/g, " ")
      .trim()
      .toLowerCase();

    if (!href) return;

    let resolvedUrl;

    try {
      resolvedUrl =
        new URL(
          href,
          response.url
        ).href;
    } catch {
      return;
    }

    const sourceType =
      classifyDownload(
        resolvedUrl
      );

    const isDownload =
      sourceType !== "external" ||
      text.includes(
        "download movie"
      );

    if (
      !isDownload ||
      seenDownloads.has(
        resolvedUrl
      )
    ) {
      return;
    }

    seenDownloads.add(
      resolvedUrl
    );

    downloadUrls.push(
      resolvedUrl
    );
  });

  /*
   * Keep downloadUrl for backwards
   * compatibility with existing code.
   */
  const downloadUrl =
    downloadUrls[0] ||
    null;

  /*
   * Extract the movie synopsis.
   * TheNkiri pages contain useful synopsis text mixed with
   * download instructions, SEO filler and unrelated text.
   */
  const description =
    extractDescription($);

  const pageText = $.text();

  const size =
    extractSize(pageText);

  const year =
    extractYear(title);

  const quality =
    extractQuality(pageText);

  const poster =
    $('meta[property="og:image"]').attr("content") ||
    $("article img").first().attr("src") ||
    null;

  return {
    title,
    size,
    poster,
    description,
    year,
    quality,
    downloadUrl,
    downloadUrls
  };
}

module.exports = {
  parseNkiriPage
};
