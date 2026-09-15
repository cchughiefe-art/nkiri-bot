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
    `search:${normalized}:all-pages-v3`;

  let results =
    getCache(cacheKey);

  if (!results) {
    console.log(
      "Searching TheNkiri:",
      query
    );

    const encoded =
      encodeURIComponent(query);

    const collected = [];
    const seen = new Set();

    /*
     * Safety limit.
     *
     * TheNkiri normally has only a few
     * result pages for a specific title.
     * This prevents a malformed pagination
     * link from creating an endless crawl.
     */
    const MAX_SOURCE_PAGES = 20;

    for (
      let sourcePage = 1;
      sourcePage <= MAX_SOURCE_PAGES;
      sourcePage++
    ) {
      const url =
        sourcePage === 1
          ? `https://thenkiri.com/?s=${encoded}`
          : `https://thenkiri.com/page/${sourcePage}/?s=${encoded}`;

      console.log(
        `TheNkiri search page ${sourcePage}...`
      );

      let response;

      try {
        response =
          await request(url);
      } catch (error) {
        /*
         * A 404/nonexistent page after we
         * already collected results simply
         * means pagination has ended.
         */
        if (sourcePage > 1) {
          console.log(
            `Search pagination ended at page ${sourcePage - 1}`
          );
          break;
        }

        throw error;
      }

      const html =
        await response.text();

      const found =
        parsePosts(
          html,
          response.url
        );

      /*
       * No posts on a later page means
       * there is nothing else to crawl.
       */
      if (!found.length) {
        break;
      }

      let added = 0;

      for (const item of found) {
        if (seen.has(item.url)) {
          continue;
        }

        seen.add(item.url);
        collected.push(item);
        added++;
      }

      /*
       * WordPress can sometimes redirect an
       * invalid high page number back to an
       * earlier page. If everything on this
       * page was already seen, stop.
       */
      if (added === 0) {
        break;
      }

      /*
       * Detect whether a real next search
       * page exists instead of deliberately
       * generating a 404 on every search.
       */
      const $ =
        cheerio.load(html);

      const nextHref =
        $(
          'a.next.page-numbers, ' +
          '.nav-links a.next, ' +
          'a[rel="next"]'
        )
          .first()
          .attr("href");

      if (!nextHref) {
        /*
         * Some themes use only numbered
         * pagination. Look for page N+1.
         */
        let hasNextNumber = false;

        $(
          '.page-numbers a, ' +
          '.nav-links a'
        ).each((_, element) => {
          const href =
            $(element).attr("href");

          if (!href) return;

          if (
            href.includes(
              `/page/${sourcePage + 1}/`
            )
          ) {
            hasNextNumber = true;
          }
        });

        if (!hasNextNumber) {
          break;
        }
      }
    }

    /*
     * Rank everything returned by TheNkiri first.
     */
    results =
      rankResults(
        query,
        collected
      );

    /*
     * Remove unrelated WordPress search results when
     * we have genuine title matches.
     *
     * Example:
     * "Vampire diaries" should keep all Vampire
     * Diaries seasons but remove Mayfair Witches,
     * The Originals, Stags, etc.
     */
    const queryWords =
      normalized
        .replace(/[^a-z0-9]+/g, " ")
        .split(/\s+/)
        .filter(word => word.length >= 2);

    if (queryWords.length) {
      const strongMatches =
        results.filter(item => {
          const title =
            String(
              item.cleanTitle ||
              item.title ||
              ""
            )
              .toLowerCase()
              .replace(/[^a-z0-9]+/g, " ");

          return queryWords.every(
            word =>
              title
                .split(/\s+/)
                .includes(word)
          );
        });

      /*
       * Only apply strict filtering when it actually
       * found something. This preserves fuzzy search
       * behavior for misspellings/partial searches.
       */
      if (strongMatches.length) {
        results = strongMatches;
      }
    }

    setCache(
      cacheKey,
      results,
      CACHE_TTL
    );

    console.log(
      `TheNkiri search collected ${results.length} unique result(s)`
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

  /*
   * TheNkiri exposes separate browsing pages.
   * Using them is more reliable than trying
   * to infer sections from the homepage.
   */
  let sourceUrl =
    "https://thenkiri.com/";

  if (type === "movie") {
    sourceUrl =
      "https://thenkiri.com/movies-menu/";
  }

  if (type === "series") {
    sourceUrl =
      "https://thenkiri.com/tv-series-menu/";
  }

  if (type === "drama") {
    sourceUrl =
      "https://thenkiri.com/korean-drama-menu/";
  }

  const cacheKey =
    `latest:${type}:${sourceUrl}`;

  let results =
    getCache(cacheKey);

  if (!results) {
    console.log(
      `Loading latest ${type} posts...`
    );

    const response =
      await request(sourceUrl);

    const html =
      await response.text();

    results =
      parsePosts(
        html,
        response.url
      );

    /*
     * Some menu layouts don't wrap every
     * card in a normal WordPress article.
     * Extract matching post links directly
     * as a fallback.
     */
    if (!results.length) {
      const $ =
        cheerio.load(html);

      const seen =
        new Set();

      results = [];

      $("a").each((_, element) => {
        const anchor =
          $(element);

        const href =
          anchor.attr("href");

        const title =
          clean(
            anchor.text()
          );

        if (
          !href ||
          !title
        ) {
          return;
        }

        let url;

        try {
          url =
            new URL(
              href,
              response.url
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

        const detected =
          detectType(title);

        if (
          type === "movie" &&
          detected !== "movie"
        ) {
          return;
        }

        if (
          type === "series" &&
          detected !== "series"
        ) {
          return;
        }

        if (
          type === "drama" &&
          !title
            .toLowerCase()
            .includes("drama")
        ) {
          return;
        }

        seen.add(url);

        const image =
          anchor.find("img")
            .first()
            .attr("data-src") ||
          anchor.find("img")
            .first()
            .attr("src") ||
          null;

        results.push({
          title,
          cleanTitle:
            cleanTitle(title),
          year:
            extractYear(title),
          type:
            type === "drama"
              ? "series"
              : detected,
          url,
          image
        });
      });
    }

    /*
     * Filter menu/navigation links that
     * aren't actual media posts.
     */
    results =
      results.filter(item => {
        const lower =
          item.title
            .toLowerCase();

        if (
          lower.startsWith("visit ") ||
          lower === "movies" ||
          lower === "tv series" ||
          lower.includes("menu")
        ) {
          return false;
        }

        if (
          type === "movie"
        ) {
          return (
            item.type === "movie" ||
            lower.includes("movie")
          );
        }

        if (
          type === "series"
        ) {
          return (
            item.type === "series" ||
            lower.includes("series")
          );
        }

        return true;
      });

    setCache(
      cacheKey,
      results,
      10 * 60 * 1000
    );
  }

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
    type,
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

module.exports = {
  searchNkiri,
  getLatest
};
