require("dotenv").config();

const TelegramBot = require("node-telegram-bot-api");
const { fetch, Agent } = require("undici");
const { Readable } = require("stream");

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

const mediaAgent = new Agent({
  connect: {
    timeout: 30000
  },
  headersTimeout: 60000,

  // Large movie downloads can take a long time.
  bodyTimeout: 0
});

const TEST_MOVIE =
  "https://thenkiri.com/awareness-2023-download-spanish-movie/";

const cache = new Map();

function cleanFilename(url) {
  try {
    const pathname =
      new URL(url).pathname;

    return decodeURIComponent(
      pathname.split("/").pop()
    ) || "movie.mkv";
  } catch {
    return "movie.mkv";
  }
}

async function getMovieStream(
  directUrl,
  referer
) {
  console.log("Opening media stream...");

  const response = await fetch(
    directUrl,
    {
      dispatcher: mediaAgent,
      redirect: "follow",

      headers: {
        "user-agent":
          "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",

        "accept":
          "application/octet-stream,*/*",

        "referer":
          referer
      }
    }
  );

  console.log(
    "Media HTTP status:",
    response.status
  );

  console.log(
    "Media size:",
    response.headers.get(
      "content-length"
    )
  );

  if (!response.ok) {
    if (response.body) {
      await response.body.cancel();
    }

    throw new Error(
      `Media server returned HTTP ${response.status}`
    );
  }

  if (!response.body) {
    throw new Error(
      "Media server returned no body"
    );
  }

  return Readable.fromWeb(
    response.body
  );
}

bot.onText(/\/start/, async msg => {
  await bot.sendMessage(
    msg.chat.id,
    "Welcome to TheNkiri Movie Bot.\n\nSend /awareness to test movie delivery."
  );
});

bot.onText(/\/awareness/, async msg => {
  const chatId = msg.chat.id;

  try {
    const movie =
      await parseNkiriPage(
        TEST_MOVIE
      );

    await bot.sendPhoto(
      chatId,
      movie.poster,
      {
        caption:
          `🎬 ${movie.title}\n` +
          `📦 ${movie.size || "Unknown size"}`,

        reply_markup: {
          inline_keyboard: [
            [
              {
                text:
                  "Download Movie",

                callback_data:
                  "download_awareness"
              }
            ]
          ]
        }
      }
    );

  } catch (error) {
    console.error(
      "MOVIE ERROR:",
      error
    );

    await bot.sendMessage(
      chatId,
      "I couldn't load this movie."
    );
  }
});

bot.on(
  "callback_query",
  async query => {

    if (
      query.data !==
      "download_awareness"
    ) {
      return;
    }

    const chatId =
      query.message.chat.id;

    await bot.answerCallbackQuery(
      query.id
    );

    try {

      // Telegram already has it.
      if (
        cache.has("awareness")
      ) {
        console.log(
          "Using Telegram cache..."
        );

        await bot.sendDocument(
          chatId,
          cache.get("awareness")
        );

        return;
      }

      const status =
        await bot.sendMessage(
          chatId,
          "Preparing movie..."
        );

      const movie =
        await parseNkiriPage(
          TEST_MOVIE
        );

      console.log(
        "Resolving DownloadWella..."
      );

      const resolved =
        await resolveDownloadWella(
          movie.downloadUrl
        );

      console.log(
        "Direct media URL resolved."
      );

      await bot.editMessageText(
        "Uploading movie to Telegram...",
        {
          chat_id: chatId,
          message_id:
            status.message_id
        }
      );

      const stream =
        await getMovieStream(
          resolved.directUrl,
          movie.downloadUrl
        );

      const filename =
        cleanFilename(
          resolved.directUrl
        );

      console.log(
        "Sending:",
        filename
      );

      const sent =
        await bot.sendDocument(
          chatId,

          stream,

          {
            caption:
              "Awareness (2023)\nTheNkiri"
          },

          {
            filename,
            contentType:
              "application/octet-stream"
          }
        );

      console.log(
        "Telegram upload completed."
      );

      if (
        sent.document &&
        sent.document.file_id
      ) {
        cache.set(
          "awareness",
          sent.document.file_id
        );

        console.log(
          "Telegram file_id cached."
        );
      }

      await bot.editMessageText(
        "Movie delivered.",
        {
          chat_id: chatId,
          message_id:
            status.message_id
        }
      );

    } catch (error) {

      console.error(
        "\nDOWNLOAD ERROR:"
      );

      console.error(error);

      try {
        await bot.sendMessage(
          chatId,
          "The movie could not be delivered. Please try again."
        );
      } catch {}
    }
  }
);

bot.on(
  "polling_error",
  error => {
    console.error(
      "Telegram polling error:",
      error.message
    );
  }
);

console.log(
  "TheNkiri Telegram bot is running..."
);

const http = require("http");

const PORT = process.env.PORT || 8080;

http.createServer((req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/plain"
  });
  res.end("TheNkiri Telegram Bot is running");
}).listen(PORT, "0.0.0.0", () => {
  console.log(`Health server listening on port ${PORT}`);
});
