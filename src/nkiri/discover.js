const cheerio = require("cheerio");
const {
  fetch,
  Agent
} = require("undici");

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
  connect: {
    timeout: 30000
  },
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
  action: {
    name: "Action",
    emoji: "💥",
    slug: "action"
  },
  romance: {
    name: "Romance",
    emoji: "❤️",
    slug: "romance"
  },
  "sci-fi": {
    name: "Sci-Fi",
    emoji: "🚀",
    slug: "sci-fi"
  },
  comedy: {
    name: "Comedy",
    emoji: "😂",
    slug: "comedy"
  },
  horror: {
    name: "Horror",
    emoji: "👻",
    slug: "horror"
  },
  thriller: {
    name: "Thriller",
    emoji: "🔪",
    slug: "thriller"
  },
  drama: {
    name: "Drama",
    emoji: "🎭",
    slug: "drama"
  },
  crime: {
    name: "Crime",
    emoji: "🕵️",
    slug: "crime"
  },
  fantasy: {
    name: "Fantasy",
    emoji: "🧙",
    slug: "fantasy"
  },
  adventure: {
    name: "Adventure",
    emoji: "🗺️",
    slug: "adventure"
  },
  mystery: {
    name: "Mystery",
    emoji: "🧩",
    slug: "mystery"
  },
  animation: {
    name: "Animation",
    emoji: "🎨",
    slug: "animation"
  }
};

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

async function request(url) {
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
}

function parsePosts(html, baseUrl) {
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

    const title =
      clean(anchor.text());

    const href =
      anchor.attr("href");

    if (!title || !href) {
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
   * Each Telegram page can be built from
   * the corresponding WordPress tag page.
   * But caching several source pages gives
   * us stable Telegram pagination.
   */
  const cacheKey =
    `genre:${config.slug}:v1`;

  let results =
    getCache(cacheKey);

  if (!results) {
    results = [];

    const seen = new Set();

    /*
     * Load a useful catalogue without
     * crawling the entire website.
     */
    const MAX_PAGES = 10;

    for (
      let sourcePage = 1;
      sourcePage <= MAX_PAGES;
      sourcePage++
    ) {
      const url =
        sourcePage === 1
          ? `https://thenkiri.com/tag/${config.slug}/`
          : `https://thenkiri.com/tag/${config.slug}/page/${sourcePage}/`;

      console.log(
        `Loading ${config.name} page ${sourcePage}...`
      );

      let response;

      try {
        response =
          await request(url);
      } catch (error) {
        if (sourcePage > 1) {
          break;
        }

        throw error;
      }

      const html =
        await response.text();

      const posts =
        parsePosts(
          html,
          response.url
        );

      if (!posts.length) {
        break;
      }

      let added = 0;

      for (const post of posts) {
        if (seen.has(post.url)) {
          continue;
        }

        seen.add(post.url);
        results.push(post);
        added++;
      }

      if (!added) {
        break;
      }

      const $ =
        cheerio.load(html);

      const hasNext =
        Boolean(
          $(
            'a.next.page-numbers, .nav-links a.next, a[rel="next"]'
          ).length
        );

      if (!hasNext) {
        let numberedNext = false;

        $("a.page-numbers")
          .each((_, element) => {
            const href =
              $(element)
                .attr("href") ||
              "";

            if (
              href.includes(
                `/page/${sourcePage + 1}/`
              )
            ) {
              numberedNext = true;
            }
          });

        if (!numberedNext) {
          break;
        }
      }
    }

    setCache(
      cacheKey,
      results,
      30 * 60 * 1000
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
    genre,
    name:
      config.name,
    emoji:
      config.emoji,

    results:
      results.slice(
        start,
        start + perPage
      ),

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
