const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const {
  cleanTitle,
  extractYear,
  detectType
} = require("../core/media");

const {
  getCache,
  setCache
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
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
};

const GENRES = {
  action: { name: "Action", emoji: "💥", slug: "action" },
  romance: { name: "Romance", emoji: "❤️", slug: "romance" },
  "sci-fi": { name: "Sci-Fi", emoji: "🚀", slug: "sci-fi" },
  comedy: { name: "Comedy", emoji: "😂", slug: "comedy" },
  horror: { name: "Horror", emoji: "👻", slug: "horror" },
  thriller: { name: "Thriller", emoji: "🔪", slug: "thriller" },
  drama: { name: "Drama", emoji: "🎭", slug: "drama" },
  crime: { name: "Crime", emoji: "🕵️", slug: "crime" },
  fantasy: { name: "Fantasy", emoji: "🧙", slug: "fantasy" },
  adventure: { name: "Adventure", emoji: "🗺️", slug: "adventure" },
  mystery: { name: "Mystery", emoji: "🧩", slug: "mystery" },
  animation: { name: "Animation", emoji: "🎨", slug: "animation" }
};

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

async function request(url) {
  const response = await fetch(url, {
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
}

function parsePosts(html, baseUrl) {
  const $ = cheerio.load(html);
  const results = [];
  const seen = new Set();

  $("article, .post, .type-post")
    .each((_, element) => {
      const item = $(element);

      const anchor = item
        .find(
          ".entry-title a, h1 a, h2 a, h3 a"
        )
        .first();

      const title = clean(anchor.text());
      const href = anchor.attr("href");

      if (!title || !href) return;

      let url;

      try {
        url = new URL(
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

      seen.add(url);

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

function getMaxSourcePage(html) {
  const $ = cheerio.load(html);

  let maxPage = 1;

  $("a.page-numbers")
    .each((_, element) => {
      const text =
        clean($(element).text());

      const number =
        Number(text);

      if (
        Number.isInteger(number) &&
        number > maxPage
      ) {
        maxPage = number;
      }

      const href =
        $(element)
          .attr("href") ||
        "";

      const match =
        href.match(
          /\/page\/(\d+)\//
        );

      if (match) {
        maxPage =
          Math.max(
            maxPage,
            Number(match[1])
          );
      }
    });

  return maxPage;
}

function sourceUrl(
  slug,
  sourcePage
) {
  return sourcePage === 1
    ? `https://thenkiri.com/tag/${slug}/`
    : `https://thenkiri.com/tag/${slug}/page/${sourcePage}/`;
}

async function getSourcePage(
  config,
  sourcePage
) {
  const cacheKey =
    `genre-source:${config.slug}:${sourcePage}:v2`;

  const cached =
    getCache(cacheKey);

  if (cached) {
    return cached;
  }

  console.log(
    `Loading ${config.name} source page ${sourcePage}...`
  );

  const response =
    await request(
      sourceUrl(
        config.slug,
        sourcePage
      )
    );

  const html =
    await response.text();

  const value = {
    posts:
      parsePosts(
        html,
        response.url
      ),
    maxSourcePage:
      getMaxSourcePage(html)
  };

  setCache(
    cacheKey,
    value,
    30 * 60 * 1000
  );

  return value;
}

async function discoverGenre(
  genre,
  options = {}
) {
  const config =
    GENRES[genre];

  if (!config) {
    throw new Error(
      "Unknown genre"
    );
  }

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
   * Fetch page 1 only first.
   * It tells us:
   * - posts per WordPress page
   * - how many source pages exist
   */
  const first =
    await getSourcePage(
      config,
      1
    );

  const sourcePageSize =
    Math.max(
      1,
      first.posts.length
    );

  const maxSourcePage =
    Math.max(
      1,
      first.maxSourcePage
    );

  /*
   * Fetch the final source page so total
   * title count is accurate without crawling
   * every page in between.
   */
  let last = first;

  if (maxSourcePage > 1) {
    last =
      await getSourcePage(
        config,
        maxSourcePage
      );
  }

  const total =
    maxSourcePage === 1
      ? first.posts.length
      : (
          (maxSourcePage - 1) *
          sourcePageSize
        ) +
        last.posts.length;

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

  const sourcePage =
    Math.floor(
      start /
      sourcePageSize
    ) + 1;

  const offset =
    start %
    sourcePageSize;

  let current;

  if (sourcePage === 1) {
    current = first;
  } else if (
    sourcePage === maxSourcePage
  ) {
    current = last;
  } else {
    current =
      await getSourcePage(
        config,
        sourcePage
      );
  }

  let results =
    current.posts.slice(
      offset,
      offset + perPage
    );

  /*
   * A Telegram page can cross a WordPress
   * source-page boundary.
   */
  if (
    results.length < perPage &&
    sourcePage < maxSourcePage
  ) {
    let next;

    if (
      sourcePage + 1 ===
      maxSourcePage
    ) {
      next = last;
    } else {
      next =
        await getSourcePage(
          config,
          sourcePage + 1
        );
    }

    results =
      results.concat(
        next.posts.slice(
          0,
          perPage -
          results.length
        )
      );
  }

  return {
    genre,
    name:
      config.name,
    emoji:
      config.emoji,
    results,
    total,
    page:
      safePage,
    pages,
    hasPrevious:
      safePage > 1,
    hasNext:
      safePage < pages
  };
}

module.exports = {
  GENRES,
  discoverGenre
};
