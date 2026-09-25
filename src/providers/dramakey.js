const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const BASE = "https://dramakey.com/";

const dispatcher = new Agent({
  connect: { timeout: 15000 },
  headersTimeout: 30000,
  bodyTimeout: 30000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "accept-language": "en-US,en;q=0.9"
};

const sleep = ms =>
  new Promise(resolve => setTimeout(resolve, ms));

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

function absolute(value, base = BASE) {
  if (!value) return null;

  try {
    const url = new URL(value, base);

    if (url.protocol !== "https:" && url.protocol !== "http:") {
      return null;
    }

    return url.href;
  } catch {
    return null;
  }
}

function detectType(title) {
  return /\bS(?:eason)?\s*0?\d+\b/i.test(title) ||
    /\bepisode\s*\d+/i.test(title) ||
    /\b(?:drama|series|tv show)\b/i.test(title)
      ? "series"
      : "movie";
}

function extractYear(title) {
  const match = clean(title).match(/\b(?:19|20)\d{2}\b/);
  return match ? Number(match[0]) : null;
}

async function request(url) {
  let lastError;

  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const response = await fetch(url, {
        dispatcher,
        headers: HEADERS,
        redirect: "follow",
        signal: AbortSignal.timeout(35000)
      });

      if (
        response.ok ||
        ![408, 429, 500, 502, 503, 504].includes(response.status)
      ) {
        return response;
      }

      lastError = new Error(`DramaKey HTTP ${response.status}`);

      if (attempt < 3) {
        const retryAfter =
          Number(response.headers.get("retry-after"));

        await sleep(
          Number.isFinite(retryAfter)
            ? Math.min(retryAfter * 1000, 10000)
            : attempt * 1000 + Math.floor(Math.random() * 500)
        );
      }
    } catch (error) {
      lastError = error;

      if (attempt < 3) {
        await sleep(
          attempt * 1000 + Math.floor(Math.random() * 500)
        );
      }
    }
  }

  throw lastError;
}

function parseSearch(html, baseUrl = BASE) {
  const $ = cheerio.load(html || "");
  const results = [];
  const seen = new Set();

  $(
    "article, .post, .type-post, .elementor-post, .jeg_post, .jnews_module"
  ).each((_, element) => {
    const item = $(element);
    const anchor = item
      .find(
        "h1 a, h2 a, h3 a, .entry-title a, .post-title a, a[href]"
      )
      .first();

    const url = absolute(anchor.attr("href"), baseUrl);

    if (
      !url ||
      !new URL(url).hostname.endsWith("dramakey.com") ||
      seen.has(url)
    ) {
      return;
    }

    const title =
      clean(anchor.attr("title")) ||
      clean(anchor.text()) ||
      clean(
        item
          .find("h1,h2,h3,.entry-title,.post-title")
          .first()
          .text()
      );

    if (!title || title.length < 2) return;

    const image = absolute(
      item.find("img").first().attr("data-src") ||
      item.find("img").first().attr("data-lazy-src") ||
      item.find("img").first().attr("src"),
      baseUrl
    );

    seen.add(url);

    results.push({
      provider: "dramakey",
      id: url,
      title,
      cleanTitle: title,
      type: detectType(title),
      year: extractYear(title),
      image: image || null,
      url
    });
  });

  return results;
}

async function searchDramaKey(query, limit = 12) {
  const q = clean(query);

  if (!q) return [];

  const url = new URL("/", BASE);
  url.searchParams.set("s", q);

  const response = await request(url.href);

  if (!response.ok) {
    throw new Error(
      `DramaKey search returned HTTP ${response.status}`
    );
  }

  const results = parseSearch(
    await response.text(),
    response.url
  );

  const words = q
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .split(/\s+/)
    .filter(Boolean);

  return results
    .filter(item => {
      const title = item.title.toLowerCase();
      return words.every(word => title.includes(word));
    })
    .slice(0, Math.max(1, Number(limit) || 12));
}

module.exports = {
  BASE,
  searchDramaKey,
  parseSearch
};
