function clean(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

const BLOCKED = [
  "download movie",
  "download episode",
  "download nollywood",
  "download hollywood",
  "fzmovies",
  "netnaija",
  "o2tvseries",
  "torrent",
  "for readability",
  "i am buying a book",
  "i have bought",
  "transition words",
  "report abuse"
];

function extractDescription($) {
  const candidates = [];

  $("article p, .entry-content p").each(
    (index, element) => {
      const text = clean(
        $(element).text()
      );

      if (
        text.length < 70 ||
        text.length > 1500
      ) return;

      const lower =
        text.toLowerCase();

      if (
        BLOCKED.some(x =>
          lower.includes(x)
        )
      ) return;

      let score = 0;

      if (text.length >= 100) score += 5;
      if (text.length >= 150) score += 2;
      if (text.length <= 700) score += 2;

      score +=
        Math.max(0, 7 - index);

      if (
        /subtitles?|vlc|audio is|download size|click here|create download|password/i
          .test(text)
      ) {
        score -= 10;
      }

      candidates.push({
        text,
        score
      });
    }
  );

  candidates.sort(
    (a, b) =>
      b.score - a.score
  );

  return candidates[0]?.text || null;
}

function extractYear(title) {
  const matches =
    String(title || "")
      .match(/\b(?:19|20)\d{2}\b/g);

  return matches?.at(-1) || null;
}

function extractSize(text) {
  const match =
    String(text || "").match(
      /(?:Download\s*)?Size\s*(?:This Video is)?\s*:?\s*([\d.]+\s*(?:MB|GB))/i
    );

  return match?.[1] || null;
}

function extractQuality(text) {
  const match =
    String(text || "").match(
      /\b(2160p|1080p|720p|480p|4K|WEB[- ]?DL|WEBRip|BluRay|HDRip|HDTV)\b/i
    );

  return match?.[1] || null;
}

function truncate(text, max = 700) {
  const value = clean(text);

  if (value.length <= max)
    return value;

  return (
    value.slice(0, max - 1)
      .replace(/\s+\S*$/, "") +
    "…"
  );
}

module.exports = {
  clean,
  extractDescription,
  extractYear,
  extractSize,
  extractQuality,
  truncate
};
