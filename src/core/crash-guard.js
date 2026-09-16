require("dotenv").config();

const TelegramBot = require("node-telegram-bot-api");

const WRAPPED = Symbol.for("nkiri.crashGuardWrapped");

function errorText(error) {
  return String(
    error?.response?.body?.description ||
    error?.message ||
    error ||
    "Unknown error"
  );
}

function harmlessTelegramError(error) {
  const m = errorText(error).toLowerCase();

  return (
    m.includes("query is too old") ||
    m.includes("query id is invalid") ||
    m.includes("response timeout expired") ||
    m.includes("message is not modified") ||
    m.includes("message to edit not found") ||
    m.includes("message can't be edited") ||
    m.includes("message can not be edited") ||
    m.includes("message to delete not found") ||
    m.includes("message can't be deleted") ||
    m.includes("there is no text in the message to edit") ||
    m.includes("there is no caption in the message to edit") ||
    m.includes("bot was blocked by the user") ||
    m.includes("user is deactivated") ||
    m.includes("chat not found")
  );
}

function transientNetworkError(error) {
  const code =
    String(error?.code || "").toUpperCase();

  const m =
    errorText(error).toLowerCase();

  return (
    [
      "ETIMEDOUT",
      "ESOCKETTIMEDOUT",
      "ECONNRESET",
      "ECONNREFUSED",
      "EAI_AGAIN",
      "ENETUNREACH",
      "EHOSTUNREACH",
      "UND_ERR_CONNECT_TIMEOUT",
      "UND_ERR_HEADERS_TIMEOUT",
      "UND_ERR_SOCKET"
    ].includes(code) ||
    m.includes("socket hang up") ||
    m.includes("timed out")
  );
}

function log(scope, error) {
  console.error(
    `[crash-guard] ${scope}:`,
    errorText(error)
  );
}

function wrapHandler(scope, handler) {
  if (typeof handler !== "function")
    return handler;

  if (handler[WRAPPED])
    return handler;

  const wrapped = function (...args) {
    try {
      const result =
        handler.apply(this, args);

      if (
        result &&
        typeof result.then === "function"
      ) {
        result.catch(error => {
          log(
            `${scope} rejected`,
            error
          );
        });
      }

      return result;
    } catch (error) {
      log(
        `${scope} threw`,
        error
      );

      return undefined;
    }
  };

  Object.defineProperty(
    wrapped,
    WRAPPED,
    { value: true }
  );

  return wrapped;
}

/*
 * Protect async Telegram event handlers.
 */
const originalOn =
  TelegramBot.prototype.on;

TelegramBot.prototype.on =
  function (event, handler) {
    return originalOn.call(
      this,
      event,
      wrapHandler(
        `event:${event}`,
        handler
      )
    );
  };

/*
 * Protect /start, /stats, /search etc.
 */
const originalOnText =
  TelegramBot.prototype.onText;

TelegramBot.prototype.onText =
  function (regexp, handler) {
    return originalOnText.call(
      this,
      regexp,
      wrapHandler(
        `onText:${regexp}`,
        handler
      )
    );
  };

/*
 * Protect editMessageText.
 *
 * Photo/video messages use captions instead
 * of normal Telegram text.
 */
const originalEdit =
  TelegramBot.prototype.editMessageText;

TelegramBot.prototype.editMessageText =
  async function (text, options = {}) {
    try {
      return await originalEdit.call(
        this,
        text,
        options
      );
    } catch (error) {
      const m =
        errorText(error).toLowerCase();

      if (
        m.includes(
          "there is no text in the message to edit"
        )
      ) {
        try {
          return await this.editMessageCaption(
            text,
            options
          );
        } catch (captionError) {
          log(
            "caption fallback",
            captionError
          );

          if (options.chat_id) {
            const {
              chat_id,
              message_id,
              inline_message_id,
              ...sendOptions
            } = options;

            try {
              return await this.sendMessage(
                chat_id,
                text,
                sendOptions
              );
            } catch (sendError) {
              log(
                "fresh-message fallback",
                sendError
              );

              return false;
            }
          }

          return false;
        }
      }

      if (harmlessTelegramError(error)) {
        console.warn(
          "[crash-guard] ignored Telegram edit:",
          errorText(error)
        );

        return false;
      }

      throw error;
    }
  };

/*
 * Expired callback buttons must never crash.
 */
const originalAnswer =
  TelegramBot.prototype.answerCallbackQuery;

TelegramBot.prototype.answerCallbackQuery =
  async function (...args) {
    try {
      return await originalAnswer.apply(
        this,
        args
      );
    } catch (error) {
      if (harmlessTelegramError(error)) {
        console.warn(
          "[crash-guard] ignored callback:",
          errorText(error)
        );

        return false;
      }

      throw error;
    }
  };

/*
 * Deleted/missing messages must never crash.
 */
const originalDelete =
  TelegramBot.prototype.deleteMessage;

TelegramBot.prototype.deleteMessage =
  async function (...args) {
    try {
      return await originalDelete.apply(
        this,
        args
      );
    } catch (error) {
      if (harmlessTelegramError(error)) {
        console.warn(
          "[crash-guard] ignored delete:",
          errorText(error)
        );

        return false;
      }

      throw error;
    }
  };

/*
 * Final safety net for missed async failures.
 */
process.on(
  "unhandledRejection",
  reason => {
    log(
      "unhandled rejection contained",
      reason
    );
  }
);

/*
 * Known Telegram/network errors remain alive.
 * Truly unknown runtime corruption is logged
 * and left for Raven to restart safely.
 */
process.on(
  "uncaughtException",
  error => {
    if (
      harmlessTelegramError(error) ||
      transientNetworkError(error)
    ) {
      log(
        "operational exception contained",
        error
      );

      return;
    }

    log(
      "FATAL exception",
      error
    );

    setTimeout(
      () => process.exit(1),
      750
    ).unref();
  }
);

console.log(
  "Crash guard enabled: Telegram + async protection active"
);
