const {
  searchNkiri
} = require("./search");

const {
  cleanTitle,
  normalizeForSearch,
  similarity
} = require("../core/media");

function extractSeason(title) {
  const text =
    String(title || "");

  let match =
    text.match(
      /\bS(?:eason)?\s*0?(\d{1,2})\b/i
    );

  if (match) {
    return Number(
      match[1]
    );
  }

  return null;
}

function getBaseSeriesTitle(title) {
  return cleanTitle(title)
    .replace(
      /\s+\bS(?:eason)?\s*0?\d{1,2}\b.*$/i,
      ""
    )
    .replace(
      /\s+\((?:Complete|Episode.*)\).*$/i,
      ""
    )
    .trim();
}

function isSameSeries(
  baseTitle,
  candidateTitle
) {
  const base =
    normalizeForSearch(
      baseTitle
    );

  const candidateBase =
    normalizeForSearch(
      getBaseSeriesTitle(
        candidateTitle
      )
    );

  if (!base || !candidateBase) {
    return false;
  }

  if (
    base === candidateBase
  ) {
    return true;
  }

  /*
   * Fuzzy fallback for punctuation and
   * small naming differences.
   * Keep threshold high so unrelated
   * search results aren't grouped.
   */
  return (
    similarity(
      baseTitle,
      getBaseSeriesTitle(
        candidateTitle
      )
    ) >= 0.82
  );
}

function seasonQuality(item) {
  const title =
    String(
      item.cleanTitle ||
      item.title ||
      ""
    ).toLowerCase();

  let score = 0;

  if (
    title.includes(
      "complete"
    )
  ) {
    score += 2;
  }

  if (
    item.url.includes(
      "complete"
    )
  ) {
    score += 1;
  }

  /*
   * Slight preference for cleaner URLs.
   * This helps when TheNkiri search has
   * duplicate pages for one season.
   */
  score -=
    item.url.length /
    10000;

  return score;
}

async function discoverSeasons(
  title
) {
  const baseTitle =
    getBaseSeriesTitle(
      title
    );

  if (!baseTitle) {
    return {
      baseTitle:
        cleanTitle(title),
      seasons: []
    };
  }

  const result =
    await searchNkiri(
      baseTitle,
      {
        page: 1,
        perPage: 8
      }
    );

  const bySeason =
    new Map();

  for (
    const item
    of result.results
  ) {
    if (
      item.type !== "series"
    ) {
      continue;
    }

    if (
      !isSameSeries(
        baseTitle,
        item.cleanTitle ||
        item.title
      )
    ) {
      continue;
    }

    /*
     * TheNkiri search can occasionally return
     * a correct-looking title attached to an
     * unrelated old post URL.
     *
     * Example:
     * Reacher S01 -> /suspicion-s01-...
     *
     * Reject obvious title/URL mismatches.
     */
    const baseWords =
      normalizeForSearch(baseTitle)
        .split(/\s+/)
        .filter(word => word.length >= 4);

    const normalizedUrl =
      normalizeForSearch(
        item.url
          .replace(/^https?:\/\/[^/]+/i, "")
          .replace(/[-_/]+/g, " ")
      );

    if (
      baseWords.length &&
      !baseWords.some(
        word =>
          normalizedUrl.includes(word)
      )
    ) {
      console.log(
        `Skipping suspicious season result: ${item.cleanTitle || item.title} -> ${item.url}`
      );

      continue;
    }

    const season =
      extractSeason(
        item.cleanTitle ||
        item.title
      );

    if (!season) {
      continue;
    }

    const existing =
      bySeason.get(
        season
      );

    if (
      !existing ||
      seasonQuality(item) >
      seasonQuality(existing)
    ) {
      bySeason.set(
        season,
        {
          season,
          title:
            item.cleanTitle ||
            cleanTitle(
              item.title
            ),
          url:
            item.url,
          image:
            item.image ||
            null
        }
      );
    }
  }

  /*
   * Always include the selected page
   * later in bot.js if search happens
   * not to return it.
   */
  const seasons =
    [...bySeason.values()]
      .sort(
        (a, b) =>
          a.season -
          b.season
      );

  return {
    baseTitle,
    seasons
  };
}

module.exports = {
  extractSeason,
  getBaseSeriesTitle,
  isSameSeries,
  discoverSeasons
};
