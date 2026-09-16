require("dotenv").config();

const fs = require("fs");
const path = require("path");
const TelegramBot = require("node-telegram-bot-api");
const { create: createCallback, get: getCallback } = require("./callbacks");
const { buildFullStats } = require("./full-stats");

const REQUIRED_CHANNEL =
  process.env.REQUIRED_CHANNEL || "@voidupdatezone";
const REQUIRED_CHANNEL_URL =
  process.env.REQUIRED_CHANNEL_URL || "https://t.me/voidupdatezone";
const SHARE_MODE =
  String(process.env.SHARE_MODE || "daily").toLowerCase();
const APP_TIMEZONE =
  process.env.APP_TIMEZONE || "Africa/Lagos";
const ADMIN_USER_ID = String(
  process.env.ADMIN_USER_ID ||
  process.env.REPORT_CHAT_ID ||
  "8006789415"
);
const BACKUP_ENABLED =
  String(process.env.BACKUP_ENABLED || "true").toLowerCase() !== "false";
const ENV_MAINTENANCE =
  String(process.env.MAINTENANCE_MODE || "false").toLowerCase() === "true";

const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
const UX_FILE = path.join(DATA_DIR, "ux.json");
const CORE_STORE_FILE = path.join(DATA_DIR, "store.json");

const VLC_ANDROID_URL =
  "https://play.google.com/store/apps/details?id=org.videolan.vlc";
const INFUSE_IOS_URL =
  "https://apps.apple.com/app/infuse-video-player/id1136220934";

const startListeners = new WeakMap();
const lastAttemptByChat = new Map();
const installedBots = new WeakSet();
const botUsernames = new WeakMap();

function emptyUx() {
  return {
    users: {},
    maintenance: ENV_MAINTENANCE,
    lastBackupDate: null,
    metrics: {
      starts: 0,
      deviceSelections: 0,
      sharesConfirmed: 0,
      membershipChecks: 0,
      membershipFailures: 0,
      referrals: 0
    }
  };
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

function readUx() {
  ensureDataDir();
  if (!fs.existsSync(UX_FILE)) {
    writeJsonAtomic(UX_FILE, emptyUx());
  }

  const state = readJson(UX_FILE, emptyUx());
  state.users ||= {};
  state.metrics ||= {};
  state.maintenance ??= ENV_MAINTENANCE;
  return state;
}

function writeUx(state) {
  writeJsonAtomic(UX_FILE, state);
}

function metric(state, name, amount = 1) {
  state.metrics ||= {};
  state.metrics[name] = (state.metrics[name] || 0) + amount;
}

function getUxUser(state, telegramUser = {}) {
  if (!telegramUser?.id) return null;
  const id = String(telegramUser.id);
  const existing = state.users[id] || {
    id: telegramUser.id,
    firstSeenAt: Date.now(),
    lastSeenAt: Date.now(),
    device: null,
    referrerId: null,
    referrals: 0,
    lastShareDate: null,
    lastSharedAt: null,
    channelVerifiedAt: null
  };

  existing.username = telegramUser.username || existing.username || null;
  existing.firstName = telegramUser.first_name || existing.firstName || null;
  existing.lastName = telegramUser.last_name || existing.lastName || null;
  existing.language = telegramUser.language_code || existing.language || null;
  existing.lastSeenAt = Date.now();

  state.users[id] = existing;
  return existing;
}

function dateParts() {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: APP_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23"
  }).formatToParts(new Date());

  const out = {};
  for (const part of parts) out[part.type] = part.value;
  return out;
}

function todayKey() {
  const p = dateParts();
  return `${p.year}-${p.month}-${p.day}`;
}

function isAdmin(userId) {
  return Boolean(ADMIN_USER_ID) && String(userId) === ADMIN_USER_ID;
}

async function safeAnswer(bot, queryId, options = {}) {
  try {
    return await bot.answerCallbackQuery(queryId, options);
  } catch (error) {
    const msg = String(
      error?.message || error?.response?.body?.description || ""
    );
    if (
      msg.includes("query is too old") ||
      msg.includes("query ID is invalid") ||
      msg.includes("response timeout expired")
    ) {
      return false;
    }
    throw error;
  }
}

async function getBotUsername(bot) {
  if (botUsernames.has(bot)) return botUsernames.get(bot);
  try {
    const me = await bot.getMe();
    const username = me.username || "";
    botUsernames.set(bot, username);
    return username;
  } catch {
    return "";
  }
}

async function referralShareUrl(bot, userId) {
  const username = await getBotUsername(bot);
  const botUrl = username
    ? `https://t.me/${username}?start=ref_${userId}`
    : "https://t.me/";

  return (
    "https://t.me/share/url?url=" +
    encodeURIComponent(botUrl) +
    "&text=" +
    encodeURIComponent(
      "Watch movies, series and K-Drama with TheNkiri Bot."
    )
  );
}

function recordReferral(state, user, startText) {
  const match = String(startText || "").match(/(?:^|\s)ref_(\d+)/i);
  if (!match || !user) return;

  const referrerId = String(match[1]);
  const userId = String(user.id);

  if (referrerId === userId || user.referrerId) return;

  user.referrerId = referrerId;

  const referrer = state.users[referrerId];
  if (referrer) {
    referrer.referrals = (referrer.referrals || 0) + 1;
  }

  metric(state, "referrals");
}

async function showDevicePicker(bot, chatId, firstTime = true) {
  await bot.sendMessage(
    chatId,
    firstTime
      ? "🎬 Welcome to TheNkiri Bot\n\nBefore we get started, what device are you using?"
      : "📱 Change device\n\nChoose the device you use for downloads:",
    {
      reply_markup: {
        inline_keyboard: [
          [
            { text: "🤖 Android", callback_data: "ux:device:android" },
            { text: "🍎 iPhone / iPad", callback_data: "ux:device:ios" }
          ]
        ]
      }
    }
  );
}

async function showPlayerGuide(bot, chatId, device) {
  if (device === "ios") {
    await bot.sendMessage(
      chatId,
      "🍎 iPhone / iPad selected\n\nFor the best experience, we recommend Infuse.\n\n• MKV and MP4 support\n• subtitles\n• multiple audio tracks\n• Picture-in-Picture\n\nHow to watch:\n1. Download the movie\n2. Save it to Files\n3. Open/share the file with Infuse",
      {
        reply_markup: {
          inline_keyboard: [
            [{ text: "📥 Install Infuse", url: INFUSE_IOS_URL }],
            [{ text: "✅ Continue", callback_data: "ux:device:continue" }]
          ]
        }
      }
    );
    return;
  }

  await bot.sendMessage(
    chatId,
    "🤖 Android selected\n\nFor the best movie experience, install VLC.\n\n• MKV and MP4 support\n• subtitles\n• multiple audio tracks\n• large movie files\n\nHow to watch:\n1. Download your movie\n2. Open the downloaded file\n3. Choose VLC",
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: "📥 Install VLC", url: VLC_ANDROID_URL }],
          [{ text: "✅ Continue", callback_data: "ux:device:continue" }]
        ]
      }
    }
  );
}

async function showJoinGate(bot, chatId) {
  await bot.sendMessage(
    chatId,
    "🔒 Channel membership required\n\nJoin @voidupdatezone before downloading.\n\nAfter joining, tap Verify Membership.",
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: "📢 Join @voidupdatezone", url: REQUIRED_CHANNEL_URL }],
          [{ text: "✅ Verify Membership", callback_data: "ux:gate:verify" }]
        ]
      }
    }
  );
}

async function showShareGate(bot, chatId, userId) {
  const url = await referralShareUrl(bot, userId);

  await bot.sendMessage(
    chatId,
    "📤 Daily share required\n\nShare the bot once today to unlock downloads for the rest of today.\n\nAfter sharing, tap I Shared.",
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: "📤 Share Bot", url }],
          [{ text: "✅ I Shared", callback_data: "ux:gate:shared" }]
        ]
      }
    }
  );
}

async function isChannelMember(bot, userId, state = null) {
  if (!REQUIRED_CHANNEL) return true;

  if (state) metric(state, "membershipChecks");

  try {
    const member = await bot.getChatMember(REQUIRED_CHANNEL, userId);
    const ok =
      member.status === "creator" ||
      member.status === "administrator" ||
      member.status === "member" ||
      (member.status === "restricted" && member.is_member === true);

    if (!ok && state) metric(state, "membershipFailures");
    return ok;
  } catch (error) {
    if (state) metric(state, "membershipFailures");
    console.error("CHANNEL CHECK ERROR:", error.message);
    return false;
  }
}

function playbackTipForUser(userId) {
  const state = readUx();
  const user = state.users[String(userId)];
  if (user?.device === "ios") {
    return "\n\n🍎 iPhone tip: save the file to Files, then open/share it with Infuse.";
  }
  if (user?.device === "android") {
    return "\n\n🤖 Android tip: open the downloaded file with VLC.";
  }
  return "";
}

function readCoreStore() {
  return readJson(CORE_STORE_FILE, {
    users: {},
    downloads: [],
    reports: [],
    stats: {}
  });
}

function formatUserName(user = {}) {
  return [user.firstName, user.lastName].filter(Boolean).join(" ") || "No name";
}

function enhancedStatsText() {
  return buildFullStats();
}

async function sendLong(bot, chatId, text) {
  const max = 3900;
  if (text.length <= max) {
    await bot.sendMessage(chatId, text);
    return;
  }

  let rest = text;
  while (rest.length) {
    let cut = Math.min(max, rest.length);
    if (cut < rest.length) {
      const nl = rest.lastIndexOf("\n", cut);
      if (nl > 1000) cut = nl;
    }
    await bot.sendMessage(chatId, rest.slice(0, cut));
    rest = rest.slice(cut).replace(/^\n+/, "");
  }
}

async function showHelp(bot, chatId, userId) {
  const state = readUx();
  const user = state.users[String(userId)] || {};
  const deviceText =
    user.device === "ios"
      ? "🍎 iPhone/iPad: save to Files and open with Infuse."
      : user.device === "android"
        ? "🤖 Android: open downloaded files with VLC."
        : "📱 Choose your device with /device.";

  await bot.sendMessage(
    chatId,
    `❓ TheNkiri Bot Help\n\n` +
      `1. Search for a movie, series or K-Drama.\n` +
      `2. Open the title and choose the movie/episode.\n` +
      `3. Join @voidupdatezone when asked.\n` +
      `4. Share the bot once each day to unlock downloads.\n` +
      `5. Open your downloaded file with the recommended player.\n\n` +
      `${deviceText}\n\n` +
      `Commands:\n` +
      `/device — change device\n` +
      `/resume — resume your recent selection\n` +
      `/report — report a problem\n` +
      `/privacy — what the bot stores`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: "📢 Updates Channel", url: REQUIRED_CHANNEL_URL }],
          [{ text: "📱 Change Device", callback_data: "ux:device:choose" }],
          [{ text: "🏠 Home", callback_data: "home:menu" }]
        ]
      }
    }
  );
}

function privacyText() {
  return (
    "🔐 Privacy\n\n" +
    "The bot stores only information needed to operate and improve the service: Telegram user ID, username/name when Telegram provides them, language, selected device, usage counts, referral relationship, channel/share access state, reports, and download activity.\n\n" +
    "The bot does not receive your phone number unless you explicitly send it, and it does not need your Telegram password."
  );
}

async function sendBackup(bot, manual = false) {
  if (!ADMIN_USER_ID) return false;
  ensureDataDir();

  const state = readUx();
  const today = todayKey();
  if (!manual && state.lastBackupDate === today) return false;

  const files = [CORE_STORE_FILE, UX_FILE].filter(file => fs.existsSync(file));
  if (!files.length) return false;

  try {
    await bot.sendMessage(
      ADMIN_USER_ID,
      `🗄 TheNkiri ${manual ? "Manual" : "Daily"} Backup — ${today}`
    );

    for (const file of files) {
      await bot.sendDocument(ADMIN_USER_ID, file, {
        caption: path.basename(file)
      });
    }

    state.lastBackupDate = today;
    writeUx(state);
    return true;
  } catch (error) {
    console.error("BACKUP ERROR:", error.message);
    return false;
  }
}

function installBotExtras(bot, originalOnText) {
  if (installedBots.has(bot)) return;
  installedBots.add(bot);

  const originalEditMessageText = bot.editMessageText.bind(bot);
  bot.editMessageText = async (text, options = {}) => {
    const chatId = options?.chat_id;
    if (chatId != null) {
      const attempt = lastAttemptByChat.get(String(chatId));
      if (attempt) {
        const failure = String(text).includes("download link is currently unavailable");
        if (failure) {
          options = {
            ...options,
            reply_markup: {
              inline_keyboard: [
                [{ text: "🔄 Generate New Link", callback_data: attempt.callbackData }],
                [{ text: "⚠️ Report Problem", callback_data: "report:menu" }],
                [{ text: "🏠 Home", callback_data: "home:menu" }]
              ]
            }
          };

          if (ADMIN_USER_ID) {
            bot.sendMessage(
              ADMIN_USER_ID,
              `❌ Download failure\nUser: ${attempt.username ? "@" + attempt.username : "ID " + attempt.userId}\nType: ${attempt.type}`
            ).catch(() => {});
          }

          lastAttemptByChat.delete(String(chatId));
        } else {
          const tip = playbackTipForUser(attempt.userId);
          if (tip && !String(text).includes("tip:")) {
            text = String(text) + tip;
          }
          lastAttemptByChat.delete(String(chatId));
        }
      }
    }

    return originalEditMessageText(text, options);
  };

  originalOnText.call(bot, /\/device(?:\s+.*)?$/, async msg => {
    const state = readUx();
    getUxUser(state, msg.from);
    writeUx(state);
    await showDevicePicker(bot, msg.chat.id, false);
  });

  originalOnText.call(bot, /\/help(?:\s+.*)?$/, async msg => {
    await showHelp(bot, msg.chat.id, msg.from.id);
  });

  originalOnText.call(bot, /\/privacy(?:\s+.*)?$/, async msg => {
    await bot.sendMessage(msg.chat.id, privacyText());
  });

  originalOnText.call(bot, /\/resume(?:\s+.*)?$/, async msg => {
    const state = readUx();
    const user = state.users[String(msg.from.id)];
    const progress = user?.lastProgress;

    if (!progress?.kind || !progress?.payload) {
      await bot.sendMessage(
        msg.chat.id,
        "I don't have a recent selection to resume yet. Use Search to find your title."
      );
      return;
    }

    let callbackData;
    try {
      callbackData = createCallback(progress.kind, progress.payload);
    } catch {
      await bot.sendMessage(
        msg.chat.id,
        "Your saved progress could not be restored. Please search for the title again."
      );
      return;
    }

    await bot.sendMessage(
      msg.chat.id,
      `▶️ Resume ${progress.label || "your recent selection"}:`,
      {
        reply_markup: {
          inline_keyboard: [
            [{ text: "▶️ Resume", callback_data: callbackData }],
            [{ text: "🔎 Search", callback_data: "home:search" }]
          ]
        }
      }
    );
  });

  originalOnText.call(bot, /\/user(?:\s+(\d+))?$/, async (msg, match) => {
    if (!isAdmin(msg.from?.id)) return;
    const id = match?.[1];
    if (!id) {
      await bot.sendMessage(msg.chat.id, "Usage: /user TELEGRAM_ID");
      return;
    }

    const core = readCoreStore();
    const ux = readUx();
    const user = core.users?.[String(id)];
    const uxUser = ux.users?.[String(id)] || {};
    if (!user && !uxUser.id) {
      await bot.sendMessage(msg.chat.id, "User not found.");
      return;
    }

    const u = user || uxUser;
    const device = uxUser.device === "ios" ? "iPhone/iPad" : uxUser.device === "android" ? "Android" : "Unknown";
    const referrer = uxUser.referrerId ? `ID ${uxUser.referrerId}` : "None";
    const text =
      `👤 User Details\n\n` +
      `Name: ${formatUserName(u)}\n` +
      `Username: ${u.username ? "@" + u.username : "No username"}\n` +
      `Telegram ID: ${u.id || id}\n` +
      `Language: ${u.language || "Unknown"}\n` +
      `Device: ${device}\n` +
      `Referrer: ${referrer}\n` +
      `Referrals: ${uxUser.referrals || 0}\n\n` +
      `🔎 Searches: ${u.searches || 0}\n` +
      `⬇️ Downloads: ${u.downloads || 0}\n` +
      `⚠️ Reports: ${u.reports || 0}\n` +
      `Shared today: ${uxUser.lastShareDate === todayKey() ? "Yes" : "No"}`;

    await bot.sendMessage(msg.chat.id, text);
  });

  originalOnText.call(bot, /\/maintenance(?:\s+(on|off|status))?$/i, async (msg, match) => {
    if (!isAdmin(msg.from?.id)) return;
    const state = readUx();
    const action = String(match?.[1] || "status").toLowerCase();
    if (action === "on") state.maintenance = true;
    if (action === "off") state.maintenance = false;
    writeUx(state);
    await bot.sendMessage(
      msg.chat.id,
      `🛠 Maintenance mode: ${state.maintenance ? "ON" : "OFF"}`
    );
  });

  originalOnText.call(bot, /\/backup(?:\s+.*)?$/, async msg => {
    if (!isAdmin(msg.from?.id)) return;
    const ok = await sendBackup(bot, true);
    await bot.sendMessage(
      msg.chat.id,
      ok ? "✅ Backup sent to this admin chat." : "❌ Backup failed. Check Raven logs."
    );
  });

  if (BACKUP_ENABLED) {
    setTimeout(() => sendBackup(bot, false).catch(() => {}), 15000);
    setInterval(() => sendBackup(bot, false).catch(() => {}), 60 * 60 * 1000);
  }
}

const proto = TelegramBot.prototype;
const originalOnText = proto.onText;
const originalOn = proto.on;

proto.onText = function(regexp, listener) {
  installBotExtras(this, originalOnText);
  const source = regexp?.source || "";

  if (source.includes("\\/start")) {
    startListeners.set(this, listener);
    const bot = this;

    return originalOnText.call(this, regexp, async function(msg, match) {
      const state = readUx();
      const user = getUxUser(state, msg.from);
      metric(state, "starts");
      recordReferral(state, user, msg.text);
      writeUx(state);

      if (!user?.device) {
        await showDevicePicker(bot, msg.chat.id, true);
        return;
      }

      return listener(msg, match);
    });
  }

  if (source.includes("\\/stats")) {
    const bot = this;
    return originalOnText.call(this, regexp, async function(msg) {
      if (!isAdmin(msg.from?.id)) return;
      await sendLong(bot, msg.chat.id, enhancedStatsText());
    });
  }

  return originalOnText.call(this, regexp, listener);
};

proto.on = function(event, listener) {
  if (event !== "callback_query") {
    return originalOn.call(this, event, listener);
  }

  installBotExtras(this, originalOnText);

  const bot = this;

  return originalOn.call(this, event, async function(query, ...args) {
    const data = query?.data || "";
    const chatId = query?.message?.chat?.id;
    const from = query?.from;

    if (!chatId || !from?.id) {
      return listener.call(bot, query, ...args);
    }

    const state = readUx();
    const user = getUxUser(state, from);

    try {
      if (data === "ux:device:choose") {
        writeUx(state);
        await safeAnswer(bot, query.id);
        await showDevicePicker(bot, chatId, false);
        return;
      }

      if (data === "ux:device:android" || data === "ux:device:ios") {
        user.device = data.endsWith("ios") ? "ios" : "android";
        user.deviceSelectedAt = Date.now();
        metric(state, "deviceSelections");
        writeUx(state);
        await safeAnswer(bot, query.id, { text: "Device saved." });
        await showPlayerGuide(bot, chatId, user.device);
        return;
      }

      if (data === "ux:device:continue") {
        writeUx(state);
        await safeAnswer(bot, query.id);
        const startListener = startListeners.get(bot);
        if (startListener) {
          await startListener(
            {
              from,
              chat: { id: chatId },
              text: "/start"
            },
            ["/start"]
          );
        }
        return;
      }

      if (data === "ux:gate:verify") {
        const joined = await isChannelMember(bot, from.id, state);
        if (!joined) {
          writeUx(state);
          await safeAnswer(bot, query.id, {
            text: "Membership not detected yet.",
            show_alert: true
          });
          return;
        }

        user.channelVerifiedAt = Date.now();
        writeUx(state);
        await safeAnswer(bot, query.id, { text: "Membership verified." });

        if (SHARE_MODE === "daily" && user.lastShareDate !== todayKey()) {
          await showShareGate(bot, chatId, from.id);
        } else {
          await bot.sendMessage(chatId, "✅ Download access is ready.");
        }
        return;
      }

      if (data === "ux:gate:shared") {
        const joined = await isChannelMember(bot, from.id, state);
        if (!joined) {
          writeUx(state);
          await safeAnswer(bot, query.id, {
            text: "Join @voidupdatezone first.",
            show_alert: true
          });
          await showJoinGate(bot, chatId);
          return;
        }

        user.channelVerifiedAt = Date.now();
        user.lastShareDate = todayKey();
        user.lastSharedAt = Date.now();
        metric(state, "sharesConfirmed");
        writeUx(state);
        await safeAnswer(bot, query.id, { text: "Downloads unlocked for today." });
        await bot.sendMessage(
          chatId,
          "✅ Downloads unlocked for today.\n\nTap your movie or episode download button again."
        );
        return;
      }

      if (data === "home:help") {
        writeUx(state);
        await safeAnswer(bot, query.id);
        await showHelp(bot, chatId, from.id);
        return;
      }

      const progressKinds = [
        "title",
        "seasonpage",
        "allseasons",
        "episode",
        "download"
      ];

      for (const kind of progressKinds) {
        if (data.startsWith(`${kind}:`)) {
          const payload = getCallback(data, kind);
          if (payload) {
            user.lastProgress = {
              kind,
              payload,
              label:
                payload.title ||
                payload.baseTitle ||
                payload.label ||
                "your recent selection",
              updatedAt: Date.now()
            };
          }
          break;
        }
      }

      if (data.startsWith("episode:") || data.startsWith("download:")) {
        if (!user.device) {
          writeUx(state);
          await safeAnswer(bot, query.id, { text: "Choose your device first." });
          await showDevicePicker(bot, chatId, true);
          return;
        }

        if (state.maintenance) {
          writeUx(state);
          await safeAnswer(bot, query.id, {
            text: "Downloads are temporarily under maintenance.",
            show_alert: true
          });
          await bot.sendMessage(
            chatId,
            "🛠 Downloads are temporarily unavailable. Search and browsing still work. Please try again later."
          );
          return;
        }

        const joined = await isChannelMember(bot, from.id, state);
        if (!joined) {
          writeUx(state);
          await safeAnswer(bot, query.id, { text: "Join @voidupdatezone first." });
          await showJoinGate(bot, chatId);
          return;
        }

        user.channelVerifiedAt = Date.now();

        if (SHARE_MODE === "daily" && user.lastShareDate !== todayKey()) {
          writeUx(state);
          await safeAnswer(bot, query.id, { text: "Share the bot once today first." });
          await showShareGate(bot, chatId, from.id);
          return;
        }

        lastAttemptByChat.set(String(chatId), {
          callbackData: data,
          userId: from.id,
          username: from.username || null,
          type: data.startsWith("episode:") ? "episode" : "movie"
        });
      }

      writeUx(state);
      return listener.call(bot, query, ...args);
    } catch (error) {
      console.error("LAUNCH UX ERROR:", error);
      writeUx(state);

      if (data.startsWith("episode:") || data.startsWith("download:") || data.startsWith("ux:")) {
        await safeAnswer(bot, query.id, {
          text: "Something went wrong. Please try again.",
          show_alert: true
        }).catch(() => {});
        return;
      }

      return listener.call(bot, query, ...args);
    }
  });
};

console.log(
  `Launch upgrades enabled: channel=${REQUIRED_CHANNEL}, share=${SHARE_MODE}, timezone=${APP_TIMEZONE}`
);
