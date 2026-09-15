const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const {
  rankResults,
  cleanTitle,
  extractYear,
  detectType
} = require("../core/media");

const {
  getCache,
  setCache,
  incrementStat
} = require("../core/store");

const dispatcher = new Agent({
  connect: { timeout: 30000 },
  headersTimeout: 60000,
  bodyTimeout: 60000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "accept-language": "en-US,en;q=0.9"
};

const CACHE_TTL =
  30 * 60 * 1000;

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

async function request(url) {
  let lastError;

  for (
    let attempt = 1;
    attempt <= 3;
    attempt++
  ) {
    try {
      const response =
        await fetch(url, {
          dispatcher,
          headers: HEADERS,
          redirect: "follow"
        });

      if (!response.ok) {
        throw new Error(
          `TheNkiri returned HTTP ${response.status}`
        );
      }

      return response;

    } catch (error) {
      lastError = error;

      if (attempt < 3) {
        await new Promise(
          resolve =>
            setTimeout(
              resolve,
              attempt * 1500
            )
        );
      }
    }
  }

  throw lastError;
}

function parsePosts(
  html,
  baseUrl
) {
  const $ = cheerio.load(html);

  const results = [];
  const seen = new Set();

  $(
    "article, .post, .type-post"
  ).each((_, element) => {
    const item = $(element);

    const anchor =
      item.find(
        ".entry-title a, h1 a, h2 a, h3 a"
      ).first();

    let href =
      anchor.attr("href");

    let title =
      clean(anchor.text());

    if (!href) {
      const fallback =
        item.find("a").first();

      href =
        fallback.attr("href");

      title =
        title ||
        clean(fallback.text());
    }

    if (!href || !title) {
      return;
    }

    let url;

    try {
      url =
        new URL(
          href,
          baseUrl
        ).href;
    } catch {
      return;
    }

    if (
      !url.startsWith(
        "https://thenkiri.com/"
      ) ||
      seen.has(url)
    ) {
      return;
    }

    const image =
      item.find("img")
        .first()
        .attr("data-src") ||
      item.find("img")
        .first()
        .attr("data-lazy-src") ||
      item.find("img")
        .first()
        .attr("src") ||
      null;

    seen.add(url);

    results.push({
      title,
      cleanTitle:
        cleanTitle(title),
      year:
        extractYear(title),
      type:
        detectType(title),
      url,
      image
    });
  });

  return results;
}

async function searchNkiri(
  query,
  options = {}
) {
  const page =
    Math.max(
      1,
      Number(options.page) || 1
    );

  const perPage =
    Math.max(
      1,
      Math.min(
        8,
        Number(options.perPage) || 6
      )
    );

  const normalized =
    String(query)
      .trim()
      .toLowerCase();

  const cacheKey =
    `search:${normalized}`;

  let results =
    getCache(cacheKey);

  if (!results) {
    console.log(
      "Searching TheNkiri:",
      query
    );

    const response =
      await request(
        `https://thenkiri.com/?s=${encodeURIComponent(query)}`
      );

    const html =
      await response.text();

    results =
      parsePosts(
        html,
        response.url
      );

    results =
      rankResults(
        query,
        results
      );

    setCache(
      cacheKey,
      results,
      CACHE_TTL
    );
  }

  incrementStat("searches");

  const total =
    results.length;

  const pages =
    Math.max(
      1,
      Math.ceil(
        total / perPage
      )
    );

  const safePage =
    Math.min(
      page,
      pages
    );

  const start =
    (safePage - 1) *
    perPage;

  return {
    query,
    results:
      results.slice(
        start,
        start + perPage
      ),
    total,
    page: safePage,
    pages,
    hasPrevious:
      safePage > 1,
    hasNext:
      safePage < pages
  };
}

async function getLatest(
  type = "all",
  options = {}
) {
  const page =
    Math.max(
      1,
      Number(options.page) || 1
    );

  const perPage =
    Math.max(
      1,
      Math.min(
        8,
        Number(options.perPage) || 6
      )
    );

  const cacheKey =
    "latest:homepage";

  let results =
    getCache(cacheKey);

  if (!results) {
    console.log(
      "Loading latest TheNkiri posts..."
    );

    const response =
      await request(
        "https://thenkiri.com/"
      );

    const html =
      await response.text();

    results =
      parsePosts(
        html,
        response.url
      );

    setCache(
      cacheKey,
      results,
      10 * 60 * 1000
    );
  }

  let filtered =
    results;

  if (type === "series") {
    filtered =
      results.filter(
        item =>
          item.type === "series"
      );
  }

  if (type === "movie") {
    filtered =
      results.filter(
        item =>
          item.type !== "series"
      );
  }

  const total =
    filtered.length;

  const pages =
    Math.max(
      1,
      Math.ceil(
        total / perPage
      )
    );

  const safePage =
    Math.min(
      page,
      pages
    );

  const start =
    (safePage - 1) *
    perPage;

  return {
    type,
    results:
      filtered.slice(
        start,
        start + perPage
      ),
    total,
    page: safePage,
    pages,
    hasPrevious:
      safePage > 1,
    hasNext:
      safePage < pages
  };
}

module.exports = {
  searchNkiri,
  getLatest
};
