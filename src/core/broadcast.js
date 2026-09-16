require("dotenv").config();

const fs = require("fs");
const path = require("path");
const TelegramBot = require("node-telegram-bot-api");

const ADMIN_USER_ID = String(
  process.env.ADMIN_USER_ID ||
  process.env.REPORT_CHAT_ID ||
  "8006789415"
);

const DATA_DIR =
  process.env.DATA_DIR ||
  path.join(process.cwd(), "data");

const CORE_STORE_FILE =
  path.join(DATA_DIR, "store.json");

const UX_FILE =
  path.join(DATA_DIR, "ux.json");

const BROADCAST_DELAY_MS = Math.max(
  40,
  Number(process.env.BROADCAST_DELAY_MS || 60) || 60
);

const MAX_HISTORY = 50;
const installedBots = new WeakSet();
const sessions = new WeakMap();
const runStates = new WeakMap();

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function ensureDataDir() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

function writeJsonAtomic(file, data) {
  ensureDataDir();
  const temp = `${file}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(data, null, 2));
  fs.renameSync(temp, file);
}

function readCoreStore() {
  return readJson(CORE_STORE_FILE, {
    users: {},
    downloads: [],
    reports: [],
    stats: {}
  });
}

function readUx() {
  const state = readJson(UX_FILE, {
    users: {},
    maintenance: false,
    metrics: {}
  });

  state.users ||= {};
  state.metrics ||= {};
  state.broadcastHistory ||= [];
  return state;
}

function writeUx(state) {
  state.users ||= {};
  state.metrics ||= {};
  state.broadcastHistory ||= [];
  if (state.broadcastHistory.length > MAX_HISTORY) {
    state.broadcastHistory = state.broadcastHistory.slice(-MAX_HISTORY);
  }
  writeJsonAtomic(UX_FILE, state);
}

function isAdmin(userId) {
  return Boolean(ADMIN_USER_ID) && String(userId) === ADMIN_USER_ID;
}

function mergeUsers() {
  const core = readCoreStore();
  const ux = readUx();
  const merged = new Map();

  for (const [id, user] of Object.entries(core.users || {})) {
    merged.set(String(id), {
      id: user.id || Number(id),
      username: user.username || null,
      firstName: user.firstName || null,
      lastName: user.lastName || null,
      firstSeenAt: user.firstSeenAt || null,
      lastSeenAt: user.lastSeenAt || null,
      searches: Number(user.searches || 0),
      downloads: Number(user.downloads || 0),
      reports: Number(user.reports || 0),
      device: null,
      broadcastOptOut: false
    });
  }

  for (const [id, uxUser] of Object.entries(ux.users || {})) {
    const key = String(id);
    const existing = merged.get(key) || {
      id: uxUser.id || Number(id),
      searches: 0,
      downloads: 0,
      reports: 0
    };

    merged.set(key, {
      ...existing,
      username: uxUser.username || existing.username || null,
      firstName: uxUser.firstName || existing.firstName || null,
      lastName: uxUser.lastName || existing.lastName || null,
      firstSeenAt: existing.firstSeenAt || uxUser.firstSeenAt || null,
      lastSeenAt: Math.max(
        Number(existing.lastSeenAt || 0),
        Number(uxUser.lastSeenAt || 0)
      ) || null,
      device: uxUser.device || existing.device || null,
      broadcastOptOut: uxUser.broadcastOptOut === true
    });
  }

  return [...merged.values()].filter(user => user?.id);
}

const AUDIENCES = {
  all: {
    label: "All users",
    emoji: "👥",
    match: () => true
  },
  unused: {
    label: "Started but haven't used bot",
    emoji: "💤",
    match: user =>
      Number(user.searches || 0) === 0 &&
      Number(user.downloads || 0) === 0 &&
      Number(user.reports || 0) === 0
  },
  searched: {
    label: "Searched but never downloaded",
    emoji: "🔎",
    match: user =>
      Number(user.searches || 0) > 0 &&
      Number(user.downloads || 0) === 0
  },
  downloaded: {
    label: "Downloaded at least once",
    emoji: "⬇️",
    match: user => Number(user.downloads || 0) > 0
  },
  active7: {
    label: "Active in the last 7 days",
    emoji: "🟢",
    match: user =>
      Boolean(user.lastSeenAt) &&
      Date.now() - Number(user.lastSeenAt) <= 7 * 24 * 60 * 60 * 1000
  },
  android: {
    label: "Android users",
    emoji: "🤖",
    match: user => user.device === "android"
  },
  ios: {
    label: "iPhone / iPad users",
    emoji: "🍎",
    match: user => user.device === "ios"
  }
};

function recipientsFor(audienceKey) {
  const audience = AUDIENCES[audienceKey];
  if (!audience) return [];

  const seen = new Set();
  const result = [];

  for (const user of mergeUsers()) {
    const id = String(user.id || "");
    if (!/^\d+$/.test(id)) continue;
    if (user.broadcastOptOut === true) continue;
    if (!audience.match(user)) continue;
    if (seen.has(id)) continue;
    seen.add(id);
    result.push({ ...user, id });
  }

  return result;
}

function audienceCounts() {
  const counts = {};
  for (const key of Object.keys(AUDIENCES)) {
    counts[key] = recipientsFor(key).length;
  }
  return counts;
}

function audienceKeyboard() {
  const c = audienceCounts();
  return {
    inline_keyboard: [
      [{ text: `👥 All users (${c.all})`, callback_data: "bc:aud:all" }],
      [{ text: `💤 Started, no activity (${c.unused})`, callback_data: "bc:aud:unused" }],
      [{ text: `🔎 Searched, no download (${c.searched})`, callback_data: "bc:aud:searched" }],
      [{ text: `⬇️ Downloaded before (${c.downloaded})`, callback_data: "bc:aud:downloaded" }],
      [{ text: `🟢 Active 7 days (${c.active7})`, callback_data: "bc:aud:active7" }],
      [
        { text: `🤖 Android (${c.android})`, callback_data: "bc:aud:android" },
        { text: `🍎 iPhone (${c.ios})`, callback_data: "bc:aud:ios" }
      ],
      [{ text: "❌ Cancel", callback_data: "bc:cancel" }]
    ]
  };
}

function getSession(bot) {
  let session = sessions.get(bot);
  if (!session) {
    session = { stage: "idle" };
    sessions.set(bot, session);
  }
  return session;
}

function resetSession(bot) {
  const session = getSession(bot);
  const keep = {
    stage: "idle"
  };
  sessions.set(bot, keep);
  return session;
}

function getRunState(bot) {
  let state = runStates.get(bot);
  if (!state) {
    state = {
      running: false,
      cancelRequested: false
    };
    runStates.set(bot, state);
  }
  return state;
}

async function safeAnswer(bot, queryId, options = {}) {
  try {
    return await bot.answerCallbackQuery(queryId, options);
  } catch (error) {
    const text = String(
      error?.message ||
      error?.response?.body?.description ||
      ""
    );

    if (
      text.includes("query is too old") ||
      text.includes("query ID is invalid") ||
      text.includes("response timeout expired")
    ) {
      return false;
    }

    throw error;
  }
}

function messageKind(msg = {}) {
  if (msg.text) return "text";
  if (msg.photo) return "photo";
  if (msg.video) return "video";
  if (msg.animation) return "GIF/animation";
  if (msg.document) return "document";
  if (msg.audio) return "audio";
  if (msg.voice) return "voice";
  if (msg.sticker) return "sticker";
  if (msg.location) return "location";
  if (msg.contact) return "contact";
  return "message";
}

function controlCommand(text) {
  return /^\/(?:broadcast|broadcaststats|cancelbroadcast|notifications)(?:@\w+)?(?:\s|$)/i.test(
    String(text || "")
  );
}

function ensureUxUser(state, from = {}) {
  const id = String(from.id || "");
  if (!id) return null;

  const existing = state.users[id] || {
    id: from.id,
    firstSeenAt: Date.now(),
    lastSeenAt: Date.now()
  };

  existing.username = from.username || existing.username || null;
  existing.firstName = from.first_name || existing.firstName || null;
  existing.lastName = from.last_name || existing.lastName || null;
  existing.lastSeenAt = Date.now();
  state.users[id] = existing;
  return existing;
}

function setBroadcastOptOut(from, value) {
  const state = readUx();
  const user = ensureUxUser(state, from);
  if (!user) return false;

  user.broadcastOptOut = Boolean(value);
  if (value) {
    user.broadcastOptOutAt = Date.now();
  } else {
    user.broadcastOptOutAt = null;
  }

  writeUx(state);
  return true;
}

async function showAudiencePicker(bot, chatId) {
  await bot.sendMessage(
    chatId,
    "📣 Broadcast Center\n\nChoose who should receive this broadcast.\n\nUsers who muted broadcasts are automatically excluded.",
    { reply_markup: audienceKeyboard() }
  );
}

async function showContentPrompt(bot, chatId, audienceKey) {
  const audience = AUDIENCES[audienceKey];
  const recipients = recipientsFor(audienceKey);

  await bot.sendMessage(
    chatId,
    `📣 Audience: ${audience.emoji} ${audience.label}\n` +
    `Recipients right now: ${recipients.length}\n\n` +
    "Now send the message you want to broadcast.\n\n" +
    "Supported: text, photo, video, GIF, document, audio, voice, sticker and captions.\n" +
    "Send one message at a time. Albums are not supported as one broadcast.\n\n" +
    "Use /cancelbroadcast to cancel."
  );
}

async function captureContent(bot, msg) {
  const session = getSession(bot);
  if (session.stage !== "content") return false;
  if (!isAdmin(msg.from?.id)) return false;
  if (controlCommand(msg.text)) return false;

  if (msg.media_group_id) {
    await bot.sendMessage(
      msg.chat.id,
      "⚠️ Send one photo or video at a time. Telegram albums are not supported for broadcasts yet."
    );
    return true;
  }

  if (!msg.message_id) {
    await bot.sendMessage(msg.chat.id, "⚠️ I couldn't use that message. Send it again.");
    return true;
  }

  const audience = AUDIENCES[session.audience];
  if (!audience) {
    resetSession(bot);
    await bot.sendMessage(msg.chat.id, "Broadcast audience expired. Run /broadcast again.");
    return true;
  }

  let preview;
  try {
    preview = await bot.copyMessage(
      msg.chat.id,
      msg.chat.id,
      msg.message_id
    );
  } catch (error) {
    console.error("BROADCAST PREVIEW ERROR:", error.message);
    await bot.sendMessage(
      msg.chat.id,
      "⚠️ Telegram could not copy that message type. Try text, a single photo/video/GIF, or a document."
    );
    return true;
  }

  session.stage = "preview";
  session.sourceChatId = msg.chat.id;
  session.sourceMessageId = msg.message_id;
  session.previewMessageId = preview?.message_id || null;
  session.kind = messageKind(msg);

  const count = recipientsFor(session.audience).length;

  session.controlMessage = await bot.sendMessage(
    msg.chat.id,
    `📣 Broadcast Preview\n\n` +
    `Audience: ${audience.emoji} ${audience.label}\n` +
    `Recipients: ${count}\n` +
    `Content: ${session.kind}\n\n` +
    "This is the exact message users will receive. Send it?",
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: `✅ Send to ${count} user${count === 1 ? "" : "s"}`, callback_data: "bc:send" }],
          [{ text: "✏️ Replace Message", callback_data: "bc:edit" }],
          [{ text: "❌ Cancel", callback_data: "bc:cancel" }]
        ]
      }
    }
  );

  return true;
}

function errorInfo(error) {
  const body = error?.response?.body || {};
  return {
    code: Number(body.error_code || error?.response?.statusCode || 0),
    description: String(body.description || error?.message || "Unknown error"),
    retryAfter: Number(body?.parameters?.retry_after || 0)
  };
}

async function copyToRecipient(bot, recipientId, sourceChatId, sourceMessageId) {
  let attempts = 0;

  while (attempts < 3) {
    attempts += 1;

    try {
      await bot.copyMessage(
        recipientId,
        sourceChatId,
        sourceMessageId,
        {
          reply_markup: {
            inline_keyboard: [
              [{ text: "🔕 Mute broadcasts", callback_data: "bc:mute" }]
            ]
          }
        }
      );
      return { ok: true };
    } catch (error) {
      const info = errorInfo(error);

      if (info.code === 429 && info.retryAfter > 0 && attempts < 3) {
        await sleep(info.retryAfter * 1000 + 300);
        continue;
      }

      return {
        ok: false,
        blocked:
          info.code === 403 ||
          /blocked by the user|user is deactivated|bot was blocked/i.test(info.description),
        error: info.description
      };
    }
  }

  return { ok: false, blocked: false, error: "Retry limit reached" };
}

function recordBroadcast(result) {
  const state = readUx();
  state.broadcastHistory ||= [];
  state.metrics ||= {};

  state.broadcastHistory.push(result);
  if (state.broadcastHistory.length > MAX_HISTORY) {
    state.broadcastHistory = state.broadcastHistory.slice(-MAX_HISTORY);
  }

  state.metrics.broadcastsSent = Number(state.metrics.broadcastsSent || 0) + 1;
  state.metrics.broadcastDelivered =
    Number(state.metrics.broadcastDelivered || 0) + Number(result.delivered || 0);
  state.metrics.broadcastBlocked =
    Number(state.metrics.broadcastBlocked || 0) + Number(result.blocked || 0);
  state.metrics.broadcastFailed =
    Number(state.metrics.broadcastFailed || 0) + Number(result.failed || 0);

  writeUx(state);
}

async function runBroadcast(bot, adminChatId, session) {
  const run = getRunState(bot);
  if (run.running) {
    await bot.sendMessage(adminChatId, "A broadcast is already running.");
    return;
  }

  const audience = AUDIENCES[session.audience];
  const recipients = recipientsFor(session.audience);
  const total = recipients.length;

  run.running = true;
  run.cancelRequested = false;

  let delivered = 0;
  let blocked = 0;
  let failed = 0;
  let processed = 0;

  const progress = await bot.sendMessage(
    adminChatId,
    `📣 Broadcasting…\n\nAudience: ${audience.emoji} ${audience.label}\nProgress: 0/${total}`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: "⛔ Stop Broadcast", callback_data: "bc:stop" }]
        ]
      }
    }
  );

  const updateEvery = Math.max(10, Math.ceil(total / 10));

  try {
    for (const recipient of recipients) {
      if (run.cancelRequested) break;

      const result = await copyToRecipient(
        bot,
        recipient.id,
        session.sourceChatId,
        session.sourceMessageId
      );

      processed += 1;
      if (result.ok) delivered += 1;
      else if (result.blocked) blocked += 1;
      else failed += 1;

      if (processed % updateEvery === 0 || processed === total) {
        await bot.editMessageText(
          `📣 Broadcasting…\n\n` +
          `Audience: ${audience.emoji} ${audience.label}\n` +
          `Progress: ${processed}/${total}\n` +
          `✅ ${delivered} delivered | 🚫 ${blocked} blocked | ❌ ${failed} failed`,
          {
            chat_id: adminChatId,
            message_id: progress.message_id,
            reply_markup: {
              inline_keyboard: [
                [{ text: "⛔ Stop Broadcast", callback_data: "bc:stop" }]
              ]
            }
          }
        ).catch(() => {});
      }

      await sleep(BROADCAST_DELAY_MS);
    }
  } finally {
    const canceled = run.cancelRequested;
    run.running = false;
    run.cancelRequested = false;

    const history = {
      id: `BC-${Date.now().toString(36).toUpperCase()}`,
      createdAt: Date.now(),
      audience: session.audience,
      audienceLabel: audience.label,
      kind: session.kind || "message",
      targeted: total,
      processed,
      delivered,
      blocked,
      failed,
      canceled
    };

    recordBroadcast(history);

    await bot.editMessageText(
      `📣 Broadcast ${canceled ? "Stopped" : "Complete"}\n\n` +
      `Audience: ${audience.emoji} ${audience.label}\n` +
      `Targeted: ${total}\n` +
      `Processed: ${processed}\n` +
      `✅ Delivered: ${delivered}\n` +
      `🚫 Blocked/deactivated: ${blocked}\n` +
      `❌ Failed: ${failed}` +
      (canceled ? `\n⏭ Not sent: ${Math.max(0, total - processed)}` : ""),
      {
        chat_id: adminChatId,
        message_id: progress.message_id
      }
    ).catch(async () => {
      await bot.sendMessage(
        adminChatId,
        `📣 Broadcast ${canceled ? "Stopped" : "Complete"}\n\n` +
        `Targeted: ${total}\n✅ Delivered: ${delivered}\n🚫 Blocked: ${blocked}\n❌ Failed: ${failed}`
      );
    });
  }
}

function broadcastStatsText() {
  const ux = readUx();
  const history = ux.broadcastHistory || [];
  const optedOut = Object.values(ux.users || {}).filter(u => u.broadcastOptOut === true).length;
  const delivered = history.reduce((sum, b) => sum + Number(b.delivered || 0), 0);
  const blocked = history.reduce((sum, b) => sum + Number(b.blocked || 0), 0);
  const failed = history.reduce((sum, b) => sum + Number(b.failed || 0), 0);
  const last = history.at(-1);

  return (
    `📣 Broadcast Stats\n\n` +
    `Broadcasts sent: ${history.length}\n` +
    `✅ Total delivered: ${delivered}\n` +
    `🚫 Total blocked/deactivated: ${blocked}\n` +
    `❌ Total failed: ${failed}\n` +
    `🔕 Muted broadcasts: ${optedOut}\n\n` +
    (last
      ? `Last broadcast:\n` +
        `${last.audienceLabel || last.audience}\n` +
        `${last.delivered || 0}/${last.targeted || 0} delivered\n` +
        `${new Date(last.createdAt).toLocaleString()}`
      : "No broadcasts yet.")
  );
}

function installBroadcastCommands(bot, previousOnText) {
  if (installedBots.has(bot)) return;
  installedBots.add(bot);

  previousOnText.call(bot, /\/broadcast(?:\s+.*)?$/i, async msg => {
    if (!isAdmin(msg.from?.id)) return;

    const run = getRunState(bot);
    if (run.running) {
      await bot.sendMessage(
        msg.chat.id,
        "📣 A broadcast is currently running. Use /cancelbroadcast to stop it."
      );
      return;
    }

    resetSession(bot);
    const session = getSession(bot);
    session.stage = "audience";
    await showAudiencePicker(bot, msg.chat.id);
  });

  previousOnText.call(bot, /\/cancelbroadcast(?:\s+.*)?$/i, async msg => {
    if (!isAdmin(msg.from?.id)) return;

    const run = getRunState(bot);
    if (run.running) {
      run.cancelRequested = true;
      await bot.sendMessage(msg.chat.id, "⛔ Broadcast stop requested.");
      return;
    }

    resetSession(bot);
    await bot.sendMessage(msg.chat.id, "❌ Broadcast draft cancelled.");
  });

  previousOnText.call(bot, /\/broadcaststats(?:\s+.*)?$/i, async msg => {
    if (!isAdmin(msg.from?.id)) return;
    await bot.sendMessage(msg.chat.id, broadcastStatsText());
  });

  previousOnText.call(bot, /\/notifications(?:\s+(on|off|status))?$/i, async (msg, match) => {
    const action = String(match?.[1] || "status").toLowerCase();

    if (action === "off") {
      setBroadcastOptOut(msg.from, true);
      await bot.sendMessage(
        msg.chat.id,
        "🔕 Broadcast notifications are now off.\n\nUse /notifications on anytime to turn them back on."
      );
      return;
    }

    if (action === "on") {
      setBroadcastOptOut(msg.from, false);
      await bot.sendMessage(msg.chat.id, "🔔 Broadcast notifications are now on.");
      return;
    }

    const ux = readUx();
    const user = ux.users?.[String(msg.from?.id)] || {};
    await bot.sendMessage(
      msg.chat.id,
      user.broadcastOptOut
        ? "🔕 Broadcast notifications: OFF\nUse /notifications on to re-enable them."
        : "🔔 Broadcast notifications: ON\nUse /notifications off to mute them."
    );
  });
}

if (!TelegramBot.prototype.__nkiriBroadcasts) {
  TelegramBot.prototype.__nkiriBroadcasts = true;

  // launch-upgrades.js loads first. Preserve its onText wrapper so its
  // admin, onboarding, stats and other features continue to work.
  const previousOnText = TelegramBot.prototype.onText;
  TelegramBot.prototype.onText = function(regexp, listener) {
    installBroadcastCommands(this, previousOnText);
    return previousOnText.call(this, regexp, listener);
  };

  // Intercept the admin's next message while composing a broadcast, and
  // handle broadcast-specific callback buttons before the normal bot flow.
  const previousOn = TelegramBot.prototype.on;
  TelegramBot.prototype.on = function(event, listener) {
    const bot = this;

    if (event === "message") {
      return previousOn.call(this, event, async function(msg, ...args) {
        try {
          const session = getSession(bot);
          if (
            session.stage === "content" &&
            isAdmin(msg.from?.id) &&
            !controlCommand(msg.text)
          ) {
            const handled = await captureContent(bot, msg);
            if (handled) return;
          }
        } catch (error) {
          console.error("BROADCAST CAPTURE ERROR:", error);
          if (isAdmin(msg.from?.id)) {
            await bot.sendMessage(
              msg.chat.id,
              "⚠️ I couldn't capture that broadcast message. Try again or use /cancelbroadcast."
            ).catch(() => {});
            return;
          }
        }

        return listener.call(bot, msg, ...args);
      });
    }

    if (event !== "callback_query") {
      return previousOn.call(this, event, listener);
    }

    return previousOn.call(this, event, async function(query, ...args) {
      const data = String(query?.data || "");
      const chatId = query?.message?.chat?.id;
      const from = query?.from;

      try {
        if (data === "bc:mute") {
          setBroadcastOptOut(from, true);
          await safeAnswer(bot, query.id, {
            text: "Broadcasts muted. Use /notifications on to re-enable."
          });
          return;
        }

        if (!data.startsWith("bc:")) {
          return listener.call(bot, query, ...args);
        }

        if (!isAdmin(from?.id)) {
          await safeAnswer(bot, query.id, { text: "Admin only." });
          return;
        }

        const session = getSession(bot);
        const run = getRunState(bot);

        if (data.startsWith("bc:aud:")) {
          const audienceKey = data.split(":")[2];
          if (!AUDIENCES[audienceKey]) {
            await safeAnswer(bot, query.id, { text: "Unknown audience." });
            return;
          }

          session.stage = "content";
          session.audience = audienceKey;
          await safeAnswer(bot, query.id, { text: "Audience selected." });
          await showContentPrompt(bot, chatId, audienceKey);
          return;
        }

        if (data === "bc:edit") {
          if (session.stage !== "preview") {
            await safeAnswer(bot, query.id, { text: "Broadcast draft expired." });
            return;
          }

          session.stage = "content";
          if (session.previewMessageId) {
            await bot.deleteMessage(chatId, session.previewMessageId).catch(() => {});
          }
          await safeAnswer(bot, query.id, { text: "Send the replacement message." });
          await showContentPrompt(bot, chatId, session.audience);
          return;
        }

        if (data === "bc:cancel") {
          resetSession(bot);
          await safeAnswer(bot, query.id, { text: "Broadcast cancelled." });
          await bot.sendMessage(chatId, "❌ Broadcast cancelled.");
          return;
        }

        if (data === "bc:stop") {
          if (!run.running) {
            await safeAnswer(bot, query.id, { text: "No broadcast is running." });
            return;
          }

          run.cancelRequested = true;
          await safeAnswer(bot, query.id, { text: "Stopping broadcast…" });
          return;
        }

        if (data === "bc:send") {
          if (session.stage !== "preview" || !session.sourceMessageId) {
            await safeAnswer(bot, query.id, {
              text: "Broadcast draft expired. Run /broadcast again.",
              show_alert: true
            });
            return;
          }

          if (run.running) {
            await safeAnswer(bot, query.id, { text: "A broadcast is already running." });
            return;
          }

          const snapshot = { ...session };
          resetSession(bot);
          await safeAnswer(bot, query.id, { text: "Broadcast started." });

          runBroadcast(bot, chatId, snapshot).catch(async error => {
            console.error("BROADCAST RUN ERROR:", error);
            const state = getRunState(bot);
            state.running = false;
            state.cancelRequested = false;
            await bot.sendMessage(
              chatId,
              `❌ Broadcast stopped because of an unexpected error:\n${error.message}`
            ).catch(() => {});
          });
          return;
        }

        return listener.call(bot, query, ...args);
      } catch (error) {
        console.error("BROADCAST CALLBACK ERROR:", error);
        await safeAnswer(bot, query.id, {
          text: "Broadcast action failed. Try again.",
          show_alert: true
        }).catch(() => {});
      }
    });
  };
}

console.log("Broadcast center enabled: segmented audiences, previews, opt-out, safe delivery");
