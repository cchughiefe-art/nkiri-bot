const {
  cleanTitle
} = require("./media");

const {
  truncate
} = require("../nkiri/metadata");

const BRAND = "🎬  T H E  N K I R I";

function homeText() {
  return [
    BRAND,
    "",
    "Movies • TV Series • K-Drama",
    "",
    "Find your next watch and get the download link in a few taps.",
    "",
    "👇 Choose what you want to explore"
  ].join("\n");
}

function homeKeyboard() {
  return {
    inline_keyboard: [
      [
        {
          text: "🔎 Search",
          callback_data: "search:start"
        }
      ],
      [
        {
          text: "🎬 Latest Movies",
          callback_data: "latest:movie:1"
        },
        {
          text: "📺 Latest Series",
          callback_data: "latest:series:1"
        }
      ],
      [
        {
          text: "🇰🇷 K-Drama",
          callback_data: "latest:drama:1"
        }
      ],
      [
        {
          text: "❓ Help",
          callback_data: "help:show"
        }
      ]
    ]
  };
}

function movieCaption(movie) {
  const lines = [
    `🎬 ${cleanTitle(movie.title)}`
  ];

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

  if (movie.description) {
    lines.push(
      "",
      "📝 Synopsis",
      truncate(
        movie.description,
        650
      )
    );
  }

  return lines.join("\n");
}

function seriesCaption(series) {
  const lines = [
    `📺 ${cleanTitle(series.title)}`
  ];

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

  if (series.description) {
    lines.push(
      "",
      "📝 Synopsis",
      truncate(
        series.description,
        600
      )
    );
  }

  return lines.join("\n");
}

function downloadMessage(
  media,
  resolved
) {
  const lines = [
    `✅ Download ready`,
    "",
    `🎬 ${cleanTitle(media.title)}`
  ];

  if (media.size) {
    lines.push(
      `📦 ${media.size}`
    );
  }

  lines.push("");

  if (resolved.external) {
    lines.push(
      resolved.type === "wetafiles"
        ? "🌐 Continue on WetaFiles and tap “Create download link”."
        : "🌐 Continue on the download provider page."
    );
  } else {
    lines.push(
      "⬇️ Tap below to start your download."
    );
  }

  return lines.join("\n");
}

function downloadKeyboard(resolved) {
  return {
    inline_keyboard: [
      [
        {
          text:
            resolved.external
              ? "🌐 Continue to Download"
              : "⬇️ Download Now",
          url: resolved.directUrl
        }
      ],
      [
        {
          text: "🔎 Search Again",
          callback_data: "search:start"
        },
        {
          text: "🏠 Home",
          callback_data: "home:menu"
        }
      ]
    ]
  };
}

function episodeKeyboard(
  episodes,
  makeCallback
) {
  const rows = [];

  for (
    let i = 0;
    i < episodes.length;
    i += 2
  ) {
    const row = [];

    for (
      let j = i;
      j < Math.min(
        i + 2,
        episodes.length
      );
      j++
    ) {
      const episode =
        episodes[j];

      row.push({
        text:
          `▶️ ${episode.label}`,
        callback_data:
          makeCallback(
            episode,
            j
          )
      });
    }

    rows.push(row);
  }

  rows.push([
    {
      text: "🔎 Search",
      callback_data: "search:start"
    },
    {
      text: "🏠 Home",
      callback_data: "home:menu"
    }
  ]);

  return {
    inline_keyboard: rows
  };
}

function loadingText(type) {
  switch (type) {
    case "search":
      return "🔎 Searching TheNkiri…";

    case "movie":
      return "🎬 Loading movie details…";

    case "series":
      return "📺 Loading series details…";

    case "download":
      return "⏳ Preparing your download link…";

    default:
      return "⏳ Loading…";
  }
}

function errorText(error) {
  const message =
    String(
      error?.message || ""
    ).toLowerCase();

  if (
    message.includes("no download")
  ) {
    return [
      "⚠️ Download unavailable",
      "",
      "We couldn't find a working download link for this title.",
      "",
      "Try another title or check again later."
    ].join("\n");
  }

  return [
    "⚠️ Something went wrong",
    "",
    "The request could not be completed right now.",
    "",
    "Please try again."
  ].join("\n");
}

function helpText() {
  return [
    "❓ How to use TheNkiri Bot",
    "",
    "1️⃣ Search for a movie or series.",
    "2️⃣ Select the correct title.",
    "3️⃣ Read the synopsis and details.",
    "4️⃣ Choose an episode if it is a series.",
    "5️⃣ Tap the download button.",
    "",
    "🌐 WetaFiles downloads open in your browser, where you tap “Create download link”.",
    "",
    "Use 🏠 Home anytime to return to the main menu."
  ].join("\n");
}

module.exports = {
  BRAND,
  homeText,
  homeKeyboard,
  movieCaption,
  seriesCaption,
  downloadMessage,
  downloadKeyboard,
  episodeKeyboard,
  loadingText,
  errorText,
  helpText
};
