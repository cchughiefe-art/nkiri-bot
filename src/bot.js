require("dotenv").config();

const TelegramBot =
  require("node-telegram-bot-api");
const http = require("http");

const {
  searchNkiri,
  getLatest
} = require("./nkiri/search");

const {
  GENRES,
  discoverGenre
} = require("./nkiri/discover");

const {
  parseNkiriPage
} = require("./nkiri/parser");

const {
  parseSeriesPage
} = require("./nkiri/series");

const {
  discoverSeasons,
  extractSeason
} = require("./nkiri/seasons");

const {
  resolveDownload
} = require("./resolvers");

const {
  searchFzForBot,
  latestFzForBot,
  isFzUrl,
  fzIdFromUrl,
  getFzBotTitle,
  resolveFzSource
} = require("./core/fzmovies-bot");

const {
  cleanTitle
} = require("./core/media");

const {
  search9jaRocks,
  get9jaTitle
} = require("./providers/9jarocks");

const {
  searchDramaKey
} = require("./providers/dramakey");

const {
  resolveLoadedFiles
} = require("./resolvers/loadedfiles");

const {
  homeText: decoratedHomeText,
  movieCaption,
  seriesCaption,
  downloadMessage,
  downloadKeyboard,
  helpText
} = require("./core/telegram-ui");

const {
  create,
  get
} = require("./core/callbacks");

const {
  consume
} = require("./core/rate-limit");

const {
  incrementStat,
  reportBroken,
  saveReport,
  trackUser,
  incrementUserStat,
  trackDownload,
  getAnalytics,
  getUser,
  grantShareAccess,
  hasShareAccess,
  consumeShareAccess,
  cleanup
} = require("./core/store");

const token =
  process.env.TELEGRAM_BOT_TOKEN;

if (!token) {
  throw new Error(
    "TELEGRAM_BOT_TOKEN missing"
  );
}

const bot =
  new TelegramBot(
    token,
    { polling: true }
  );

/*
 * Telegram callback queries expire quickly.
 * After a restart, Telegram may deliver an old
 * button press. Do not let that crash the bot.
 */
const originalAnswerCallbackQuery =
  bot.answerCallbackQuery.bind(bot);

bot.answerCallbackQuery =
  async (...args) => {
    try {
      return await originalAnswerCallbackQuery(
        ...args
      );
    } catch (error) {
      const message =
        String(
          error?.message ||
          error?.response?.body?.description ||
          ""
        );

      if (
        message.includes(
          "query is too old"
        ) ||
        message.includes(
          "query ID is invalid"
        ) ||
        message.includes(
          "response timeout expired"
        )
      ) {
        console.warn(
          "Ignored expired Telegram callback"
        );

        return false;
      }

      throw error;
    }
  };


/*
 * Telegram messages containing photos/videos/documents have captions,
 * not text. Old callback buttons can also point at messages that can no
 * longer be edited. Never let these normal Telegram 400 responses crash
 * the bot.
 */
const originalEditMessageText =
  bot.editMessageText.bind(bot);

bot.editMessageText =
  async (text, options = {}) => {
    try {
      return await originalEditMessageText(
        text,
        options
      );
    } catch (error) {
      const description =
        String(
          error?.response?.body?.description ||
          error?.message ||
          ""
        );

      if (
        description.includes(
          "there is no text in the message to edit"
        )
      ) {
        /*
         * The callback came from a media message.
         * Telegram requires editMessageCaption instead.
         */
        try {
          return await bot.editMessageCaption(
            text,
            options
          );
        } catch (captionError) {
          const captionDescription =
            String(
              captionError?.response?.body?.description ||
              captionError?.message ||
              ""
            );

          console.warn(
            "Media edit fallback:",
            captionDescription
          );

          /*
           * If even the caption cannot be edited,
           * send a fresh message instead of crashing.
           */
          if (options.chat_id) {
            const {
              chat_id,
              message_id,
              inline_message_id,
              ...sendOptions
            } = options;

            return await bot.sendMessage(
              chat_id,
              text,
              sendOptions
            );
          }

          return false;
        }
      }

      if (
        description.includes(
          "message is not modified"
        ) ||
        description.includes(
          "message to edit not found"
        ) ||
        description.includes(
          "message can't be edited"
        )
      ) {
        console.warn(
          "Ignored harmless Telegram edit error:",
          description
        );

        return false;
      }

      throw error;
    }
  };

const PAGE_SIZE = 6;

/*
 * Used when Search is pressed.
 * Telegram will treat the user's next
 * normal message as a search anyway,
 * but this gives clearer UX.
 */
const awaitingSearch =
  new Set();

const awaitingReport =
  new Map();

const REPORT_CHAT_ID =
  process.env.REPORT_CHAT_ID ||
  null;

const ADMIN_USER_ID =
  String(
    process.env.ADMIN_USER_ID ||
    process.env.REPORT_CHAT_ID ||
    ""
  );


const REQUIRED_CHANNEL =
  process.env.REQUIRED_CHANNEL || "";

const REQUIRED_CHANNEL_URL =
  process.env.REQUIRED_CHANNEL_URL ||
  (
    REQUIRED_CHANNEL.startsWith("@")
      ? `https://t.me/${REQUIRED_CHANNEL.slice(1)}`
      : ""
  );

const SHARE_MODE =
  String(
    process.env.SHARE_MODE || "every"
  ).toLowerCase() === "daily"
    ? "daily"
    : "every";

const APP_TIMEZONE =
  process.env.APP_TIMEZONE ||
  "Africa/Lagos";

let BOT_USERNAME =
  process.env.BOT_USERNAME || "";

bot.getMe()
  .then(me => {
    BOT_USERNAME =
      BOT_USERNAME ||
      me.username ||
      "";

    console.log(
      `Telegram bot: @${BOT_USERNAME}`
    );
  })
  .catch(error => {
    console.error(
      "BOT INFO ERROR:",
      error.message
    );
  });

function currentDateKey() {
  return new Intl.DateTimeFormat(
    "en-CA",
    {
      timeZone:
        APP_TIMEZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit"
    }
  ).format(
    new Date()
  );
}

function shareBotUrl() {
  const botUrl =
    BOT_USERNAME
      ? `https://t.me/${BOT_USERNAME}`
      : "https://t.me/";

  return (
    "https://t.me/share/url?" +
    "url=" +
    encodeURIComponent(botUrl) +
    "&text=" +
    encodeURIComponent(
      "Watch movies, series and K-Drama with TheNkiri Bot."
    )
  );
}

async function channelMember(
  userId
) {
  if (!REQUIRED_CHANNEL) {
    return true;
  }

  try {
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

  } catch (error) {
    console.error(
      "CHANNEL CHECK ERROR:",
      error.message
    );

    return false;
  }
}

async function showJoinGate(
  chatId
) {
  const rows = [];

  if (REQUIRED_CHANNEL_URL) {
    rows.push([
      {
        text:
          "📢 Join Telegram Channel",
        url:
          REQUIRED_CHANNEL_URL
      }
    ]);
  }

  rows.push([
    {
      text:
        "✅ I Joined — Verify",
      callback_data:
        "gate:joined"
    }
  ]);

  await bot.sendMessage(
    chatId,
    "🔒 First download requirement\n\n" +
    "Join our Telegram channel before your first download.",
    {
      reply_markup: {
        inline_keyboard:
          rows
      }
    }
  );
}

async function showShareGate(
  chatId
) {
  const message =
    SHARE_MODE === "daily"
      ? "Share the bot once today before downloading."
      : "Share the bot before this download.";

  await bot.sendMessage(
    chatId,
    "📤 Share required\n\n" +
    message,
    {
      reply_markup: {
        inline_keyboard: [
          [
            {
              text:
                "📤 Share Bot",
              url:
                shareBotUrl()
            }
          ],
          [
            {
              text:
                "✅ I Shared",
              callback_data:
                "gate:shared"
            }
          ]
        ]
      }
    }
  );
}

async function checkDownloadAccess(
  userId,
  chatId
) {
  const user =
    getUser(userId);

  const firstDownload =
    !user ||
    Number(
      user.downloads || 0
    ) === 0;

  if (
    firstDownload &&
    REQUIRED_CHANNEL
  ) {
    const joined =
      await channelMember(
        userId
      );

    if (!joined) {
      await showJoinGate(
        chatId
      );

      return false;
    }
  }

  const dateKey =
    currentDateKey();

  if (
    !hasShareAccess(
      userId,
      dateKey,
      SHARE_MODE
    )
  ) {
    await showShareGate(
      chatId
    );

    return false;
  }

  return true;
}

function homeKeyboard() {
  return {
    inline_keyboard: [
      [
        {
          text: "🔎 Search",
          callback_data:
            "home:search"
        }
      ],
      [
        {
          text: "🎬 Latest Movies",
          callback_data:
            "latest:movie:1"
        },
        {
          text: "📺 Latest Series",
          callback_data:
            "latest:series:1"
        }
      ],
      [
        {
          text: "🇰🇷 K-Drama",
          callback_data:
            "latest:drama:1"
        }
      ],
      [
        {
          text: "🧭 Discover",
          callback_data:
            "discover:menu"
        }
      ],
      [
        {
          text: "✨ What's New",
          callback_data:
            "home:whatsnew"
        }
      ],
      [
        {
          text: "⚠️ Report a Problem",
          callback_data:
            "report:menu"
        }
      ],
      [
        {
          text: "❓ Help",
          callback_data:
            "home:help"
        }
      ]
    ]
  };
}

async function showHome(chatId) {
  await bot.sendMessage(
    chatId,
    decoratedHomeText(),
    {
      reply_markup:
        homeKeyboard()
    }
  );
}

function shortTitle(title) {
  const value =
    cleanTitle(title);

  if (value.length <= 52) {
    return value;
  }

  return (
    value.slice(0, 49) +
    "..."
  );
}

function resultKeyboard(
  items,
  navigation = {}
) {
  const rows = [];

  for (const item of items) {
    const callback =
      create(
        "title",
        {
          url: item.url,
          title:
            item.cleanTitle ||
            item.title,
          type:
            item.type || null,
          provider:
            item.provider || null,
          fzId:
            item.fzId || null
        }
      );

    rows.push([
      {
        text:
          `${item.type === "series" ? "📺" : "🎬"} ` +
          shortTitle(
            item.cleanTitle ||
            item.title
          ),
        callback_data:
          callback
      }
    ]);
  }

  const nav = [];

  if (navigation.previous) {
    nav.push({
      text: "◀ Previous",
      callback_data:
        navigation.previous
    });
  }

  if (navigation.next) {
    nav.push({
      text: "Next ▶",
      callback_data:
        navigation.next
    });
  }

  if (nav.length) {
    rows.push(nav);
  }

  rows.push([
    {
      text: "🏠 Home",
      callback_data: "home:menu"
    }
  ]);

  return {
    inline_keyboard: rows
  };
}

async function renderSearch(
  chatId,
  query,
  page = 1,
  messageId = null
) {
  const result =
    await searchNkiri(
      query,
      {
        page,
        perPage:
          PAGE_SIZE
      }
    );

  try {
    const fz =
      await searchFzForBot(
        query,
        PAGE_SIZE
      );

    if (fz.length) {
      const seen =
        new Set(
          result.results.map(
            item =>
              `${item.provider || "thenkiri"}:${item.url}`
          )
        );

      for (const item of fz) {
        const key =
          `fzmovies:${item.url}`;

        if (!seen.has(key)) {
          seen.add(key);
          result.results.push(item);
        }
      }

      result.total =
        result.results.length;

      result.pages = 1;
      result.page = 1;
      result.hasPrevious = false;
      result.hasNext = false;
    }
  } catch (error) {
    console.error(
      "FZMOVIES BOT SEARCH ERROR:",
      error.message
    );
  }

  /*
   * 9JAROCKS SEARCH
   *
   * Completely isolated from the other providers.
   * If 9jaRocks is unavailable, normal search still works.
   */
  try {
    const jarocks =
      await search9jaRocks(
        query,
        PAGE_SIZE
      );

    if (jarocks.length) {
      const seen =
        new Set(
          result.results.map(
            item =>
              `${item.provider || "thenkiri"}:${item.url}`
          )
        );

      for (const item of jarocks) {
        const key =
          `9jarocks:${item.url}`;

        if (!seen.has(key)) {
          seen.add(key);

          result.results.push({
            ...item,
            provider:
              "9jarocks"
          });
        }
      }

      result.total =
        result.results.length;

      result.pages = 1;
      result.page = 1;
      result.hasPrevious = false;
      result.hasNext = false;
    }

  } catch (error) {
    console.error(
      "9JAROCKS SEARCH ERROR:",
      error.message
    );
  }

  /*
   * DRAMAKEY SEARCH
   *
   * DramaKey title pages use DownloadWella links, so the normal
   * Nkiri title, episode and download handlers can process them.
   */
  try {
    const dramas =
      await searchDramaKey(
        query,
        PAGE_SIZE
      );

    if (dramas.length) {
      const seen =
        new Set(
          result.results.map(
            item =>
              `${item.provider || "thenkiri"}:${item.url}`
          )
        );

      for (const item of dramas) {
        const key =
          `dramakey:${item.url}`;

        if (!seen.has(key)) {
          seen.add(key);
          result.results.push(item);
        }
      }

      result.total =
        result.results.length;

      result.pages = 1;
      result.page = 1;
      result.hasPrevious = false;
      result.hasNext = false;
    }
  } catch (error) {
    console.error(
      "DRAMAKEY SEARCH ERROR:",
      error.message
    );
  }

  /*
   * Final merged search ordering.
   *
   * Alphabetical A-Z with natural
   * numeric sorting:
   * Season 1, Season 2, Season 10.
   */
  result.results.sort(
    (a, b) =>
      String(
        a.cleanTitle ||
        a.title ||
        ""
      ).localeCompare(
        String(
          b.cleanTitle ||
          b.title ||
          ""
        ),
        undefined,
        {
          numeric: true,
          sensitivity: "base"
        }
      )
  );

  result.total =
    result.results.length;

  if (!result.results.length) {
    const text =
      `No results found for "${query}".\n\n` +
      "Try another spelling or a shorter title.";

    const options = {
      reply_markup: {
        inline_keyboard: [
          [
            {
              text: "🔎 Search Again",
              callback_data:
                "home:search"
            }
          ],
          [
            {
              text: "🏠 Home",
              callback_data:
                "home:menu"
            }
          ]
        ]
      }
    };

    if (messageId) {
      await bot.editMessageText(
        text,
        {
          chat_id: chatId,
          message_id: messageId,
          ...options
        }
      );
    } else {
      await bot.sendMessage(
        chatId,
        text,
        options
      );
    }

    return;
  }

  const previous =
    result.hasPrevious
      ? create(
          "searchpage",
          {
            query,
            page:
              result.page - 1
          }
        )
      : null;

  const next =
    result.hasNext
      ? create(
          "searchpage",
          {
            query,
            page:
              result.page + 1
          }
        )
      : null;

  const text =
    `🔎 Results for "${query}"\n\n` +
    `${result.total} result(s)` +
    (
      result.pages > 1
        ? ` • Page ${result.page}/${result.pages}`
        : ""
    );

  const options = {
    reply_markup:
      resultKeyboard(
        result.results,
        {
          previous,
          next
        }
      )
  };

  if (messageId) {
    await bot.editMessageText(
      text,
      {
        chat_id: chatId,
        message_id: messageId,
        ...options
      }
    );
  } else {
    await bot.sendMessage(
      chatId,
      text,
      options
    );
  }
}

async function renderLatest(
  chatId,
  type,
  page = 1,
  messageId = null
) {
  const result =
    await getLatest(
      type,
      {
        page,
        perPage:
          PAGE_SIZE
      }
    );

  const labels = {
    movie:
      "🎬 Latest Movies",
    series:
      "📺 Latest Series",
    drama:
      "🇰🇷 Latest K-Drama"
  };

  const previous =
    result.hasPrevious
      ? `latest:${type}:${result.page - 1}`
      : null;

  const next =
    result.hasNext
      ? `latest:${type}:${result.page + 1}`
      : null;

  const text =
    `${labels[type] || "Latest"}\n\n` +
    (
      result.results.length
        ? `${result.total} title(s) • Page ${result.page}/${result.pages}`
        : "No titles found right now."
    );

  const options = {
    reply_markup:
      resultKeyboard(
        result.results,
        {
          previous,
          next
        }
      )
  };

  if (messageId) {
    await bot.editMessageText(
      text,
      {
        chat_id: chatId,
        message_id: messageId,
        ...options
      }
    );
  } else {
    await bot.sendMessage(
      chatId,
      text,
      options
    );
  }
}

function genreKeyboard() {
  const keys =
    Object.entries(GENRES);

  const rows = [];

  for (
    let i = 0;
    i < keys.length;
    i += 2
  ) {
    const row = [];

    for (
      let j = i;
      j < Math.min(i + 2, keys.length);
      j++
    ) {
      const [key, genre] =
        keys[j];

      row.push({
        text:
          `${genre.emoji} ${genre.name}`,
        callback_data:
          `genre:${key}:1`
      });
    }

    rows.push(row);
  }

  rows.push([
    {
      text: "🏠 Home",
      callback_data:
        "home:menu"
    }
  ]);

  return {
    inline_keyboard: rows
  };
}

async function renderGenre(
  chatId,
  genre,
  page = 1,
  messageId = null
) {
  const result =
    await discoverGenre(
      genre,
      {
        page,
        perPage: PAGE_SIZE
      }
    );

  const previous =
    result.hasPrevious
      ? `genre:${genre}:${result.page - 1}`
      : null;

  const next =
    result.hasNext
      ? `genre:${genre}:${result.page + 1}`
      : null;

  const text =
    `${result.emoji} ${result.name}\n\n` +
    `${result.total} title(s)` +
    (
      result.pages > 1
        ? ` • Page ${result.page}/${result.pages}`
        : ""
    );

  const keyboard =
    resultKeyboard(
      result.results,
      {
        previous,
        next
      }
    );

  /*
   * Add Discover button before Home.
   */
  const rows =
    keyboard.inline_keyboard;

  const home =
    rows.pop();

  rows.push([
    {
      text: "🧭 All Genres",
      callback_data:
        "discover:menu"
    }
  ]);

  rows.push(home);

  const options = {
    reply_markup:
      keyboard
  };

  if (messageId) {
    await bot.editMessageText(
      text,
      {
        chat_id:
          chatId,
        message_id:
          messageId,
        ...options
      }
    );
  } else {
    await bot.sendMessage(
      chatId,
      text,
      options
    );
  }
}

function reportMenuKeyboard() {
  return {
    inline_keyboard: [
      [
        {
          text: "⬇️ Download problem",
          callback_data:
            "report:type:download"
        }
      ],
      [
        {
          text: "🔎 Search problem",
          callback_data:
            "report:type:search"
        }
      ],
      [
        {
          text: "📺 Missing title / episode",
          callback_data:
            "report:type:missing"
        }
      ],
      [
        {
          text: "📝 Wrong information",
          callback_data:
            "report:type:info"
        }
      ],
      [
        {
          text: "🤖 Bot problem",
          callback_data:
            "report:type:bot"
        }
      ],
      [
        {
          text: "💬 Other",
          callback_data:
            "report:type:other"
        }
      ],
      [
        {
          text: "🏠 Home",
          callback_data:
            "home:menu"
        }
      ]
    ]
  };
}

const REPORT_LABELS = {
  download: "Download problem",
  search: "Search problem",
  missing: "Missing title / episode",
  info: "Wrong information",
  bot: "Bot problem",
  other: "Other"
};

async function sendReportToAdmin(
  report,
  user
) {
  if (!REPORT_CHAT_ID) {
    return;
  }

  const username =
    user?.username
      ? `@${user.username}`
      : "No username";

  const text =
    `⚠️ New Problem Report\n\n` +
    `ID: ${report.id}\n` +
    `Type: ${report.category}\n` +
    `From: ${user?.first_name || "User"}\n` +
    `Username: ${username}\n` +
    `User ID: ${user?.id || "unknown"}\n\n` +
    `${report.message}`;

  await bot.sendMessage(
    REPORT_CHAT_ID,
    text
  ).catch(error => {
    console.error(
      "REPORT FORWARD ERROR:",
      error.message
    );
  });
}

bot.onText(
  /\/start(?:\s+.*)?$/,
  async msg => {
    trackUser(
      msg.from
    );

    await showHome(
      msg.chat.id
    );
  }
);

bot.onText(
  /\/version(?:\s+.*)?$/,
  async msg => {
    if (
      String(msg.from?.id) !==
      ADMIN_USER_ID
    ) {
      return;
    }

    const me =
      await bot.getMe();

    await bot.sendMessage(
      msg.chat.id,
      `Build: V2-REPORT-STATS-20260916\n` +
      `Bot: @${me.username}\n` +
      `Bot ID: ${me.id}\n` +
      `Admin ID: ${ADMIN_USER_ID || "not set"}`
    );
  }
);

bot.onText(
  /\/stats(?:\s+.*)?$/,
  async msg => {
    if (
      String(msg.from?.id) !==
      ADMIN_USER_ID
    ) {
      return;
    }

    const a =
      getAnalytics();

    const top =
      a.topDownloads.length
        ? a.topDownloads
            .map(
              (item, index) =>
                `${index + 1}. ${item.title} — ${item.count}`
            )
            .join("\n")
        : "No downloads yet.";

    const recent =
      a.recentDownloads.length
        ? a.recentDownloads
            .map(item => {
              const user =
                item.username
                  ? `@${item.username}`
                  : `ID ${item.userId}`;

              const episode =
                item.episode
                  ? ` • ${item.episode}`
                  : "";

              return (
                `• ${item.title}${episode}\n` +
                `  ${user}`
              );
            })
            .join("\n")
        : "No recent downloads.";

    const users =
      a.recentUsers.length
        ? a.recentUsers
            .map(user => {
              const name =
                [
                  user.firstName,
                  user.lastName
                ]
                  .filter(Boolean)
                  .join(" ") ||
                "No name";

              const username =
                user.username
                  ? `@${user.username}`
                  : "No username";

              return (
                `👤 ${name}\n` +
                `Username: ${username}\n` +
                `Telegram ID: ${user.id}\n` +
                `Language: ${user.language || "unknown"}\n` +
                `Searches: ${user.searches || 0}\n` +
                `Downloads: ${user.downloads || 0}\n` +
                `Reports: ${user.reports || 0}`
              );
            })
            .join("\n\n")
        : "No users yet.";

    const text =
      `📊 TheNkiri Bot Stats\n\n` +
      `👥 Total users: ${a.totalUsers}\n` +
      `🟢 Active 24h: ${a.active24h}\n` +
      `📅 Active 7d: ${a.active7d}\n\n` +
      `🔎 Searches: ${a.searches}\n` +
      `⬇️ Downloads: ${a.totalDownloads}\n` +
      `⚡ Downloads 24h: ${a.downloads24h}\n` +
      `❌ Failed downloads: ${a.failedDownloads}\n` +
      `⚠️ Reports: ${a.reports}\n\n` +
      `🔥 Most Downloaded\n${top}\n\n` +
      `🕘 Recent Downloads\n${recent}\n\n` +
      `👥 Recent Users\n\n${users}`;

    await bot.sendMessage(
      msg.chat.id,
      text
    );
  }
);

bot.onText(
  /\/user(?:\s+(\d+))?$/,
  async (msg, match) => {
    if (
      String(msg.from?.id) !==
      ADMIN_USER_ID
    ) {
      return;
    }

    const id =
      match?.[1];

    if (!id) {
      await bot.sendMessage(
        msg.chat.id,
        "Usage: /user TELEGRAM_ID"
      );

      return;
    }

    const user =
      getUser(id);

    if (!user) {
      await bot.sendMessage(
        msg.chat.id,
        "User not found."
      );

      return;
    }

    const name =
      [
        user.firstName,
        user.lastName
      ]
        .filter(Boolean)
        .join(" ") ||
      "No name";

    const username =
      user.username
        ? `@${user.username}`
        : "No username";

    await bot.sendMessage(
      msg.chat.id,
      `👤 User Details\n\n` +
      `Name: ${name}\n` +
      `Username: ${username}\n` +
      `Telegram ID: ${user.id}\n` +
      `Language: ${user.language || "unknown"}\n\n` +
      `🔎 Searches: ${user.searches || 0}\n` +
      `⬇️ Downloads: ${user.downloads || 0}\n` +
      `⚠️ Reports: ${user.reports || 0}\n\n` +
      `First seen: ${
        user.firstSeenAt
          ? new Date(user.firstSeenAt).toLocaleString()
          : "Unknown"
      }\n` +
      `Last seen: ${
        user.lastSeenAt
          ? new Date(user.lastSeenAt).toLocaleString()
          : "Unknown"
      }`
    );
  }
);

bot.onText(
  /\/report(?:\s+.*)?$/,
  async msg => {
    await bot.sendMessage(
      msg.chat.id,
      "⚠️ Report a Problem\n\nChoose the type of problem:",
      {
        reply_markup:
          reportMenuKeyboard()
      }
    );
  }
);

bot.onText(
  /\/search(?:\s+(.+))?$/,
  async (msg, match) => {
    const chatId =
      msg.chat.id;

    const query =
      match?.[1]?.trim();

    if (!query) {
      awaitingSearch.add(
        chatId
      );

      await bot.sendMessage(
        chatId,
        "🔎 Search TheNkiri\n\nSend me the name of a movie, TV series or K-Drama."
      );

      return;
    }

    const limit =
      consume(
        msg.from.id,
        "search"
      );

    if (!limit.allowed) {
      await bot.sendMessage(
        chatId,
        `Too many searches. Try again in ${limit.retryAfter}s.`
      );

      return;
    }

    try {
      await renderSearch(
        chatId,
        query
      );
    } catch (error) {
      console.error(
        "SEARCH ERROR:",
        error
      );

      await bot.sendMessage(
        chatId,
        "⚠️ Search failed. Please try again.",
        {
          reply_markup: {
            inline_keyboard: [
              [
                {
                  text: "🔎 Search Again",
                  callback_data:
                    "home:search"
                }
              ],
              [
                {
                  text: "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          }
        }
      );
    }
  }
);

/*
 * Plain text = search.
 */
bot.on(
  "message",
  async msg => {
    if (!msg.text) return;

    trackUser(
      msg.from
    );

    const text =
      msg.text.trim();

    if (
      !text ||
      text.startsWith("/")
    ) {
      return;
    }

    const chatId =
      msg.chat.id;

    /*
     * If this user is currently writing a
     * problem report, do not treat their
     * message as a movie search.
     */
    const pendingReport =
      awaitingReport.get(
        chatId
      );

    if (pendingReport) {
      awaitingReport.delete(
        chatId
      );

      incrementUserStat(
        msg.from?.id,
        "reports"
      );

      const report =
        saveReport({
          category:
            pendingReport.label,
          categoryKey:
            pendingReport.type,
          message:
            text.slice(0, 2000),
          userId:
            msg.from?.id || null,
          username:
            msg.from?.username || null,
          firstName:
            msg.from?.first_name || null,
          chatId:
            chatId
        });

      await sendReportToAdmin(
        report,
        msg.from
      );

      await bot.sendMessage(
        chatId,
        `✅ Report received\n\n` +
        `Report ID: ${report.id}\n\n` +
        `Thanks. We'll use this to investigate the problem.`,
        {
          reply_markup: {
            inline_keyboard: [
              [
                {
                  text:
                    "⚠️ Report Another",
                  callback_data:
                    "report:menu"
                }
              ],
              [
                {
                  text:
                    "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          }
        }
      );

      return;
    }

    awaitingSearch.delete(
      chatId
    );

    const limit =
      consume(
        msg.from.id,
        "search"
      );

    if (!limit.allowed) {
      await bot.sendMessage(
        chatId,
        `Too many searches. Try again in ${limit.retryAfter}s.`
      );

      return;
    }

    const status =
      await bot.sendMessage(
        chatId,
        `🔎 Searching TheNkiri for "${text}"…`
      );

    try {
      incrementUserStat(
        msg.from?.id,
        "searches"
      );

      await renderSearch(
        chatId,
        text,
        1,
        status.message_id
      );
    } catch (error) {
      console.error(
        "SEARCH ERROR:",
        error
      );

      await bot.editMessageText(
        "⚠️ Search failed. Please try again.",
        {
          chat_id:
            chatId,
          message_id:
            status.message_id,
          reply_markup: {
            inline_keyboard: [
              [
                {
                  text:
                    "🔎 Search Again",
                  callback_data:
                    "home:search"
                }
              ],
              [
                {
                  text:
                    "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          }
        }
      ).catch(() => {});
    }
  }
);

bot.on(
  "callback_query",
  async query => {
    const data =
      query.data || "";

    trackUser(
      query.from
    );

    const chatId =
      query.message.chat.id;

    /*
     * DOWNLOAD ACCESS
     */
    if (
      data === "gate:joined"
    ) {
      const joined =
        await channelMember(
          query.from.id
        );

      if (!joined) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "Membership not detected yet.",
            show_alert: true
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Membership confirmed."
        }
      );

      await showShareGate(
        chatId
      );

      return;
    }

    if (
      data === "gate:shared"
    ) {
      const user =
        getUser(
          query.from.id
        );

      const firstDownload =
        !user ||
        Number(
          user.downloads || 0
        ) === 0;

      if (
        firstDownload &&
        REQUIRED_CHANNEL
      ) {
        const joined =
          await channelMember(
            query.from.id
          );

        if (!joined) {
          await bot.answerCallbackQuery(
            query.id,
            {
              text:
                "Join the channel first.",
              show_alert: true
            }
          );

          return;
        }
      }

      grantShareAccess(
        query.from.id,
        currentDateKey()
      );

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Download unlocked."
        }
      );

      await bot.sendMessage(
        chatId,
        "✅ Download unlocked.\n\n" +
        "Tap the movie or episode download button again."
      );

      return;
    }

    /*
     * REPORT A PROBLEM
     */
    if (
      data === "report:menu"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      awaitingReport.delete(
        chatId
      );

      await bot.sendMessage(
        chatId,
        "⚠️ Report a Problem\n\nChoose the type of problem:",
        {
          reply_markup:
            reportMenuKeyboard()
        }
      );

      return;
    }

    if (
      data.startsWith(
        "report:type:"
      )
    ) {
      const type =
        data.split(":")[2];

      const label =
        REPORT_LABELS[type];

      if (!label) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "Unknown report type."
          }
        );

        return;
      }

      awaitingReport.set(
        chatId,
        {
          type,
          label,
          createdAt:
            Date.now()
        }
      );

      await bot.answerCallbackQuery(
        query.id
      );

      await bot.sendMessage(
        chatId,
        `⚠️ ${label}\n\n` +
        "Describe the problem in one message.\n\n" +
        "Include the movie/series name, season or episode if relevant."
      );

      return;
    }

    /*
     * DISCOVER
     */
    if (
      data === "discover:menu"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      await bot.editMessageText(
        "🧭 Discover\n\n" +
        "Choose a genre:",
        {
          chat_id:
            chatId,
          message_id:
            query.message.message_id,
          reply_markup:
            genreKeyboard()
        }
      );

      return;
    }

    if (
      data.startsWith("genre:")
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      const parts =
        data.split(":");

      const genre =
        parts[1];

      const page =
        Number(parts[2]) || 1;

      if (!GENRES[genre]) {
        await bot.sendMessage(
          chatId,
          "⚠️ Genre not found."
        );

        return;
      }

      try {
        await renderGenre(
          chatId,
          genre,
          page,
          query.message.message_id
        );
      } catch (error) {
        console.error(
          "GENRE ERROR:",
          error
        );

        await bot.sendMessage(
          chatId,
          "⚠️ I couldn't load this genre.",
          {
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "🧭 Genres",
                    callback_data:
                      "discover:menu"
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    /*
     * HOME
     */
    if (
      data === "home:menu"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      await bot.editMessageText(
        decoratedHomeText(),
        {
          chat_id:
            chatId,
          message_id:
            query.message.message_id,
          reply_markup:
            homeKeyboard()
        }
      );

      return;
    }

    if (
      data === "home:whatsnew"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      await bot.editMessageText(
        "✨ What's New in TheNkiri\n\n" +
        "✅ More movie and series sources\n" +
        "✅ Better backup download servers\n" +
        "✅ Improved movie and episode detection\n" +
        "✅ Better download link generation\n" +
        "✅ Improved resumable downloads\n" +
        "✅ Better fallback when one server fails\n" +
        "✅ More improvements are coming\n\n" +
        "Updated: 17 September 2026",
        {
          chat_id:
            chatId,
          message_id:
            query.message.message_id,
          reply_markup: {
            inline_keyboard: [
              [
                {
                  text: "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          }
        }
      );

      return;
    }

    if (
      data === "home:help"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      await bot.editMessageText(
        helpText(),
        {
          chat_id:
            chatId,
          message_id:
            query.message.message_id,
          reply_markup: {
            inline_keyboard: [
              [
                {
                  text: "🔎 Search",
                  callback_data:
                    "home:search"
                }
              ],
              [
                {
                  text: "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          }
        }
      );

      return;
    }

    if (
      data === "home:search"
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      awaitingSearch.add(
        chatId
      );

      await bot.sendMessage(
        chatId,
        "🔎 Search TheNkiri\n\nSend me the name of a movie, TV series or K-Drama."
      );

      return;
    }

    /*
     * LATEST PAGINATION
     */
    if (
      data.startsWith(
        "latest:"
      )
    ) {
      await bot.answerCallbackQuery(
        query.id
      );

      const parts =
        data.split(":");

      const type =
        parts[1];

      const page =
        Number(parts[2]) || 1;

      try {
        await renderLatest(
          chatId,
          type,
          page,
          query.message.message_id
        );
      } catch (error) {
        console.error(
          "LATEST ERROR:",
          error
        );

        await bot.sendMessage(
          chatId,
          "⚠️ I couldn't load the latest titles.",
          {
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text: "🔄 Try Again",
                    callback_data:
                      `latest:${type}:${page}`
                  }
                ],
                [
                  {
                    text: "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    /*
     * SEARCH PAGINATION
     */
    if (
      data.startsWith(
        "searchpage:"
      )
    ) {
      const payload =
        get(
          data,
          "searchpage"
        );

      if (!payload) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This page expired. Search again."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      try {
        await renderSearch(
          chatId,
          payload.query,
          payload.page,
          query.message.message_id
        );
      } catch (error) {
        console.error(
          "PAGE ERROR:",
          error
        );
      }

      return;
    }

    /*
     * TITLE SELECTED
     */
    if (
      data.startsWith(
        "title:"
      )
    ) {
      const item =
        get(
          data,
          "title"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This selection expired. Search again."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Loading title information..."
        );

      try {
        /*
         * 9JAROCKS TITLE
         */
        if (
          item.provider === "9jarocks"
        ) {
          const jr =
            await get9jaTitle(
              item.url
            );

          await bot.deleteMessage(
            chatId,
            status.message_id
          ).catch(() => {});

          /*
           * 9JAROCKS SERIES
           */
          if (
            jr.type === "series" &&
            Array.isArray(jr.episodes) &&
            jr.episodes.length
          ) {

            /*
             * Bundled pages such as:
             *
             * Season 7 – 8
             *
             * should show seasons first.
             */
            if (
              Array.isArray(
                jr.seasonGroups
              ) &&
              jr.seasonGroups.length > 1
            ) {
              const buttons =
                jr.seasonGroups.map(
                  group => [
                    {
                      text:
                        `📺 Season ${group.season} — ${group.count} Episodes`,

                      callback_data:
                        create(
                          "9jseason",
                          {
                            title:
                              jr.cleanTitle ||
                              jr.title,

                            season:
                              group.season,

                            episodes:
                              group.episodes
                          }
                        )
                    }
                  ]
                );

              buttons.push([
                {
                  text:
                    "🔎 Search Again",
                  callback_data:
                    "home:search"
                },
                {
                  text:
                    "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]);

              const baseTitle =
                String(
                  jr.cleanTitle ||
                  jr.title
                )
                  .replace(
                    /\s+season\s+\d+\s*[-–]\s*\d+.*$/i,
                    ""
                  )
                  .trim();

              const caption =
                `📺 ${baseTitle}\n\n` +
                `${jr.seasonGroups.length} seasons found.\n` +
                "Choose a season:";

              if (jr.image) {
                await bot.sendPhoto(
                  chatId,
                  jr.image,
                  {
                    caption,
                    reply_markup: {
                      inline_keyboard:
                        buttons
                    }
                  }
                );
              } else {
                await bot.sendMessage(
                  chatId,
                  caption,
                  {
                    reply_markup: {
                      inline_keyboard:
                        buttons
                    }
                  }
                );
              }

              return;
            }

            /*
             * Normal single-season page.
             */
            const buttons = [];

            for (
              const episode
              of jr.episodes
            ) {
              buttons.push([
                {
                  text:
                    `▶️ Episode ${episode.episode}`,

                  callback_data:
                    create(
                      "9jepisode",
                      {
                        title:
                          jr.cleanTitle ||
                          jr.title,

                        label:
                          `Episode ${episode.episode}`,

                        episode:
                          episode.episode,

                        season:
                          episode.season ||
                          jr.season ||
                          null,

                        sources:
                          episode.sources || []
                      }
                    )
                }
              ]);
            }

            buttons.push([
              {
                text:
                  "🔎 Search Again",
                callback_data:
                  "home:search"
              },
              {
                text:
                  "🏠 Home",
                callback_data:
                  "home:menu"
              }
            ]);

            const caption =
              `📺 ${jr.cleanTitle || jr.title}\n\n` +
              `${jr.episodes.length} episode(s) found.\n` +
              "Choose an episode:";

            if (jr.image) {
              await bot.sendPhoto(
                chatId,
                jr.image,
                {
                  caption,
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            } else {
              await bot.sendMessage(
                chatId,
                caption,
                {
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            }

            return;
          }

          /*
           * 9JAROCKS MOVIE
           */
          const downloadCallback =
            create(
              "9jdownload",
              {
                title:
                  jr.cleanTitle ||
                  jr.title,
                sources:
                  jr.downloadLinks || []
              }
            );

          const caption =
            `🎬 ${jr.cleanTitle || jr.title}\n\n` +
            (
              jr.description
                ? `${jr.description}\n\n`
                : ""
            ) +
            "Tap below to generate a fresh download link.";

          const keyboard = {
            inline_keyboard: [
              [
                {
                  text:
                    "⬇️ Download Movie",
                  callback_data:
                    downloadCallback
                }
              ],
              [
                {
                  text:
                    "🔎 Search Again",
                  callback_data:
                    "home:search"
                },
                {
                  text:
                    "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          };

          if (jr.image) {
            await bot.sendPhoto(
              chatId,
              jr.image,
              {
                caption,
                reply_markup:
                  keyboard
              }
            );
          } else {
            await bot.sendMessage(
              chatId,
              caption,
              {
                reply_markup:
                  keyboard
              }
            );
          }

          return;
        }

        if (
          item.provider === "fzmovies" ||
          isFzUrl(item.url)
        ) {
          const fzId =
            item.fzId ||
            fzIdFromUrl(
              item.url
            );

          const fz =
            await getFzBotTitle(
              fzId
            );

          await bot.deleteMessage(
            chatId,
            status.message_id
          ).catch(() => {});

          /*
           * FZMOVIES SERIES
           */
          if (
            fz.type === "series" &&
            Array.isArray(fz.episodes) &&
            fz.episodes.length
          ) {
            const buttons = [];

            for (
              const episode
              of fz.episodes
            ) {
              buttons.push([
                {
                  text:
                    `▶️ ${episode.label}`,
                  callback_data:
                    create(
                      "fzepisode",
                      {
                        title:
                          fz.cleanTitle ||
                          fz.title,
                        label:
                          episode.label,
                        sources:
                          episode.sources || []
                      }
                    )
                }
              ]);
            }

            buttons.push([
              {
                text:
                  "🔎 Search Again",
                callback_data:
                  "home:search"
              },
              {
                text:
                  "🏠 Home",
                callback_data:
                  "home:menu"
              }
            ]);

            const caption =
              `📺 ${fz.cleanTitle || fz.title}\n\n` +
              `${fz.episodes.length} episode(s) found.\n` +
              "Choose an episode:";

            if (fz.image) {
              await bot.sendPhoto(
                chatId,
                fz.image,
                {
                  caption,
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            } else {
              await bot.sendMessage(
                chatId,
                caption,
                {
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            }

            return;
          }

          /*
           * FZMOVIES MOVIE
           */
          const downloadCallback =
            create(
              "fzdownload",
              {
                title:
                  fz.cleanTitle ||
                  fz.title,
                sources:
                  fz.downloadLinks || []
              }
            );

          const caption =
            `🎬 ${fz.cleanTitle || fz.title}\n\n` +
            (
              fz.description
                ? `${fz.description}\n\n`
                : ""
            ) +
            "Tap below to generate a fresh download link.";

          const keyboard = {
            inline_keyboard: [
              [
                {
                  text:
                    "⬇️ Download Movie",
                  callback_data:
                    downloadCallback
                }
              ],
              [
                {
                  text:
                    "🔎 Search Again",
                  callback_data:
                    "home:search"
                },
                {
                  text:
                    "🏠 Home",
                  callback_data:
                    "home:menu"
                }
              ]
            ]
          };

          if (fz.image) {
            await bot.sendPhoto(
              chatId,
              fz.image,
              {
                caption,
                reply_markup:
                  keyboard
              }
            );
          } else {
            await bot.sendMessage(
              chatId,
              caption,
              {
                reply_markup:
                  keyboard
              }
            );
          }

          return;
        }

        const series =
          await parseSeriesPage(
            item.url
          );

        /*
         * SERIES
         */
        if (
          series.isSeries &&
          series.episodes.length > 1
        ) {
          /*
           * Discover other season pages for
           * the same series before showing
           * individual episodes.
           */
          let discovered = {
            baseTitle:
              cleanTitle(series.title),
            seasons: []
          };

          try {
            discovered =
              await discoverSeasons(
                series.title
              );
          } catch (error) {
            console.error(
              "SEASON DISCOVERY ERROR:",
              error.message
            );
          }

          const selectedSeason =
            extractSeason(
              series.title
            );

          /*
           * Make sure the currently selected
           * page is present even if search
           * didn't return it.
           */
          const seasons =
            [...discovered.seasons];

          if (
            selectedSeason &&
            !seasons.some(
              item =>
                item.season ===
                selectedSeason
            )
          ) {
            seasons.push({
              season:
                selectedSeason,
              title:
                cleanTitle(
                  series.title
                ),
              url:
                item.url,
              image:
                series.poster ||
                null
            });

            seasons.sort(
              (a, b) =>
                a.season -
                b.season
            );
          }

          /*
           * More than one season exists:
           * show season navigation first.
           */
          if (
            seasons.length > 1
          ) {
            const buttons =
              seasons.map(
                season => [
                  {
                    text:
                      `📺 Season ${season.season}`,
                    callback_data:
                      create(
                        "seasonpage",
                        {
                          baseTitle:
                            discovered.baseTitle,
                          season:
                            season.season,
                          title:
                            season.title,
                          url:
                            season.url
                        }
                      )
                  }
                ]
              );

            buttons.push([
              {
                text:
                  "🔎 Search Again",
                callback_data:
                  "home:search"
              },
              {
                text:
                  "🏠 Home",
                callback_data:
                  "home:menu"
              }
            ]);

            await bot.deleteMessage(
              chatId,
              status.message_id
            ).catch(() => {});

            const caption =
              `📺 ${discovered.baseTitle}\n\n` +
              `${seasons.length} season(s) found.\n` +
              "Choose a season:";

            if (series.poster) {
              await bot.sendPhoto(
                chatId,
                series.poster,
                {
                  caption,
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            } else {
              await bot.sendMessage(
                chatId,
                caption,
                {
                  reply_markup: {
                    inline_keyboard:
                      buttons
                  }
                }
              );
            }

            return;
          }

          /*
           * Only one season was found.
           * Go directly to its episodes.
           */
          const buttons = [];

          for (
            const episode
            of series.episodes
          ) {
            buttons.push([
              {
                text:
                  `▶️ ${episode.label}`,
                callback_data:
                  create(
                    "episode",
                    {
                      title:
                        cleanTitle(
                          series.title
                        ),
                      label:
                        episode.label,
                      downloadUrl:
                        episode.downloadUrl,
                      referer:
                        item.url,
                      direct:
                        episode.direct === true,
                      sourceType:
                        episode.sourceType || null
                    }
                  )
              }
            ]);
          }

          buttons.push([
            {
              text:
                "🔎 Search Again",
              callback_data:
                "home:search"
            },
            {
              text:
                "🏠 Home",
              callback_data:
                "home:menu"
            }
          ]);

          await bot.deleteMessage(
            chatId,
            status.message_id
          ).catch(() => {});

          const caption =
            `📺 ${cleanTitle(series.title)}\n\n` +
            `${series.episodes.length} episode(s) found.\n` +
            "Choose an episode:";

          if (series.poster) {
            await bot.sendPhoto(
              chatId,
              series.poster,
              {
                caption,
                reply_markup: {
                  inline_keyboard:
                    buttons
                }
              }
            );
          } else {
            await bot.sendMessage(
              chatId,
              caption,
              {
                reply_markup: {
                  inline_keyboard:
                    buttons
                }
              }
            );
          }

          return;
        }

        /*
         * MOVIE
         */
        const movie =
          await parseNkiriPage(
            item.url
          );

        const movieTitle =
          cleanTitle(
            movie.title
          );

        const downloadCallback =
          create(
            "download",
            {
              url:
                item.url,
              title:
                movieTitle
            }
          );

        const caption =
          `🎬 ${movieTitle}\n` +
          (
            movie.size
              ? `📦 ${movie.size}\n`
              : ""
          ) +
          "\nTap below to generate a fresh download link.";

        const keyboard = {
          inline_keyboard: [
            [
              {
                text:
                  "⬇️ Download Movie",
                callback_data:
                  downloadCallback
              }
            ],
            [
              {
                text:
                  "🔎 Search Again",
                callback_data:
                  "home:search"
              },
              {
                text:
                  "🏠 Home",
                callback_data:
                  "home:menu"
              }
            ]
          ]
        };

        await bot.deleteMessage(
          chatId,
          status.message_id
        ).catch(() => {});

        if (movie.poster) {
          await bot.sendPhoto(
            chatId,
            movie.poster,
            {
              caption,
              reply_markup:
                keyboard
            }
          );
        } else {
          await bot.sendMessage(
            chatId,
            caption,
            {
              reply_markup:
                keyboard
            }
          );
        }

      } catch (error) {
        console.error(
          "TITLE ERROR:",
          error
        );

        await bot.editMessageText(
          "I couldn't load this title.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }

    /*
     * CROSS-PAGE SEASON SELECTED
     */
    if (
      data.startsWith(
        "seasonpage:"
      )
    ) {
      const item =
        get(
          data,
          "seasonpage"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This season selection expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      const status =
        await bot.sendMessage(
          chatId,
          `Loading Season ${item.season}...`
        );

      try {
        const season =
          await parseSeriesPage(
            item.url
          );

        if (
          !season.episodes.length
        ) {
          throw new Error(
            "No episodes found"
          );
        }

        const buttons = [];

        for (
          const episode
          of season.episodes
        ) {
          buttons.push([
            {
              text:
                `▶️ ${episode.label}`,
              callback_data:
                create(
                  "episode",
                  {
                    title:
                      item.baseTitle,
                    label:
                      episode.label,
                    downloadUrl:
                      episode.downloadUrl,
                    referer:
                      item.url,
                    direct:
                      episode.direct === true,
                    sourceType:
                      episode.sourceType || null
                  }
                )
            }
          ]);
        }

        /*
         * Rediscover seasons so the user can
         * move between them after opening one.
         */
        let allSeasons = [];

        try {
          const discovery =
            await discoverSeasons(
              item.title
            );

          allSeasons =
            discovery.seasons;
        } catch (error) {
          console.error(
            "SEASON NAV ERROR:",
            error.message
          );
        }

        if (
          allSeasons.length > 1
        ) {
          buttons.push([
            {
              text:
                "📚 All Seasons",
              callback_data:
                create(
                  "allseasons",
                  {
                    baseTitle:
                      item.baseTitle,
                    seasons:
                      allSeasons
                  }
                )
            }
          ]);
        }

        buttons.push([
          {
            text:
              "🏠 Home",
            callback_data:
              "home:menu"
          }
        ]);

        await bot.editMessageText(
          `📺 ${item.baseTitle}\n` +
          `Season ${item.season}\n\n` +
          `${season.episodes.length} episode(s)\n` +
          "Choose an episode:",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard:
                buttons
            }
          }
        );

      } catch (error) {
        console.error(
          "SEASON PAGE ERROR:",
          error
        );

        await bot.editMessageText(
          `❌ Season ${item.season} could not be loaded.`,
          {
            chat_id:
              chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }

    /*
     * ALL SEASONS
     */
    if (
      data.startsWith(
        "allseasons:"
      )
    ) {
      const item =
        get(
          data,
          "allseasons"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This season list expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      const buttons =
        item.seasons.map(
          season => [
            {
              text:
                `📺 Season ${season.season}`,
              callback_data:
                create(
                  "seasonpage",
                  {
                    baseTitle:
                      item.baseTitle,
                    season:
                      season.season,
                    title:
                      season.title,
                    url:
                      season.url
                  }
                )
            }
          ]
        );

      buttons.push([
        {
          text:
            "🏠 Home",
          callback_data:
            "home:menu"
        }
      ]);

      await bot.sendMessage(
        chatId,
        `📺 ${item.baseTitle}\n\nChoose a season:`,
        {
          reply_markup: {
            inline_keyboard:
              buttons
          }
        }
      );

      return;
    }

    /*
     * SEASON SELECTED
     */
    if (
      data.startsWith(
        "season:"
      )
    ) {
      const item =
        get(
          data,
          "season"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This season selection expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      const buttons =
        item.episodes.map(
          episode => [
            {
              text:
                `▶️ ${episode.label}`,
              callback_data:
                create(
                  "episode",
                  {
                    title:
                      item.title,
                    label:
                      episode.label,
                    downloadUrl:
                      episode.downloadUrl,
                    direct:
                      episode.direct === true,
                    sourceType:
                      episode.sourceType || null
                  }
                )
            }
          ]
        );

      buttons.push([
        {
          text:
            "🏠 Home",
          callback_data:
            "home:menu"
        }
      ]);

      await bot.sendMessage(
        chatId,
        `📺 ${item.title}\n` +
        `Season ${item.season}\n\n` +
        "Choose an episode:",
        {
          reply_markup: {
            inline_keyboard:
              buttons
          }
        }
      );

      return;
    }

    /*
     * 9JAROCKS SEASON SELECTED
     */
    if (
      data.startsWith(
        "9jseason:"
      )
    ) {
      const item =
        get(
          data,
          "9jseason"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This season selection expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id
      );

      const episodes =
        Array.isArray(
          item.episodes
        )
          ? item.episodes
          : [];

      const buttons = [];

      for (
        const episode
        of episodes
      ) {
        buttons.push([
          {
            text:
              `▶️ Episode ${episode.episode}`,

            callback_data:
              create(
                "9jepisode",
                {
                  title:
                    item.title,

                  label:
                    `Season ${item.season} Episode ${episode.episode}`,

                  season:
                    item.season,

                  episode:
                    episode.episode,

                  sources:
                    episode.sources || []
                }
              )
          }
        ]);
      }

      buttons.push([
        {
          text:
            "🔎 Search Again",
          callback_data:
            "home:search"
        },
        {
          text:
            "🏠 Home",
          callback_data:
            "home:menu"
        }
      ]);

      await bot.sendMessage(
        chatId,
        `📺 ${item.title}\n` +
        `Season ${item.season}\n\n` +
        `${episodes.length} episode(s) found.\n` +
        "Choose an episode:",
        {
          reply_markup: {
            inline_keyboard:
              buttons
          }
        }
      );

      return;
    }

    /*
     * 9JAROCKS EPISODE DOWNLOAD
     */
    if (
      data.startsWith(
        "9jepisode:"
      )
    ) {
      const item =
        get(
          data,
          "9jepisode"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This episode selection expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh 9jaRocks episode link..."
        );

      try {
        let resolved = null;

        for (
          const source
          of item.sources || []
        ) {
          try {
            if (
              source?.url &&
              /loadedfiles\.net/i.test(
                source.url
              )
            ) {
              resolved =
                await resolveLoadedFiles(
                  source.url
                );
            }

            if (
              resolved?.directUrl
            ) {
              break;
            }

          } catch (error) {
            console.error(
              "9JAROCKS MIRROR ERROR:",
              error.message
            );
          }
        }

        /*
         * LoadedFiles occasionally changes its timer page.
         * Keep the episode usable by opening the original authorized
         * download page when automatic resolution is unavailable.
         */
        if (!resolved?.directUrl) {
          const fallback =
            (item.sources || [])
              .find(source => source?.url);

          if (fallback) {
            resolved = {
              directUrl:
                fallback.url,
              external: true
            };
          }
        }

        if (!resolved?.directUrl) {
          throw new Error(
            "No working episode source"
          );
        }

        incrementStat(
          "downloads"
        );

        incrementUserStat(
          query.from.id,
          "downloads"
        );

        trackDownload({
          userId:
            query.from.id,
          username:
            query.from.username || null,
          type:
            "episode",
          title:
            item.title,
          episode:
            item.label,
          provider:
            "9jarocks"
        });

        await bot.editMessageText(
          `📺 ${item.title}\n` +
          `${item.label}\n\n` +
          "Your download link is ready.\n" +
          "Start it now because temporary links can expire.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "⬇️ Download Episode",
                    url:
                      resolved.directUrl
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );

      } catch (error) {
        console.error(
          "9JAROCKS EPISODE ERROR:",
          error
        );

        incrementStat(
          "failedDownloads"
        );

        await bot.editMessageText(
          "❌ This 9jaRocks episode is currently unavailable.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "🔎 Search Again",
                    callback_data:
                      "home:search"
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    /*
     * 9JAROCKS MOVIE DOWNLOAD
     */
    if (
      data.startsWith(
        "9jdownload:"
      )
    ) {
      const item =
        get(
          data,
          "9jdownload"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This download request expired."
          }
        );

        return;
      }

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh 9jaRocks download link..."
        );

      try {
        let resolved = null;

        for (
          const source
          of item.sources || []
        ) {
          try {
            if (
              source?.url &&
              /loadedfiles\.net/i.test(
                source.url
              )
            ) {
              resolved =
                await resolveLoadedFiles(
                  source.url
                );
            }

            if (
              resolved?.directUrl
            ) {
              break;
            }

          } catch (error) {
            console.error(
              "9JAROCKS MIRROR ERROR:",
              error.message
            );
          }
        }

        if (!resolved?.directUrl) {
          const fallback =
            (item.sources || [])
              .find(source => source?.url);

          if (fallback) {
            resolved = {
              directUrl:
                fallback.url,
              external: true
            };
          }
        }

        if (!resolved?.directUrl) {
          throw new Error(
            "No working movie source"
          );
        }

        incrementStat(
          "downloads"
        );

        incrementUserStat(
          query.from.id,
          "downloads"
        );

        trackDownload({
          userId:
            query.from.id,
          username:
            query.from.username || null,
          type:
            "movie",
          title:
            item.title,
          provider:
            "9jarocks"
        });

        await bot.editMessageText(
          `🎬 ${item.title}\n\n` +
          "Your download link is ready.\n" +
          "Start it now because temporary links can expire.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "⬇️ Download Movie",
                    url:
                      resolved.directUrl
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );

      } catch (error) {
        console.error(
          "9JAROCKS MOVIE ERROR:",
          error
        );

        incrementStat(
          "failedDownloads"
        );

        await bot.editMessageText(
          "❌ This 9jaRocks movie is currently unavailable.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "🔎 Search Again",
                    callback_data:
                      "home:search"
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    /*
     * FZMOVIES EPISODE DOWNLOAD
     */
    if (
      data.startsWith(
        "fzepisode:"
      )
    ) {
      const item =
        get(
          data,
          "fzepisode"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This episode selection expired."
          }
        );
        return;
      }

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh FZMovies episode link..."
        );

      try {
        let resolved = null;

        for (
          const source
          of item.sources || []
        ) {
          try {
            resolved =
              await resolveFzSource(
                source
              );

            if (
              resolved?.directUrl
            ) {
              break;
            }
          } catch (error) {
            console.error(
              "FZMOVIES MIRROR ERROR:",
              error.message
            );
          }
        }

        if (!resolved?.directUrl) {
          throw new Error(
            "No working episode source"
          );
        }

        await bot.editMessageText(
          `📺 ${item.title}\n` +
          `${item.label}\n\n` +
          "Your download link is ready.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "⬇️ Download Episode",
                    url:
                      resolved.directUrl
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );

      } catch (error) {
        console.error(
          "FZMOVIES EPISODE ERROR:",
          error
        );

        await bot.editMessageText(
          "❌ No working FZMovies episode mirror is available right now.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }

    /*
     * FZMOVIES MOVIE DOWNLOAD
     */
    if (
      data.startsWith(
        "fzdownload:"
      )
    ) {
      const item =
        get(
          data,
          "fzdownload"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This download request expired."
          }
        );
        return;
      }

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh FZMovies download link..."
        );

      try {
        let resolved = null;

        for (
          const source
          of item.sources || []
        ) {
          try {
            resolved =
              await resolveFzSource(
                source
              );

            if (
              resolved?.directUrl
            ) {
              break;
            }
          } catch (error) {
            console.error(
              "FZMOVIES MIRROR ERROR:",
              error.message
            );
          }
        }

        if (!resolved?.directUrl) {
          throw new Error(
            "No working movie source"
          );
        }

        await bot.editMessageText(
          `🎬 ${item.title}\n\n` +
          "Your download link is ready.\n" +
          "Start it now because temporary links can expire.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "⬇️ Download Movie",
                    url:
                      resolved.directUrl
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );

      } catch (error) {
        console.error(
          "FZMOVIES MOVIE ERROR:",
          error
        );

        await bot.editMessageText(
          "❌ No working FZMovies download mirror is available right now.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }

    /*
     * EPISODE DOWNLOAD
     */
    if (
      data.startsWith(
        "episode:"
      )
    ) {
      const item =
        get(
          data,
          "episode"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This episode selection expired."
          }
        );

        return;
      }

      const limit =
        consume(
          query.from.id,
          "download"
        );

      if (!limit.allowed) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              `Try again in ${limit.retryAfter}s.`
          }
        );

        return;
      }

      const accessGranted =
        await checkDownloadAccess(
          query.from.id,
          chatId
        );

      if (!accessGranted) {
        await bot.answerCallbackQuery(
          query.id
        ).catch(() => {});

        return;
      }

      consumeShareAccess(
        query.from.id,
        SHARE_MODE
      );

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh episode download link..."
        );

      try {
        let resolved;

        try {
          resolved =
            await resolveDownload(
              item.downloadUrl,
              {
                referer:
                  item.referer ||
                  undefined,
                timeoutMs: 25000
              }
            );
        } catch (resolverError) {
          /*
           * Never leave DramaKey/DownloadWella users staring at a
           * permanent loading message. If automatic generation is
           * unavailable, expose the original authorized download page.
           */
          if (
            /downloadwella\.com/i.test(
              String(item.downloadUrl || "")
            )
          ) {
            console.warn(
              "DownloadWella automatic resolver fallback:",
              resolverError.message
            );

            resolved = {
              directUrl:
                item.downloadUrl,
              external: true,
              sourceType:
                "downloadwella-page"
            };
          } else {
            throw resolverError;
          }
        }

        incrementStat(
          "downloads"
        );

        incrementUserStat(
          query.from.id,
          "downloads"
        );

        trackDownload({
          userId:
            query.from.id,
          username:
            query.from.username || null,
          type:
            "episode",
          title:
            item.title,
          episode:
            item.label,
          provider:
            resolved.sourceType || null
        });

        await bot.editMessageText(
          `📺 ${item.title}\n` +
          `${item.label}\n\n` +
          "Your download link is ready.\n" +
          "Start it now because temporary links can expire.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "⬇️ Download Episode",
                    url:
                      resolved.directUrl
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );

      } catch (error) {
        console.error(
          "EPISODE ERROR:",
          error
        );

        incrementStat(
          "failedDownloads"
        );

        reportBroken(
          item.downloadUrl,
          {
            title:
              item.title,
            episode:
              item.label,
            error:
              error.message
          }
        );

        await bot.editMessageText(
          "❌ This episode's download link is currently unavailable.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "🔎 Search Another Version",
                    callback_data:
                      "home:search"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    /*
     * MOVIE DOWNLOAD
     */
    if (
      data.startsWith(
        "download:"
      )
    ) {
      const item =
        get(
          data,
          "download"
        );

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This download request expired."
          }
        );

        return;
      }

      const limit =
        consume(
          query.from.id,
          "download"
        );

      if (!limit.allowed) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              `Try again in ${limit.retryAfter}s.`
          }
        );

        return;
      }

      const accessGranted =
        await checkDownloadAccess(
          query.from.id,
          chatId
        );

      if (!accessGranted) {
        await bot.answerCallbackQuery(
          query.id
        ).catch(() => {});

        return;
      }

      consumeShareAccess(
        query.from.id,
        SHARE_MODE
      );

      await bot.answerCallbackQuery(
        query.id,
        {
          text:
            "Generating download link..."
        }
      );

      const status =
        await bot.sendMessage(
          chatId,
          "Generating a fresh download link..."
        );

      try {
        const movie =
          await parseNkiriPage(
            item.url
          );

        if (
          !movie.downloadUrl
        ) {
          throw new Error(
            "No download link found"
          );
        }

        const resolved =
          await resolveDownload(
            movie.downloadUrls?.length
              ? movie.downloadUrls
              : movie.downloadUrl,
            {
              referer:
                item.url,
              timeoutMs: 25000
            }
          );

        incrementStat(
          "downloads"
        );

        incrementUserStat(
          query.from.id,
          "downloads"
        );

        trackDownload({
          userId:
            query.from.id,
          username:
            query.from.username || null,
          type:
            "movie",
          title:
            item.title,
          provider:
            resolved.sourceType || null
        });

        await bot.editMessageText(
        downloadMessage(
          movie,
          resolved
        ),
        {
          chat_id:
            chatId,
          message_id:
            status.message_id,
          reply_markup:
            downloadKeyboard(
              resolved
            )
        }
      );

    } catch (error) {
        console.error(
          "DOWNLOAD ERROR:",
          error
        );

        incrementStat(
          "failedDownloads"
        );

        reportBroken(
          item.url,
          {
            title:
              item.title,
            error:
              error.message
          }
        );

        await bot.editMessageText(
          "❌ This movie's download link is currently unavailable.",
          {
            chat_id:
              chatId,
            message_id:
              status.message_id,
            reply_markup: {
              inline_keyboard: [
                [
                  {
                    text:
                      "🔎 Search Another Version",
                    callback_data:
                      "home:search"
                  }
                ],
                [
                  {
                    text:
                      "🏠 Home",
                    callback_data:
                      "home:menu"
                  }
                ]
              ]
            }
          }
        );
      }

      return;
    }

    await bot.answerCallbackQuery(
      query.id
    ).catch(() => {});
  }
);

/*
 * Prevent polling errors from becoming
 * unhandled events.
 */
bot.on(
  "polling_error",
  error => {
    console.error(
      "Telegram polling error:",
      error.message
    );
  }
);

setInterval(
  () => {
    try {
      cleanup();
    } catch (error) {
      console.error(
        "CACHE CLEANUP ERROR:",
        error
      );
    }
  },
  30 * 60 * 1000
);

/*
 * Render health endpoint.
 */
const PORT =
  process.env.PORT ||
  8080;

http
  .createServer(
    (req, res) => {
      res.writeHead(
        200,
        {
          "Content-Type":
            "text/plain"
        }
      );

      res.end(
        "TheNkiri Bot V2 is running"
      );
    }
  )
  .listen(
    PORT,
    "0.0.0.0",
    () => {
      console.log(
        `Health server listening on port ${PORT}`
      );
    }
  );

bot.getMe()
  .then(me => {
    console.log(
      `TELEGRAM BOT: @${me.username} | ID: ${me.id}`
    );
    console.log(
      "BUILD: V2-REPORT-STATS-20260916"
    );
  })
  .catch(error => {
    console.error(
      "BOT IDENTITY ERROR:",
      error.message
    );
  });

console.log(
  "TheNkiri Bot V2 is running..."
);
