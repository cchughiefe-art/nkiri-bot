require("dotenv").config();

const TelegramBot =
  require("node-telegram-bot-api");
const http = require("http");

const {
  searchNkiri,
  getLatest
} = require("./nkiri/search");

const {
  parseNkiriPage
} = require("./nkiri/parser");

const {
  parseSeriesPage
} = require("./nkiri/series");

const {
  resolveDownloadWella,
  resolveWithFallback
} = require("./resolvers/downloadwella");

const {
  cleanTitle
} = require("./core/media");

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

const PAGE_SIZE = 6;

/*
 * Used when Search is pressed.
 * Telegram will treat the user's next
 * normal message as a search anyway,
 * but this gives clearer UX.
 */
const awaitingSearch =
  new Set();

function homeKeyboard() {
  return {
    inline_keyboard: [
      [
        {
          text: "🔎 Search",
          callback_data: "home:search"
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
      ]
    ]
  };
}

async function showHome(chatId) {
  await bot.sendMessage(
    chatId,
    "🎬 TheNkiri\n\n" +
    "Search for a title or browse the latest releases.",
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
            item.type || null
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

bot.onText(
  /\/start(?:\s+.*)?$/,
  async msg => {
    await showHome(
      msg.chat.id
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
        "🔎 Send me the movie or series name."
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
        "Search failed. Please try again."
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
        `🔎 Searching for "${text}"...`
      );

    try {
      const result =
        await searchNkiri(
          text,
          {
            page: 1,
            perPage:
              PAGE_SIZE
          }
        );

      if (!result.results.length) {
        await bot.editMessageText(
          `No results found for "${text}".\n\n` +
          "Try another spelling or a shorter title.",
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

        return;
      }

      const previous = null;

      const next =
        result.hasNext
          ? create(
              "searchpage",
              {
                query: text,
                page: 2
              }
            )
          : null;

      await bot.editMessageText(
        `🔎 Results for "${text}"\n\n` +
        `${result.total} result(s)` +
        (
          result.pages > 1
            ? ` • Page 1/${result.pages}`
            : ""
        ),
        {
          chat_id:
            chatId,
          message_id:
            status.message_id,
          reply_markup:
            resultKeyboard(
              result.results,
              {
                previous,
                next
              }
            )
        }
      );

    } catch (error) {
      console.error(
        "SEARCH ERROR:",
        error
      );

      await bot.editMessageText(
        "Search failed. Please try again.",
        {
          chat_id:
            chatId,
          message_id:
            status.message_id
        }
      );
    }
  }
);

bot.on(
  "callback_query",
  async query => {
    const data =
      query.data || "";

    const chatId =
      query.message.chat.id;

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
        "🎬 TheNkiri\n\n" +
        "Search for a title or browse the latest releases.",
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
        "🔎 Send me the movie or series name."
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
          "I couldn't load the latest titles."
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
          const grouped =
            new Map();

          for (
            const episode
            of series.episodes
          ) {
            const season =
              episode.season ||
              1;

            if (
              !grouped.has(
                season
              )
            ) {
              grouped.set(
                season,
                []
              );
            }

            grouped
              .get(season)
              .push(episode);
          }

          const buttons = [];

          /*
           * If multiple seasons are actually
           * present on one page, show season
           * buttons first.
           */
          if (
            grouped.size > 1
          ) {
            for (
              const [
                season,
                episodes
              ]
              of grouped
            ) {
              buttons.push([
                {
                  text:
                    `📺 Season ${season}`,
                  callback_data:
                    create(
                      "season",
                      {
                        title:
                          cleanTitle(
                            series.title
                          ),
                        poster:
                          series.poster,
                        season,
                        episodes
                      }
                    )
                }
              ]);
            }

          } else {
            const episodes =
              [...grouped.values()][0];

            for (
              const episode
              of episodes
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
                          episode.downloadUrl
                      }
                    )
                }
              ]);
            }
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
            `${series.episodes.length} episode(s) found.` +
            (
              grouped.size > 1
                ? "\nChoose a season:"
                : "\nChoose an episode:"
            );

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
                      episode.downloadUrl
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
        const resolved =
          await resolveDownloadWella(
            item.downloadUrl
          );

        incrementStat(
          "downloads"
        );

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
          await resolveWithFallback(
            movie.downloadUrls?.length
              ? movie.downloadUrls
              : movie.downloadUrl
          );

        incrementStat(
          "downloads"
        );

        await bot.editMessageText(
          `🎬 ${cleanTitle(movie.title)}\n` +
          (
            movie.size
              ? `📦 ${movie.size}\n`
              : ""
          ) +
          "\nYour download link is ready.\n" +
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
                      "⬇️ Download Now",
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

console.log(
  "TheNkiri Bot V2 is running..."
);
