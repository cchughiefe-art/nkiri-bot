const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

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

    const isDownload =
      resolvedUrl.includes(
        "downloadwella.com"
      ) ||
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
  function extractDescription() {
    const candidates = [];

    $("article p, .entry-content p").each((index, element) => {
      const text = $(element)
        .text()
        .replace(/\\s+/g, " ")
        .trim();

      if (text.length < 60) return;

      const lower = text.toLowerCase();

      const blocked = [
        "download movie",
        "download nollywood",
        "download hollywood",
        "fzmovies",
        "netnaija",
        "o2tvseries",
        "torrent",
        "for readability",
        "i am buying a book",
        "i have bought",
        "transition words",
        "report abuse"
      ];

      if (
        blocked.some(word =>
          lower.includes(word)
        )
      ) {
        return;
      }

      let score = 0;

      // Synopsis paragraphs are normally substantial,
      // natural prose.
      if (text.length >= 100) score += 4;
      if (text.length >= 150) score += 2;
      if (text.length <= 1000) score += 1;

      // Prefer early article paragraphs.
      score += Math.max(0, 5 - index);

      // Penalize technical/download instructions.
      if (
        /subtitles?|vlc|audio is|download size|click|link|episode/i
          .test(text)
      ) {
        score -= 6;
      }

      candidates.push({
        text,
        score
      });
    });

    candidates.sort(
      (a, b) =>
        b.score - a.score
    );

    return candidates[0]?.text || null;
  }

  const description =
    extractDescription();

  const pageText = $.text();

  const sizeMatch = pageText.match(
    /Download Size\s*(?:This Video is)?\s*([\d.]+\s*(?:MB|GB))/i
  );

  const poster =
    $('meta[property="og:image"]').attr("content") ||
    $("article img").first().attr("src") ||
    null;

  return {
    title,
    size: sizeMatch ? sizeMatch[1] : null,
    poster,
    description,
    downloadUrl,
    downloadUrls
  };
}

module.exports = {
  parseNkiriPage
};
