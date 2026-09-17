require("dotenv").config();

const crypto = require("crypto");
const TelegramBot = require("node-telegram-bot-api");
const { create: createCallback, get: getCallback } = require("./callbacks");
const { resolveDownload } = require("../resolvers");
const {
  incrementStat,
  reportBroken,
  incrementUserStat,
  trackDownload
} = require("./store");

const SESSION_TTL = 30 * 60 * 1000;
const MAX_SELECTED = 30;
const RESULT_CHUNK = 15;
const RESOLVE_CONCURRENCY = 3;
const sessions = new Map();

function cloneOptions(options) {
  if (!options) return options;
  return {
    ...options,
    reply_markup: options.reply_markup
      ? {
          ...options.reply_markup,
          inline_keyboard: Array.isArray(options.reply_markup.inline_keyboard)
            ? options.reply_markup.inline_keyboard.map(row => row.map(button => ({ ...button })))
            : options.reply_markup.inline_keyboard
        }
      : options.reply_markup
  };
}

function collectEpisodes(rows) {
  const episodes = [];
  const seen = new Set();
  for (const row of rows || []) {
    for (const button of row || []) {
      const cb = button?.callback_data;
      if (!cb?.startsWith("episode:")) continue;
      const p = getCallback(cb, "episode");
      if (!p || p.batchMode || !p.downloadUrl) continue;
      const key = `${p.label || ""}|${p.downloadUrl}`;
      if (seen.has(key)) continue;
      seen.add(key);
      episodes.push({
        title: p.title || "Series",
        label: p.label || `Episode ${episodes.length + 1}`,
        downloadUrl: p.downloadUrl,
        direct: p.direct === true,
        sourceType: p.sourceType || null
      });
    }
  }
  return episodes;
}

function enhanceOptions(options) {
  if (!options?.reply_markup?.inline_keyboard) return options;
  const copy = cloneOptions(options);
  const rows = copy.reply_markup.inline_keyboard;
  if (rows.some(row => row.some(b => b?.callback_data?.startsWith("batchselect:") || String(b?.text || "").includes("Full Season Links")))) return copy;

  const episodes = collectEpisodes(rows);
  if (episodes.length < 2) return copy;
  const title = episodes[0]?.title || "Series";
  const batchRow = [];

  if (episodes.length <= MAX_SELECTED) {
    batchRow.push({
      text: "⬇️ Full Season Links",
      callback_data: createCallback("episode", { batchMode: "all", title, episodes })
    });
  }

  batchRow.push({
    text: "☑️ Select Episodes",
    callback_data: createCallback("batchselect", { title, episodes })
  });

  let lastEpisodeIndex = -1;
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].some(b => b?.callback_data?.startsWith("episode:"))) lastEpisodeIndex = i;
  }
  rows.splice(lastEpisodeIndex + 1, 0, batchRow);
  return copy;
}

function newSessionId() {
  return crypto.randomBytes(5).toString("hex");
}

setInterval(() => {
  const cutoff = Date.now() - SESSION_TTL;
  for (const [id, s] of sessions) if (s.createdAt < cutoff) sessions.delete(id);
}, 10 * 60 * 1000);

function selectionKeyboard(s) {
  const rows = [];
  for (let i = 0; i < s.episodes.length; i += 2) {
    const row = [];
    for (let j = i; j < Math.min(i + 2, s.episodes.length); j++) {
      row.push({
        text: `${s.selected.has(j) ? "✅" : "⬜"} ${String(s.episodes[j].label || `Episode ${j + 1}`).slice(0, 28)}`,
        callback_data: `bs:t:${s.id}:${j}`
      });
    }
    rows.push(row);
  }
  rows.push([
    { text: s.episodes.length <= MAX_SELECTED ? "☑️ Select All" : `☑️ Select First ${MAX_SELECTED}`, callback_data: `bs:a:${s.id}` },
    { text: "🧹 Clear", callback_data: `bs:c:${s.id}` }
  ]);
  rows.push([{ text: `⬇️ Generate Selected (${s.selected.size})`, callback_data: s.generateCallback }]);
  rows.push([{ text: "❌ Cancel", callback_data: `bs:x:${s.id}` }]);
  return { inline_keyboard: rows };
}

async function safeAnswer(bot, id, options = {}) {
  try { return await bot.answerCallbackQuery(id, options); }
  catch (error) {
    const m = String(error?.message || error?.response?.body?.description || "");
    if (m.includes("query is too old") || m.includes("query ID is invalid") || m.includes("response timeout expired")) return false;
    throw error;
  }
}

async function showSelection(bot, query, payload) {
  const episodes = Array.isArray(payload?.episodes) ? payload.episodes : [];
  if (!episodes.length) {
    await safeAnswer(bot, query.id, { text: "This episode list expired. Open the season again.", show_alert: true });
    return;
  }
  const id = newSessionId();
  const s = {
    id,
    userId: query.from.id,
    chatId: query.message.chat.id,
    title: payload.title || episodes[0]?.title || "Series",
    episodes,
    selected: new Set(),
    createdAt: Date.now()
  };
  s.generateCallback = createCallback("episode", { batchMode: "session", sessionId: id, title: s.title });
  sessions.set(id, s);
  await safeAnswer(bot, query.id, { text: "Choose the episodes you want." });
  await bot.sendMessage(s.chatId, `☑️ Select Episodes\n\n${s.title}\n\nChoose up to ${MAX_SELECTED} episodes, then tap Generate Selected.`, { reply_markup: selectionKeyboard(s) });
}

async function updateSelection(bot, query) {
  const parts = String(query.data || "").split(":");
  const action = parts[1];
  const id = parts[2];
  const s = sessions.get(id);
  if (!s || Date.now() - s.createdAt > SESSION_TTL) {
    sessions.delete(id);
    await safeAnswer(bot, query.id, { text: "This selection expired. Open the season again.", show_alert: true });
    return;
  }
  if (String(s.userId) !== String(query.from.id)) {
    await safeAnswer(bot, query.id, { text: "This selection belongs to another user.", show_alert: true });
    return;
  }

  if (action === "t") {
    const index = Number(parts[3]);
    if (!Number.isInteger(index) || index < 0 || index >= s.episodes.length) return safeAnswer(bot, query.id);
    if (s.selected.has(index)) s.selected.delete(index);
    else {
      if (s.selected.size >= MAX_SELECTED) {
        await safeAnswer(bot, query.id, { text: `Maximum ${MAX_SELECTED} episodes per batch.`, show_alert: true });
        return;
      }
      s.selected.add(index);
    }
    await safeAnswer(bot, query.id);
  } else if (action === "a") {
    s.selected.clear();
    const n = Math.min(s.episodes.length, MAX_SELECTED);
    for (let i = 0; i < n; i++) s.selected.add(i);
    await safeAnswer(bot, query.id, { text: `${n} episode(s) selected.` });
  } else if (action === "c") {
    s.selected.clear();
    await safeAnswer(bot, query.id, { text: "Selection cleared." });
  } else if (action === "x") {
    sessions.delete(id);
    await safeAnswer(bot, query.id, { text: "Selection cancelled." });
    await bot.editMessageText("❌ Episode selection cancelled.", { chat_id: query.message.chat.id, message_id: query.message.message_id }).catch(() => {});
    return;
  } else return safeAnswer(bot, query.id);

  await bot.editMessageReplyMarkup(selectionKeyboard(s), { chat_id: query.message.chat.id, message_id: query.message.message_id }).catch(error => {
    if (!String(error?.message || "").includes("message is not modified")) console.error("BATCH SELECTION UI ERROR:", error.message);
  });
}

function validUrl(value) {
  try {
    const u = new URL(String(value || ""));
    return u.protocol === "http:" || u.protocol === "https:";
  } catch { return false; }
}

async function resolveEpisodes(episodes, query) {
  const results = new Array(episodes.length);
  let next = 0;
  async function worker() {
    while (true) {
      const i = next++;
      if (i >= episodes.length) return;
      const ep = episodes[i];
      try {
        const resolved = await resolveDownload(ep.downloadUrl);
        const directUrl = resolved?.directUrl;
        if (!validUrl(directUrl)) throw new Error("Resolver returned no usable URL");
        results[i] = { ok: true, episode: ep, directUrl, sourceType: resolved?.sourceType || ep.sourceType || null };
        incrementStat("downloads");
        incrementUserStat(query.from.id, "downloads");
        trackDownload({ userId: query.from.id, username: query.from.username || null, type: "episode", title: ep.title || "Series", episode: ep.label || `Episode ${i + 1}`, provider: resolved?.sourceType || null, batch: true });
      } catch (error) {
        results[i] = { ok: false, episode: ep, error: error.message };
        incrementStat("failedDownloads");
        reportBroken(ep.downloadUrl, { title: ep.title || "Series", episode: ep.label || `Episode ${i + 1}`, error: error.message, batch: true });
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(RESOLVE_CONCURRENCY, episodes.length) }, () => worker()));
  return results;
}

function resultKeyboard(items) {
  return {
    inline_keyboard: items.filter(x => x?.ok && validUrl(x.directUrl)).map(x => [{
      text: `⬇️ ${String(x.episode.label || "Episode").slice(0, 45)}`,
      url: x.directUrl
    }])
  };
}

async function sendBatchResults(bot, query, status, title, results) {
  const ok = results.filter(x => x?.ok);
  const failed = results.filter(x => !x?.ok);
  if (!ok.length) {
    await bot.editMessageText(`❌ ${title}\n\nI couldn't generate any episode links right now.\n\nFailed: ${failed.length}`, {
      chat_id: query.message.chat.id,
      message_id: status.message_id,
      reply_markup: { inline_keyboard: [[{ text: "⚠️ Report Problem", callback_data: "report:menu" }], [{ text: "🏠 Home", callback_data: "home:menu" }]] }
    });
    return;
  }

  const chunks = [];
  for (let i = 0; i < ok.length; i += RESULT_CHUNK) chunks.push(ok.slice(i, i + RESULT_CHUNK));
  const summary = `📺 ${title}\n\n✅ ${ok.length} download link(s) ready${failed.length ? `\n❌ ${failed.length} failed` : ""}\n\nTap an episode below to start downloading.\n⚠️ Fresh links may expire, so start your downloads soon.`;
  await bot.editMessageText(summary, { chat_id: query.message.chat.id, message_id: status.message_id, reply_markup: resultKeyboard(chunks[0]) });
  for (let i = 1; i < chunks.length; i++) {
    await bot.sendMessage(query.message.chat.id, `📺 ${title}\nLinks ${i * RESULT_CHUNK + 1}–${Math.min((i + 1) * RESULT_CHUNK, ok.length)}`, { reply_markup: resultKeyboard(chunks[i]) });
  }
  if (failed.length) {
    const labels = failed.slice(0, 10).map(x => `• ${x.episode?.label || "Episode"}`).join("\n");
    await bot.sendMessage(query.message.chat.id, `⚠️ Some links could not be generated:\n\n${labels}${failed.length > 10 ? `\n…and ${failed.length - 10} more.` : ""}`);
  }
}

async function handleBatch(bot, query, payload) {
  let episodes = [];
  let title = payload?.title || "Series";
  if (payload?.batchMode === "all") episodes = Array.isArray(payload.episodes) ? payload.episodes : [];
  else if (payload?.batchMode === "session") {
    const s = sessions.get(payload.sessionId);
    if (!s || String(s.userId) !== String(query.from.id)) {
      await safeAnswer(bot, query.id, { text: "This selection expired. Open the season again.", show_alert: true });
      return;
    }
    title = s.title;
    episodes = [...s.selected].sort((a, b) => a - b).map(i => s.episodes[i]).filter(Boolean);
    if (!episodes.length) {
      await safeAnswer(bot, query.id, { text: "Select at least one episode first.", show_alert: true });
      return;
    }
    sessions.delete(payload.sessionId);
  }

  if (!episodes.length) return safeAnswer(bot, query.id, { text: "No episodes found for this batch.", show_alert: true });
  if (episodes.length > MAX_SELECTED) return safeAnswer(bot, query.id, { text: `Maximum ${MAX_SELECTED} episodes per batch. Use Select Episodes.`, show_alert: true });

  await safeAnswer(bot, query.id, { text: `Generating ${episodes.length} fresh link(s)…` });
  const status = await bot.sendMessage(query.message.chat.id, `⏳ Generating ${episodes.length} fresh episode link(s)…\n\nPlease keep Telegram open for a moment.`);
  const results = await resolveEpisodes(episodes, query);
  await sendBatchResults(bot, query, status, title, results);
}

if (!TelegramBot.prototype.__nkiriBatchDownloads) {
  TelegramBot.prototype.__nkiriBatchDownloads = true;

  const originalSendMessage = TelegramBot.prototype.sendMessage;
  TelegramBot.prototype.sendMessage = function(chatId, text, options) {
    return originalSendMessage.call(this, chatId, text, enhanceOptions(options));
  };

  const originalSendPhoto = TelegramBot.prototype.sendPhoto;
  TelegramBot.prototype.sendPhoto = function(chatId, photo, options, fileOptions) {
    return originalSendPhoto.call(this, chatId, photo, enhanceOptions(options), fileOptions);
  };

  const originalEditMessageText = TelegramBot.prototype.editMessageText;
  TelegramBot.prototype.editMessageText = function(text, options) {
    return originalEditMessageText.call(this, text, enhanceOptions(options));
  };

  // launch-upgrades.js loads first; preserve its callback wrapper so its
  // device, channel-membership and daily-share checks run before episode batches.
  const originalOn = TelegramBot.prototype.on;
  TelegramBot.prototype.on = function(event, listener) {
    if (event !== "callback_query") return originalOn.call(this, event, listener);
    const bot = this;
    return originalOn.call(this, event, async function(query, ...args) {
      const data = query?.data || "";
      try {
        if (data.startsWith("batchselect:")) {
          const payload = getCallback(data, "batchselect");
          if (!payload) {
            await safeAnswer(bot, query.id, { text: "This episode list expired. Open the season again.", show_alert: true });
            return;
          }
          await showSelection(bot, query, payload);
          return;
        }
        if (data.startsWith("bs:")) {
          await updateSelection(bot, query);
          return;
        }
        if (data.startsWith("episode:")) {
          const payload = getCallback(data, "episode");
          if (payload?.batchMode) {
            await handleBatch(bot, query, payload);
            return;
          }
        }
        return listener.call(bot, query, ...args);
      } catch (error) {
        console.error("BATCH DOWNLOAD ERROR:", error);
        await safeAnswer(bot, query.id, { text: "Batch download failed. Try again.", show_alert: true }).catch(() => {});
      }
    });
  };
}

console.log(`Batch downloads enabled: max=${MAX_SELECTED}, concurrency=${RESOLVE_CONCURRENCY}`);
