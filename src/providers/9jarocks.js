const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const BASE = "https://9jarocks.net";

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
  "accept-language":
    "en-US,en;q=0.9"
};

function clean(value) {
  return String(value || "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&#39;/g, "'")
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, " ")
    .trim();
}

function absolute(href, base = BASE) {
  if (!href) return null;

  try {
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

function detectType(title) {
  const text =
    clean(title).toLowerCase();

  if (
    /\bseason\s*\d+/i.test(text) ||
    /\bs\d{1,3}\b/i.test(text) ||
    /\bepisode\s*\d+/i.test(text) ||
    text.includes("series") ||
    text.includes("tv show")
  ) {
    return "series";
  }

  return "movie";
}

function extractYear(title) {
  const match =
    clean(title).match(
      /\b(19|20)\d{2}\b/
    );

  return match
    ? Number(match[0])
    : null;
}

function extractSeason(title) {
  const text =
    String(title || "");

  let match =
    text.match(
      /\bseason\s*0?(\d{1,3})\b/i
    );

  if (!match) {
    match =
      text.match(
        /\bS0?(\d{1,3})\b/i
      );
  }

  return match
    ? Number(match[1])
    : null;
}

function extractId(url) {
  const match =
    String(url || "")
      .match(
        /id(\d+)\.html/i
      );

  return match
    ? match[1]
    : null;
}

function isTitleUrl(url) {
  try {
    const parsed =
      new URL(url);

    return (
      parsed.hostname.endsWith(
        "9jarocks.net"
      ) &&
      (
        /\/videodownload\//i.test(
          parsed.pathname
        ) ||
        /id\d+\.html$/i.test(
          parsed.pathname
        )
      )
    );
  } catch {
    return false;
  }
}

async function request(url) {
  const response =
    await fetch(url, {
      dispatcher,
      headers:
        HEADERS,
      redirect:
        "follow"
    });

  if (!response.ok) {
    throw new Error(
      `9jaRocks HTTP ${response.status}`
    );
  }

  return response;
}

function parseSearch(html) {
  const $ =
    cheerio.load(
      html || ""
    );

  const results = [];
  const seen = new Set();

  $("a[href]").each(
    (_, element) => {
      const anchor =
        $(element);

      const url =
        absolute(
          anchor.attr("href")
        );

      if (
        !url ||
        !isTitleUrl(url) ||
        seen.has(url)
      ) {
        return;
      }

      let title =
        clean(
          anchor.attr("title")
        ) ||
        clean(
          anchor.text()
        );

      const parent =
        anchor.closest(
          "article, .post, .item, .movie, .result, li, div"
        );

      if (!title) {
        title =
          clean(
            parent
              .find(
                "h1,h2,h3,h4,.title,.name"
              )
              .first()
              .text()
          );
      }

      if (
        !title ||
        title.length < 2
      ) {
        return;
      }

      const image =
        absolute(
          anchor
            .find("img")
            .first()
            .attr("data-src") ||
          anchor
            .find("img")
            .first()
            .attr("src") ||
          parent
            .find("img")
            .first()
            .attr("data-src") ||
          parent
            .find("img")
            .first()
            .attr("src")
        );

      seen.add(url);

      results.push({
        provider:
          "9jarocks",
        id:
          extractId(url) ||
          url,
        title,
        cleanTitle:
          title,
        type:
          detectType(title),
        year:
          extractYear(title),
        season:
          extractSeason(title),
        image:
          image || null,
        url
      });
    }
  );

  return results;
}

async function search9jaRocks(
  query,
  limit = 30
) {
  const q =
    String(query || "")
      .trim();

  if (!q)
    return [];

  const collected = [];
  const seen = new Set();

  function add(items) {
    for (const item of items) {
      if (
        !item?.url ||
        seen.has(item.url)
      ) {
        continue;
      }

      seen.add(item.url);
      collected.push(item);
    }
  }

  /*
   * 9jaRocks search is paginated.
   * Older seasons such as Season 1 may be
   * several pages behind the newest result.
   */
  const MAX_PAGES = 12;

  for (
    let page = 1;
    page <= MAX_PAGES;
    page++
  ) {
    try {
      const url =
        page === 1
          ? new URL(
              "/findx",
              BASE
            )
          : new URL(
              `/findx/page/${page}`,
              BASE
            );

      url.searchParams.set(
        "search",
        q
      );

      const response =
        await request(
          url.href
        );

      const html =
        await response.text();

      const items =
        parseSearch(
          html
        );

      if (!items.length) {
        break;
      }

      add(items);

    } catch (error) {
      console.error(
        `9jaRocks search page ${page} error:`,
        error.message
      );
    }
  }

  const words =
    q.toLowerCase()
      .replace(
        /[^a-z0-9]+/g,
        " "
      )
      .split(/\s+/)
      .filter(Boolean);

  const filtered =
    collected.filter(
      item => {
        const title =
          String(
            item.title || ""
          ).toLowerCase();

        return words.every(
          word =>
            title.includes(word)
        );
      }
    );

  /*
   * Natural sorting:
   *
   * Season 1
   * Season 2
   * Season 9
   * Season 10
   *
   * rather than:
   *
   * Season 1
   * Season 10
   * Season 2
   */
  filtered.sort(
    (a, b) =>
      String(
        a.title || ""
      ).localeCompare(
        String(
          b.title || ""
        ),
        undefined,
        {
          numeric: true,
          sensitivity: "base"
        }
      )
  );

  return filtered.slice(
    0,
    Math.max(
      1,
      Number(limit) || 30
    )
  );
}

function parseEpisodeSources(
  $
) {
  const episodes = [];

  $('a[href*="loadedfiles.net"]')
    .each(
      (_, element) => {
        const anchor =
          $(element);

        const url =
          absolute(
            anchor.attr(
              "href"
            )
          );

        if (!url)
          return;

        const parent =
          anchor.closest(
            "p, div, li, td, section"
          );

        const context =
          clean(
            parent.text()
          );

        const match =
          context.match(
            /\bEPISODE\s*0?(\d{1,3})\b/i
          );

        if (!match)
          return;

        const episode =
          Number(
            match[1]
          );

        episodes.push({
          episode,
          label:
            `Episode ${episode}`,
          sources: [
            {
              host:
                "loadedfiles.net",
              label:
                clean(
                  anchor.text()
                ) ||
                "Server 1",
              url
            }
          ]
        });
      }
    );

  return episodes
    .sort(
      (a, b) =>
        a.episode -
        b.episode
    );
}

function parseMovieSources(
  $
) {
  const sources = [];

  $('a[href*="loadedfiles.net"]')
    .each(
      (_, element) => {
        const anchor =
          $(element);

        const url =
          absolute(
            anchor.attr(
              "href"
            )
          );

        if (!url)
          return;

        sources.push({
          host:
            "loadedfiles.net",
          label:
            clean(
              anchor.text()
            ) ||
            "Server 1",
          url
        });
      }
    );

  return sources;
}

async function get9jaTitle(
  url
) {
  const response =
    await request(url);

  const html =
    await response.text();

  const $ =
    cheerio.load(
      html
    );

  const title =
    clean(
      $("h1")
        .first()
        .text() ||
      $("title")
        .text()
    );

  const type =
    detectType(
      title
    );

  const season =
    extractSeason(
      title
    );

  const image =
    absolute(
      $('meta[property="og:image"]')
        .attr("content") ||
      $(".post img, article img")
        .first()
        .attr("src"),
      response.url
    );

  const description =
    clean(
      $('meta[name="description"]')
        .attr("content") ||
      $(".entry-content p, article p")
        .first()
        .text()
    );

  const episodes =
    parseEpisodeSources(
      $
    );

  const downloadLinks =
    type === "movie"
      ? parseMovieSources($)
      : [];

  return {
    provider:
      "9jarocks",
    id:
      extractId(
        response.url
      ),
    title,
    cleanTitle:
      title,
    type,
    season,
    year:
      extractYear(
        title
      ),
    image:
      image || null,
    description,
    url:
      response.url,
    episodes,
    downloadLinks
  };
}

async function inspectLoadedFiles(
  url
) {
  const response =
    await fetch(
      url,
      {
        dispatcher,
        headers:
          HEADERS,
        redirect:
          "follow"
      }
    );

  if (!response.ok) {
    throw new Error(
      `LoadedFiles HTTP ${response.status}`
    );
  }

  const html =
    await response.text();

  const $ =
    cheerio.load(
      html
    );

  return {
    url:
      response.url,

    title:
      clean(
        $("title")
          .text()
      ),

    html
  };
}

module.exports = {
  BASE,
  search9jaRocks,
  get9jaTitle,
  inspectLoadedFiles,
  parseSearch,
  extractSeason,
  extractId
};
