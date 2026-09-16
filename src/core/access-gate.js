const fs = require("fs");
const path = require("path");
const TelegramBot = require("node-telegram-bot-api");

const REQUIRED_CHANNEL =
  process.env.REQUIRED_CHANNEL || "@voidupdatezone";

const REQUIRED_CHANNEL_URL =
  process.env.REQUIRED_CHANNEL_URL ||
  "https://t.me/voidupdatezone";

const SHARE_MODE =
  String(process.env.SHARE_MODE || "daily").toLowerCase();

const APP_TIMEZONE =
  process.env.APP_TIMEZONE || "Africa/Lagos";

const DATA_DIR =
  process.env.DATA_DIR ||
  path.join(process.cwd(), "data");

const FILE =
  path.join(DATA_DIR, "access-gate.json");

const usernameCache = new WeakMap();

function readState() {
  fs.mkdirSync(DATA_DIR, { recursive: true });

  if (!fs.existsSync(FILE)) {
    fs.writeFileSync(
      FILE,
      JSON.stringify({ users: {} }, null, 2)
    );
  }

  try {
    const state =
      JSON.parse(fs.readFileSync(FILE, "utf8"));

    state.users ||= {};
    return state;
  } catch {
    return { users: {} };
  }
}

function writeState(state) {
  fs.mkdirSync(DATA_DIR, { recursive: true });

  const temp = `${FILE}.tmp`;

  fs.writeFileSync(
    temp,
    JSON.stringify(state, null, 2)
  );

  fs.renameSync(temp, FILE);
}

function today() {
  const parts =
    new Intl.DateTimeFormat(
      "en-GB",
      {
        timeZone: APP_TIMEZONE,
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
      }
    ).formatToParts(new Date());

  const obj = {};

  for (const part of parts) {
    obj[part.type] = part.value;
  }

  return `${obj.year}-${obj.month}-${obj.day}`;
}

function getUser(state, user) {
  const id = String(user.id);

  state.users[id] ||= {
    id: user.id,
    username: user.username || null,
    firstName: user.first_name || null,
    lastName: user.last_name || null,
    firstSeenAt: Date.now(),
    lastSeenAt: Date.now(),
    lastShareDate: null
  };

  const entry = state.users[id];

  entry.username =
    user.username || entry.username || null;

  entry.firstName =
    user.first_name || entry.firstName || null;

  entry.lastName =
    user.last_name || entry.lastName || null;

  entry.lastSeenAt = Date.now();

  return entry;
}

function isDownload(data) {
  return (
    data.startsWith("download:") ||
    data.startsWith("episode:")
  );
}

async function isMember(bot, userId) {
  const member =
    await bot.getChatMember(
      REQUIRED_CHANNEL,
      userId
    );

  return (
    member.status === "creator" ||
    member.status === "administrator" ||
    member.status === "member" ||
    (
      member.status === "restricted" &&
      member.is_member === true
    )
  );
}

async function answer(
  bot,
  queryId,
  options = {}
) {
  try {
    await bot.answerCallbackQuery(
      queryId,
      options
    );
  } catch (error) {
    const msg = String(error?.message || "");

    if (
      msg.includes("query is too old") ||
      msg.includes("query ID is invalid") ||
      msg.includes("response timeout expired")
    ) {
      return;
    }

    throw error;
  }
}

async function botUsername(bot) {
  if (usernameCache.has(bot)) {
    return usernameCache.get(bot);
  }

  const me = await bot.getMe();
  const username = me.username || "";

  usernameCache.set(bot, username);

  return username;
}

async function shareUrl(bot) {
  let url = "https://t.me/";

  try {
    const username =
      await botUsername(bot);

    if (username) {
      url = `https://t.me/${username}`;
    }
  } catch {}

  return (
    "https://t.me/share/url?" +
    "url=" +
    encodeURIComponent(url) +
    "&text=" +
    encodeURIComponent(
      "Check out TheNkiri Bot for movies, series and K-Drama."
    )
  );
}

async function showJoin(bot, chatId) {
  await bot.sendMessage(
    chatId,
    "🔒 Channel membership required\n\n" +
    "You must join @voidupdatezone before downloading.\n\n" +
    "Join the channel, then tap Verify Membership.",
    {
      reply_markup: {
        inline_keyboard: [
          [
            {
              text: "📢 Join @voidupdatezone",
              url: REQUIRED_CHANNEL_URL
            }
          ],
          [
            {
              text: "✅ Verify Membership",
              callback_data: "gate:verify"
            }
          ]
        ]
      }
    }
  );
}

async function showShare(bot, chatId) {
  const url =
    await shareUrl(bot);

  await bot.sendMessage(
    chatId,
    "📤 Daily share required\n\n" +
    "Share the bot once today to unlock downloads for the rest of today.\n\n" +
    "After sharing, tap I Shared.",
    {
      reply_markup: {
        inline_keyboard: [
          [
            {
              text: "📤 Share Bot",
              url
            }
          ],
          [
            {
              text: "✅ I Shared",
              callback_data: "gate:shared"
            }
          ]
        ]
      }
    }
  );
}

async function checkMembership(
  bot,
  query
) {
  try {
    const ok =
      await isMember(
        bot,
        query.from.id
      );

    if (!ok) {
      await answer(
        bot,
        query.id,
        {
          text: "Join @voidupdatezone first.",
          show_alert: true
        }
      );

      await showJoin(
        bot,
        query.message.chat.id
      );

      return false;
    }

    return true;

  } catch (error) {
    console.error(
      "CHANNEL CHECK ERROR:",
      error.message
    );

    await answer(
      bot,
      query.id,
      {
        text:
          "I couldn't verify your channel membership.",
        show_alert: true
      }
    ).catch(() => {});

    return false;
  }
}

if (!TelegramBot.prototype.__nkiriGate) {
  TelegramBot.prototype.__nkiriGate = true;

  const originalOn =
    TelegramBot.prototype.on;

  TelegramBot.prototype.on =
    function(event, listener) {
      if (event !== "callback_query") {
        return originalOn.call(
          this,
          event,
          listener
        );
      }

      const bot = this;

      return originalOn.call(
        this,
        event,
        async function(query, ...args) {
          const data =
            query?.data || "";

          const chatId =
            query?.message?.chat?.id;

          const user =
            query?.from;

          if (!chatId || !user?.id) {
            return listener.call(
              bot,
              query,
              ...args
            );
          }

          try {
            if (data === "gate:verify") {
              const joined =
                await checkMembership(
                  bot,
                  query
                );

              if (!joined) return;

              await answer(
                bot,
                query.id,
                {
                  text:
                    "Membership verified."
                }
              );

              const state =
                readState();

              const entry =
                getUser(state, user);

              entry.channelVerifiedAt =
                Date.now();

              writeState(state);

              if (
                entry.lastShareDate ===
                today()
              ) {
                await bot.sendMessage(
                  chatId,
                  "✅ Downloads are already unlocked for today."
                );
              } else {
                await showShare(
                  bot,
                  chatId
                );
              }

              return;
            }

            if (data === "gate:shared") {
              const joined =
                await checkMembership(
                  bot,
                  query
                );

              if (!joined) return;

              const state =
                readState();

              const entry =
                getUser(state, user);

              entry.channelVerifiedAt =
                Date.now();

              entry.lastShareDate =
                today();

              entry.lastSharedAt =
                Date.now();

              writeState(state);

              await answer(
                bot,
                query.id,
                {
                  text:
                    "Downloads unlocked for today."
                }
              );

              await bot.sendMessage(
                chatId,
                "✅ Downloads unlocked for today.\n\n" +
                "Tap your movie or episode download button again."
              );

              return;
            }

            if (isDownload(data)) {
              const joined =
                await checkMembership(
                  bot,
                  query
                );

              if (!joined) return;

              const state =
                readState();

              const entry =
                getUser(state, user);

              entry.channelVerifiedAt =
                Date.now();

              writeState(state);

              if (
                SHARE_MODE === "daily" &&
                entry.lastShareDate !== today()
              ) {
                await answer(
                  bot,
                  query.id,
                  {
                    text:
                      "Share the bot once today first."
                  }
                );

                await showShare(
                  bot,
                  chatId
                );

                return;
              }
            }

            return listener.call(
              bot,
              query,
              ...args
            );

          } catch (error) {
            console.error(
              "ACCESS GATE ERROR:",
              error
            );

            if (
              isDownload(data) ||
              data.startsWith("gate:")
            ) {
              await answer(
                bot,
                query.id,
                {
                  text:
                    "Access verification failed. Try again.",
                  show_alert: true
                }
              ).catch(() => {});

              return;
            }

            return listener.call(
              bot,
              query,
              ...args
            );
          }
        }
      );
    };
}

console.log(
  `Access gate enabled: ${REQUIRED_CHANNEL}, share=${SHARE_MODE}`
);
