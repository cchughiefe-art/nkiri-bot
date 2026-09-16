require("dotenv").config();

const TelegramBot = require("node-telegram-bot-api");
const { create, get } = require("./callbacks");
const { consume } = require("./rate-limit");
const {
  incrementStat,
  reportBroken,
  incrementUserStat,
  trackDownload
} = require("./store");
const { resolveDownload } = require("../resolvers");
const {
  formatSize,
  makeMediaUrl,
  parseMovieXUrl,
  isMovieXTitleUrl,
  isMovieXMediaUrl,
  getMovieXInfo,
  getMovieXSources,
  extractSeasons
} = require("../providers/moviex");

function safeText(value, max = 600) {
  const text = String(value || "").replace(/\s+/g, " ").trim();
  if (text.length <= max) return text;
  return `${text.slice(0, Math.max(0, max - 1)).trim()}…`;
}

async function safeAnswer(bot, queryId, options = {}) {
  try {
    return await bot.answerCallbackQuery(queryId, options);
  } catch (error) {
    const message = String(
      error?.response?.body?.description ||
      error?.message ||
      ""
    );

    if (
      message.includes("query is too old") ||
      message.includes("query ID is invalid") ||
      message.includes("response timeout expired")
    ) {
      return false;
    }

    throw error;
  }
}

function movieCaption(subject, sources = []) {
  const year = String(subject?.releaseDate || "").slice(0, 4);
  const rating = Number(subject?.imdbRatingValue || 0);
  const genres = safeText(subject?.genre || "", 100);
  const description = safeText(subject?.description || "", 500);

  const details = [
    year || null,
    rating > 0 ? `⭐ ${rating}` : null,
    genres || null
  ].filter(Boolean).join(" • ");

  const qualities = sources.length
    ? sources
        .map(source => `${source.quality || "?"}p${source.size ? ` (${formatSize(source.size)})` : ""}`)
        .join(" • ")
    : "Loading qualities on demand";

  return (
    `🎬 ${subject?.title || "Movie"}\n` +
    `${details ? `${details}\n` : ""}` +
    `${description ? `\n${description}\n` : ""}` +
    `\nAvailable: ${qualities}\n\n` +
    `Choose a quality:`
  );
}

function seriesCaption(subject, seasons) {
  const year = String(subject?.releaseDate || "").slice(0, 4);
  const rating = Number(subject?.imdbRatingValue || 0);
  const genres = safeText(subject?.genre || "", 100);
  const description = safeText(subject?.description || "", 450);
  const details = [
    year || null,
    rating > 0 ? `⭐ ${rating}` : null,
    genres || null
  ].filter(Boolean).join(" • ");

  const totalEpisodes = seasons.reduce((sum, item) => sum + Number(item.maxEp || 0), 0);

  return (
    `📺 ${subject?.title || "TV Series"}\n` +
    `${details ? `${details}\n` : ""}` +
    `${description ? `\n${description}\n` : ""}` +
    `\n${seasons.length} season(s) • ${totalEpisodes} episode(s)\n\n` +
    `Choose a season:`
  );
}

async function sendPosterOrText(bot, chatId, poster, text, replyMarkup) {
  if (poster) {
    try {
      return await bot.sendPhoto(chatId, poster, {
        caption: text.slice(0, 1000),
        reply_markup: replyMarkup
      });
    } catch (error) {
      console.warn("MOVIEX POSTER ERROR:", error.message);
    }
  }

  return bot.sendMessage(chatId, text, {
    reply_markup: replyMarkup
  });
}

async function showMovie(bot, query, id, info) {
  const subject = info.subject;
  const sourceData = await getMovieXSources(id);
  const sources = sourceData.sources;

  if (!sources.length) {
    throw new Error("MovieX has no downloadable sources for this movie");
  }

  const buttons = sources
    .slice()
    .sort((a, b) => b.quality - a.quality)
    .map(source => [
      {
        text: `⬇️ ${source.quality || "?"}p${source.size ? ` • ${formatSize(source.size)}` : ""}`,
        callback_data: create("download", {
          url: makeMediaUrl(id, {
            kind: "movie",
            quality: source.quality,
            step: "final"
          }),
          title: subject.title,
          provider: "moviex"
        })
      }
    ]);

  buttons.push([
    { text: "🔎 Search Again", callback_data: "home:search" },
    { text: "🏠 Home", callback_data: "home:menu" }
  ]);

  await sendPosterOrText(
    bot,
    query.message.chat.id,
    subject?.cover?.url || subject?.thumbnail || null,
    movieCaption(subject, sources),
    { inline_keyboard: buttons }
  );
}

async function showSeason(bot, chatId, payload) {
  const season = Number(payload.season || 0);
  const maxEp = Math.min(50, Math.max(0, Number(payload.maxEp || 0)));

  if (!season || !maxEp) {
    throw new Error("MovieX season information is unavailable");
  }

  const rows = [];

  for (let episode = 1; episode <= maxEp; episode++) {
    const label = `S${String(season).padStart(2, "0")}E${String(episode).padStart(2, "0")}`;

    rows.push([
      {
        text: `▶️ ${label}`,
        callback_data: create("episode", {
          title: payload.title,
          label,
          downloadUrl: makeMediaUrl(payload.id, {
            kind: "episode",
            season,
            episode,
            quality: 480,
            step: "choose"
          }),
          direct: false,
          sourceType: "moviex"
        })
      }
    ]);
  }

  rows.push([
    { text: "🏠 Home", callback_data: "home:menu" }
  ]);

  await bot.sendMessage(
    chatId,
    `📺 ${payload.title}\nSeason ${season}\n\n${maxEp} episode(s)\nChoose an episode.\n\nBatch generation uses 480p when available.`,
    { reply_markup: { inline_keyboard: rows } }
  );
}

async function showSeries(bot, query, id, info) {
  const subject = info.subject;
  const seasons = extractSeasons(info);

  if (!seasons.length) {
    throw new Error("MovieX returned no season information");
  }

  if (seasons.length === 1) {
    await showSeason(bot, query.message.chat.id, {
      id,
      title: subject.title,
      season: seasons[0].season,
      maxEp: seasons[0].maxEp
    });
    return;
  }

  const buttons = seasons.map(season => [
    {
      text: `📺 Season ${season.season} • ${season.maxEp} eps`,
      callback_data: create("mxseason", {
        id,
        title: subject.title,
        season: season.season,
        maxEp: season.maxEp
      })
    }
  ]);

  buttons.push([
    { text: "🔎 Search Again", callback_data: "home:search" },
    { text: "🏠 Home", callback_data: "home:menu" }
  ]);

  await sendPosterOrText(
    bot,
    query.message.chat.id,
    subject?.cover?.url || subject?.thumbnail || null,
    seriesCaption(subject, seasons),
    { inline_keyboard: buttons }
  );
}

async function handleTitle(bot, query, item) {
  const parsed = parseMovieXUrl(item.url);
  if (!parsed?.id) return false;

  await safeAnswer(bot, query.id, { text: "Loading MovieX title…" });

  const status = await bot.sendMessage(
    query.message.chat.id,
    "Loading title information…"
  );

  try {
    const info = await getMovieXInfo(parsed.id);
    const isTv = parsed.type === "tv" || Number(info?.subject?.subjectType) === 2;

    await bot.deleteMessage(query.message.chat.id, status.message_id).catch(() => {});

    if (isTv) {
      await showSeries(bot, query, parsed.id, info);
    } else {
      await showMovie(bot, query, parsed.id, info);
    }
  } catch (error) {
    await bot.editMessageText(
      "❌ I couldn't load this MovieX title right now.",
      {
        chat_id: query.message.chat.id,
        message_id: status.message_id,
        reply_markup: {
          inline_keyboard: [
            [{ text: "🔎 Search Again", callback_data: "home:search" }],
            [{ text: "🏠 Home", callback_data: "home:menu" }]
          ]
        }
      }
    ).catch(() => {});
    throw error;
  }

  return true;
}

async function showEpisodeQualities(bot, query, payload, mediaUrl) {
  const parsed = parseMovieXUrl(mediaUrl);
  if (!parsed?.id || !parsed.season || !parsed.episode) {
    throw new Error("Invalid MovieX episode request");
  }

  await safeAnswer(bot, query.id, { text: "Loading qualities…" });

  const sourceData = await getMovieXSources(
    parsed.id,
    parsed.season,
    parsed.episode
  );

  if (!sourceData.sources.length) {
    throw new Error("MovieX has no sources for this episode");
  }

  const rows = sourceData.sources
    .slice()
    .sort((a, b) => b.quality - a.quality)
    .map(source => [
      {
        text: `⬇️ ${source.quality || "?"}p${source.size ? ` • ${formatSize(source.size)}` : ""}`,
        callback_data: create("episode", {
          title: payload.title,
          label: payload.label,
          downloadUrl: makeMediaUrl(parsed.id, {
            kind: "episode",
            season: parsed.season,
            episode: parsed.episode,
            quality: source.quality,
            step: "final"
          }),
          direct: false,
          sourceType: "moviex"
        })
      }
    ]);

  rows.push([
    { text: "🏠 Home", callback_data: "home:menu" }
  ]);

  const msg = await bot.sendMessage(
    query.message.chat.id,
    `📺 ${payload.title}\n${payload.label}\n\nChoose download quality:`
  );

  await bot.editMessageReplyMarkup(
    { inline_keyboard: rows },
    {
      chat_id: query.message.chat.id,
      message_id: msg.message_id
    }
  );
}

async function deliverMovieX(bot, query, payload, mediaUrl, kind) {
  const limit = consume(query.from.id, "download");

  if (!limit.allowed) {
    await safeAnswer(bot, query.id, {
      text: `Try again in ${limit.retryAfter}s.`
    });
    return;
  }

  await safeAnswer(bot, query.id, {
    text: "Generating fresh MovieX link…"
  });

  const chatId = query.message.chat.id;
  const status = await bot.sendMessage(
    chatId,
    "Generating a fresh MovieX download link…"
  );

  try {
    const resolved = await resolveDownload(mediaUrl);
    const parsed = parseMovieXUrl(mediaUrl);
    const quality = resolved.quality ? `${resolved.quality}p` : "Download";

    incrementStat("downloads");
    incrementUserStat(query.from.id, "downloads");

    trackDownload({
      userId: query.from.id,
      username: query.from.username || null,
      type: kind,
      title: payload.title || "MovieX",
      episode: kind === "episode" ? payload.label || null : null,
      provider: "moviex",
      quality: resolved.quality || null
    });

    await bot.editMessageText(
      `${kind === "episode" ? "📺" : "🎬"} ${payload.title || "MovieX"}\n` +
      `${kind === "episode" && payload.label ? `${payload.label}\n` : ""}` +
      `\n✅ ${quality} link ready` +
      `${resolved.sizeText ? ` • ${resolved.sizeText}` : ""}.\n` +
      `Start the download now because signed links can expire.`,
      {
        chat_id: chatId,
        message_id: status.message_id,
        reply_markup: {
          inline_keyboard: [
            [{ text: `⬇️ Download ${quality}`, url: resolved.directUrl }],
            [{ text: "🏠 Home", callback_data: "home:menu" }]
          ]
        }
      }
    );
  } catch (error) {
    incrementStat("failedDownloads");

    reportBroken(mediaUrl, {
      title: payload.title || "MovieX",
      episode: payload.label || null,
      provider: "moviex",
      error: error.message
    });

    await bot.editMessageText(
      "❌ MovieX couldn't generate this download link right now. Try again in a moment.",
      {
        chat_id: chatId,
        message_id: status.message_id,
        reply_markup: {
          inline_keyboard: [
            [{ text: "🔄 Try Again", callback_data: query.data }],
            [{ text: "🏠 Home", callback_data: "home:menu" }]
          ]
        }
      }
    ).catch(() => {});
  }
}

if (!TelegramBot.prototype.__nkiriMovieXIntegration) {
  TelegramBot.prototype.__nkiriMovieXIntegration = true;

  const originalOn = TelegramBot.prototype.on;

  TelegramBot.prototype.on = function(event, listener) {
    if (event !== "callback_query") {
      return originalOn.call(this, event, listener);
    }

    const bot = this;

    return originalOn.call(this, event, async function(query, ...args) {
      const data = String(query?.data || "");

      try {
        if (data.startsWith("title:")) {
          const item = get(data, "title");
          if (item && isMovieXTitleUrl(item.url)) {
            await handleTitle(bot, query, item);
            return;
          }
        }

        if (data.startsWith("mxseason:")) {
          const payload = get(data, "mxseason");
          await safeAnswer(bot, query.id);

          if (!payload) {
            await safeAnswer(bot, query.id, {
              text: "This MovieX season selection expired.",
              show_alert: true
            });
            return;
          }

          await showSeason(bot, query.message.chat.id, payload);
          return;
        }

        if (data.startsWith("download:")) {
          const payload = get(data, "download");

          if (payload && isMovieXMediaUrl(payload.url)) {
            await deliverMovieX(bot, query, payload, payload.url, "movie");
            return;
          }
        }

        if (data.startsWith("episode:")) {
          const payload = get(data, "episode");

          if (payload && isMovieXMediaUrl(payload.downloadUrl)) {
            const parsed = parseMovieXUrl(payload.downloadUrl);

            if (parsed?.step === "choose") {
              await showEpisodeQualities(bot, query, payload, payload.downloadUrl);
              return;
            }

            await deliverMovieX(
              bot,
              query,
              payload,
              payload.downloadUrl,
              "episode"
            );
            return;
          }
        }

        return listener.call(bot, query, ...args);
      } catch (error) {
        console.error("MOVIEX INTEGRATION ERROR:", error);

        await safeAnswer(bot, query.id, {
          text: "MovieX is temporarily unavailable. The bot is still online.",
          show_alert: true
        }).catch(() => {});

        return;
      }
    });
  };
}

console.log("MovieX provider enabled: search + movies + TV + quality links");
