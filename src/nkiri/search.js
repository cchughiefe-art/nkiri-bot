const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const dispatcher = new Agent({
  connect: { timeout: 30000 },
  headersTimeout: 60000,
  bodyTimeout: 60000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  "accept":
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "accept-language": "en-US,en;q=0.9"
};

function clean(text) {
  return String(text || "")
    .replace(/\s+/g, " ")
    .trim();
}

async function searchNkiri(query) {
  const searchUrl =
    `https://thenkiri.com/?s=${encodeURIComponent(query)}`;

  console.log("Searching TheNkiri:", query);

  const response = await fetch(searchUrl, {
    dispatcher,
    headers: HEADERS,
    redirect: "follow"
  });

  if (!response.ok) {
    throw new Error(
      `TheNkiri search returned HTTP ${response.status}`
    );
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  const results = [];
  const seen = new Set();

  /*
   * WordPress themes vary, so inspect several
   * common result containers.
   */
  $("article, .post, .type-post").each((_, element) => {
    const item = $(element);

    const link =
      item.find("h2 a").first().attr("href") ||
      item.find("h3 a").first().attr("href") ||
      item.find(".entry-title a").first().attr("href") ||
      item.find("a").first().attr("href");

    if (!link) return;

    let url;

    try {
      url = new URL(link, response.url).href;
    } catch {
      return;
    }

    if (
      !url.startsWith("https://thenkiri.com/") ||
      seen.has(url)
    ) {
      return;
    }

    const title = clean(
      item.find("h2").first().text() ||
      item.find("h3").first().text() ||
      item.find(".entry-title").first().text()
    );

    if (!title) return;

    const image =
      item.find("img").first().attr("data-src") ||
      item.find("img").first().attr("src") ||
      null;

    seen.add(url);

    results.push({
      title,
      url,
      image
    });
  });

  /*
   * Fallback for themes where posts aren't
   * wrapped in normal <article> elements.
   */
  if (!results.length) {
    $("a").each((_, element) => {
      if (results.length >= 10) return false;

      const anchor = $(element);
      const href = anchor.attr("href");
      const title = clean(anchor.text());

      if (!href || !title) return;

      let url;

      try {
        url = new URL(href, response.url).href;
      } catch {
        return;
      }

      if (
        !url.startsWith("https://thenkiri.com/") ||
        seen.has(url)
      ) {
        return;
      }

      const lower = title.toLowerCase();

      if (
        !lower.includes(query.toLowerCase()) &&
        !lower.includes("download")
      ) {
        return;
      }

      seen.add(url);

      results.push({
        title,
        url,
        image: null
      });
    });
  }

  return results.slice(0, 8);
}

module.exports = {
  searchNkiri
};
