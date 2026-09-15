require("dotenv").config();

const TelegramBot = require("node-telegram-bot-api");
const http = require("http");

const {
  parseNkiriPage
} = require("./nkiri/parser");

const {
  resolveDownloadWella
} = require("./resolvers/downloadwella");

const token = process.env.TELEGRAM_BOT_TOKEN;

if (!token) {
  throw new Error("TELEGRAM_BOT_TOKEN missing");
}

const bot = new TelegramBot(token, {
  polling: true
});

const TEST_MOVIE =
  "https://thenkiri.com/awareness-2023-download-spanish-movie/";

bot.onText(/\/start/, async msg => {
  await bot.sendMessage(
    msg.chat.id,
    "Welcome to TheNkiri Movie Bot.\n\nSend /awareness to test movie downloads."
  );
});

bot.onText(/\/awareness/, async msg => {
  const chatId = msg.chat.id;

  try {
    const movie =
      await parseNkiriPage(TEST_MOVIE);

    const caption =
      `🎬 ${movie.title}\n` +
      `📦 ${movie.size || "Unknown size"}\n\n` +
      `Tap below to generate your download link.`;

    const options = {
      caption,

      reply_markup: {
        inline_keyboard: [
          [
            {
              text: "Download Movie",
              callback_data: "download_awareness"
            }
          ]
        ]
      }
    };

    if (movie.poster) {
      await bot.sendPhoto(
        chatId,
        movie.poster,
        options
      );
    } else {
      await bot.sendMessage(
        chatId,
        caption,
        {
          reply_markup:
            options.reply_markup
        }
      );
    }

  } catch (error) {
    console.error("MOVIE ERROR:", error);

    await bot.sendMessage(
      chatId,
      "I couldn't load this movie. Please try again."
    );
  }
});

bot.on("callback_query", async query => {

  if (query.data !== "download_awareness") {
    return;
  }

  const chatId = query.message.chat.id;

  try {
    await bot.answerCallbackQuery(
      query.id,
      {
        text: "Generating download link..."
      }
    );

    const status =
      await bot.sendMessage(
        chatId,
        "Generating a fresh download link..."
      );

    /*
     * Fetch the movie page again because
     * TheNkiri/DownloadWella links can change.
     */
    const movie =
      await parseNkiriPage(TEST_MOVIE);

    if (!movie.downloadUrl) {
      throw new Error(
        "No DownloadWella link found"
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

    if (!resolved.directUrl) {
      throw new Error(
        "Direct download URL not generated"
      );
    }

    console.log(
      "Fresh direct URL generated."
    );

    await bot.editMessageText(
      `🎬 ${movie.title}\n` +
      `📦 ${movie.size || "Unknown size"}\n\n` +
      `Your download link is ready.\n\n` +
      `The link may expire, so start the download now.`,
      {
        chat_id: chatId,
        message_id: status.message_id,

        reply_markup: {
          inline_keyboard: [
            [
              {
                text: "⬇️ Download Movie",
                url: resolved.directUrl
              }
            ]
          ]
        }
      }
    );

  } catch (error) {

    console.error(
      "DOWNLOAD LINK ERROR:",
      error
    );

    try {
      await bot.sendMessage(
        chatId,
        "I couldn't generate the download link. Please try again."
      );
    } catch {}
  }
});

bot.on(
  "polling_error",
  error => {
    console.error(
      "Telegram polling error:",
      error.message
    );
  }
);


/*
 * Render health server.
 * Render supplies PORT automatically.
 */

const PORT =
  process.env.PORT || 8080;

http
  .createServer((req, res) => {

    res.writeHead(200, {
      "Content-Type":
        "text/plain"
    });

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
