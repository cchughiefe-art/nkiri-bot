const {
  cleanTitle
} = require("./media");

const {
  truncate
} = require("../nkiri/metadata");

function movieText(movie) {
  const lines = [
    `🎬 ${cleanTitle(movie.title)}`
  ];

  if (movie.description) {
    lines.push(
      "",
      truncate(
        movie.description,
        700
      )
    );
  }

  const meta = [];

  if (movie.year)
    meta.push(`📅 ${movie.year}`);

  if (movie.quality)
    meta.push(`🎞 ${movie.quality}`);

  if (movie.size)
    meta.push(`📦 ${movie.size}`);

  if (meta.length) {
    lines.push(
      "",
      meta.join("  •  ")
    );
  }

  return lines.join("\n");
}

function seriesText(series) {
  const lines = [
    `📺 ${cleanTitle(series.title)}`
  ];

  if (series.description) {
    lines.push(
      "",
      truncate(
        series.description,
        650
      )
    );
  }

  const meta = [];

  if (series.year)
    meta.push(`📅 ${series.year}`);

  if (series.quality)
    meta.push(`🎞 ${series.quality}`);

  if (series.episodeCount)
    meta.push(
      `📚 ${series.episodeCount} episodes`
    );

  if (meta.length) {
    lines.push(
      "",
      meta.join("  •  ")
    );
  }

  return lines.join("\n");
}

function sourceLabel(type) {
  switch (type) {
    case "downloadwella":
      return "Instant download";

    case "direct":
      return "Direct download";

    case "wetafiles":
      return "WetaFiles";

    default:
      return "External download";
  }
}

module.exports = {
  movieText,
  seriesText,
  sourceLabel
};
