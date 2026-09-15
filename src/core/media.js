function cleanTitle(input) {
  let title =
    String(input || "")
      .replace(/\s+/g, " ")
      .trim();

  title = title
    .replace(/^DOWNLOAD\s+/i, "")
    .replace(
      /\s*\|\s*Download.*$/i,
      ""
    )
    .replace(
      /\s*-\s*TheNkiri\s*\.?com.*$/i,
      ""
    )
    .trim();

  return title;
}

function extractYear(input) {
  const matches =
    String(input || "")
      .match(/\b(19|20)\d{2}\b/g);

  if (!matches?.length) {
    return null;
  }

  return Number(
    matches[matches.length - 1]
  );
}

function detectType(input) {
  const text =
    String(input || "")
      .toLowerCase();

  if (
    /\bs\d{1,2}\b/.test(text) ||
    text.includes("tv series") ||
    text.includes("series")
  ) {
    return "series";
  }

  return "movie";
}

function extractQuality(input) {
  const text =
    String(input || "");

  const match =
    text.match(
      /\b(2160p|1080p|720p|480p|BluRay|WEB[- .]?DL|WEBRip|HDRip|DVDRip)\b/i
    );

  return match
    ? match[1]
        .replace(/\s+/g, "")
    : null;
}

function normalizeForSearch(input) {
  return cleanTitle(input)
    .toLowerCase()
    .replace(
      /[^a-z0-9]+/g,
      " "
    )
    .trim();
}

function similarity(a, b) {
  const aa =
    new Set(
      normalizeForSearch(a)
        .split(" ")
        .filter(Boolean)
    );

  const bb =
    new Set(
      normalizeForSearch(b)
        .split(" ")
        .filter(Boolean)
    );

  if (!aa.size || !bb.size) {
    return 0;
  }

  let common = 0;

  for (const word of aa) {
    if (bb.has(word)) {
      common++;
    }
  }

  return (
    (2 * common) /
    (aa.size + bb.size)
  );
}

function rankResults(
  query,
  results
) {
  const q =
    normalizeForSearch(query);

  return [...results]
    .map(result => {
      const title =
        normalizeForSearch(
          result.title
        );

      let score =
        similarity(query, result.title);

      if (title === q) {
        score += 10;
      } else if (
        title.startsWith(q)
      ) {
        score += 5;
      } else if (
        title.includes(q)
      ) {
        score += 2;
      }

      return {
        ...result,
        cleanTitle:
          cleanTitle(result.title),
        year:
          extractYear(result.title),
        type:
          detectType(result.title),
        score
      };
    })
    .sort(
      (a, b) =>
        b.score - a.score
    );
}

module.exports = {
  cleanTitle,
  extractYear,
  detectType,
  extractQuality,
  normalizeForSearch,
  similarity,
  rankResults
};
