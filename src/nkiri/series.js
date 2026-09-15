const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");
const { classifyDownload } = require("../resolvers");

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
  "accept-language":
    "en-US,en;q=0.9"
};

function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

function detectEpisode(
  text,
  href,
  index
) {
  const combined =
    `${text} ${href}`;

  /*
   * Handles:
   * S02E01
   * S01E08
   * Season 2 Episode 1
   */
  const seasonEpisode =
    combined.match(
      /\bS(?:eason)?\s*0?(\d{1,2})\s*E(?:pisode)?\s*0?(\d{1,3})\b/i
    );

  if (seasonEpisode) {
    const season =
      Number(
        seasonEpisode[1]
      );

    const episode =
      Number(
        seasonEpisode[2]
      );

    return {
      season,
      episode,
      label:
        `S${String(season).padStart(2, "0")}` +
        `E${String(episode).padStart(2, "0")}`
    };
  }

  const episode =
    combined.match(
      /\b(?:Episode|EP)\s*0?(\d{1,3})\b/i
    );

  if (episode) {
    return {
      season: null,
      episode:
        Number(
          episode[1]
        ),
      label:
        `Episode ${Number(episode[1])}`
    };
  }

  return {
    season: null,
    episode:
      index + 1,
    label:
      text ||
      `Episode ${index + 1}`
  };
}



async function parseSeriesPage(url) {
  const response =
    await fetch(
      url,
      {
        dispatcher,
        headers: HEADERS,
        redirect: "follow"
      }
    );

  if (!response.ok) {
    throw new Error(
      `TheNkiri returned HTTP ${response.status}`
    );
  }

  const html =
    await response.text();

  const $ =
    cheerio.load(html);

  const title =
    clean(
      $("h1")
        .first()
        .text()
    ) ||
    clean(
      $("title")
        .text()
    );

  const poster =
    $('meta[property="og:image"]')
      .attr("content") ||
    $("article img")
      .first()
      .attr("src") ||
    null;

  const downloads = [];
  const seen =
    new Set();

  $("a").each(
    (_, element) => {
      const href =
        $(element)
          .attr("href");

      if (!href) return;

      let downloadUrl;

      try {
        downloadUrl =
          new URL(
            href,
            response.url
          ).href;
      } catch {
        return;
      }

      const sourceType =
        classifyDownload(
          downloadUrl
        );

      if (
        !sourceType ||
        sourceType === "external"
      ) {
        return;
      }

      const direct =
        sourceType === "direct";

      if (
        seen.has(
          downloadUrl
        )
      ) {
        return;
      }

      seen.add(
        downloadUrl
      );

      downloads.push({
        text:
          clean(
            $(element)
              .text()
          ),
        downloadUrl,
        direct:
          direct,
        sourceType:
          sourceType
      });
    }
  );

  const episodes =
    downloads.map(
      (item, index) => ({
        ...item,
        ...detectEpisode(
          item.text,
          item.downloadUrl,
          index
        )
      })
    );

  return {
    title,
    poster,
    isSeries:
      episodes.length > 1,
    episodes
  };
}

module.exports = {
  parseSeriesPage
};
