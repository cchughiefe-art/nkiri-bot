require("dotenv").config();

const TelegramBot = require("node-telegram-bot-api");
const http = require("http");

const { searchNkiri } =
  require("./nkiri/search");

const { parseNkiriPage } =
  require("./nkiri/parser");

const { resolveDownloadWella } =
  require("./resolvers/downloadwella");

const token = process.env.TELEGRAM_BOT_TOKEN;

if (!token) {
  throw new Error("TELEGRAM_BOT_TOKEN missing");
}

const bot = new TelegramBot(token, {
  polling: true
});

/*
 * Temporary result cache.
 *
 * Telegram callback_data is limited in size,
 * so we store URLs here instead of putting
 * complete URLs inside buttons.
 */
const searches = new Map();

function makeId() {
  return Math.random()
    .toString(36)
    .slice(2, 10);
}

bot.onText(/\/start/, async msg => {
  await bot.sendMessage(
    msg.chat.id,
    "Welcome to TheNkiri Movie Bot.\n\n" +
    "Send me the name of a movie or TV series.\n\n" +
    "Example:\nAvatar"
  );
});

/*
 * Handle normal text searches.
 */
bot.on("message", async msg => {
  const chatId = msg.chat.id;

  if (!msg.text) return;

  const query = msg.text.trim();

  /*
   * Commands are handled separately.
   */
  if (query.startsWith("/")) {
    return;
  }

  if (query.length < 2) {
    await bot.sendMessage(
      chatId,
      "Please enter a movie or series name."
    );
    return;
  }

  const status =
    await bot.sendMessage(
      chatId,
      `Searching TheNkiri for "${query}"...`
    );

  try {
    const results =
      await searchNkiri(query);

    if (!results.length) {
      await bot.editMessageText(
        `No results found for "${query}".`,
        {
          chat_id: chatId,
          message_id: status.message_id
        }
      );
      return;
    }

    const keyboard = [];

    for (const result of results) {
      const id = makeId();

      searches.set(id, {
        url: result.url,
        title: result.title,
        created: Date.now()
      });

      keyboard.push([
        {
          text:
            result.title.length > 55
              ? result.title.slice(0, 52) + "..."
              : result.title,

          callback_data: `movie:${id}`
        }
      ]);
    }

    await bot.editMessageText(
      `Results for "${query}"\n\n` +
      `Choose a movie or series:`,
      {
        chat_id: chatId,
        message_id: status.message_id,

        reply_markup: {
          inline_keyboard: keyboard
        }
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
        chat_id: chatId,
        message_id: status.message_id
      }
    );
  }
});


/*
 * Handle buttons.
 */
bot.on(
  "callback_query",
  async query => {

    const data =
      query.data || "";

    const chatId =
      query.message.chat.id;

    /*
     * User selected a search result.
     */
    if (data.startsWith("movie:")) {

      const id =
        data.slice(6);

      const item =
        searches.get(id);

      if (!item) {
        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This search expired. Search again."
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
          "Loading movie information..."
        );

      try {
        const movie =
          await parseNkiriPage(
            item.url
          );

        /*
         * Keep the selected page for
         * the download button.
         */
        const downloadId =
          makeId();

        searches.set(
          downloadId,
          {
            url: item.url,
            title: movie.title,
            created: Date.now()
          }
        );

        const caption =
          `🎬 ${movie.title}\n` +
          `📦 ${movie.size || "Size unavailable"}\n\n` +
          `Tap below to generate a fresh download link.`;

        const keyboard = {
          inline_keyboard: [
            [
              {
                text:
                  "⬇️ Download Movie",

                callback_data:
                  `download:${downloadId}`
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
              reply_markup: keyboard
            }
          );

        } else {

          await bot.sendMessage(
            chatId,
            caption,
            {
              reply_markup: keyboard
            }
          );
        }

      } catch (error) {

        console.error(
          "DETAIL ERROR:",
          error
        );

        await bot.editMessageText(
          "I couldn't load this title.",
          {
            chat_id: chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }


    /*
     * Generate the temporary direct
     * browser download URL.
     */
    if (data.startsWith("download:")) {

      const id =
        data.slice(9);

      const item =
        searches.get(id);

      if (!item) {

        await bot.answerCallbackQuery(
          query.id,
          {
            text:
              "This download request expired. Search again."
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

        if (!movie.downloadUrl) {
          throw new Error(
            "No download link found on title page"
          );
        }

        console.log(
          "Resolving:",
          movie.downloadUrl
        );

        const resolved =
          await resolveDownloadWella(
            movie.downloadUrl
          );

        console.log(
          "Fresh direct URL generated."
        );

        await bot.editMessageText(
          `🎬 ${movie.title}\n` +
          `📦 ${movie.size || "Size unavailable"}\n\n` +
          `Your download link is ready.\n` +
          `The link may expire, so start the download now.`,
          {
            chat_id: chatId,
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

        await bot.editMessageText(
          "I couldn't generate the download link. Please try again.",
          {
            chat_id: chatId,
            message_id:
              status.message_id
          }
        );
      }

      return;
    }
  }
);


/*
 * Remove old callback entries so memory
 * doesn't grow forever.
 */
setInterval(() => {

  const cutoff =
    Date.now() -
    60 * 60 * 1000;

  for (
    const [id, item]
    of searches
  ) {

    if (item.created < cutoff) {
      searches.delete(id);
    }
  }

}, 10 * 60 * 1000);


/*
 * Render health endpoint.
 */
const PORT =
  process.env.PORT || 8080;

http
  .createServer((req, res) => {

    res.writeHead(
      200,
      {
        "Content-Type":
          "text/plain"
      }
    );

    res.end(
      "TheNkiri Telegram Bot is running"
    );

  })
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
  "TheNkiri Telegram bot is running..."
);
