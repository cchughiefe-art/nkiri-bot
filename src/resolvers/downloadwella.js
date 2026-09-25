const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const dispatcher = new Agent({
  connect: {
    timeout: 30000
  },
  headersTimeout: 60000,
  bodyTimeout: 60000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  "accept":
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "accept-language": "en-US,en;q=0.9",
  "connection": "close"
};

const sleep = ms =>
  new Promise(resolve => setTimeout(resolve, ms));

async function request(url, options = {}) {
  let lastError;

  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      console.log(
        `DownloadWella request ${attempt}/2...`
      );

      return await fetch(url, {
        ...options,
        dispatcher
      });

    } catch (error) {
      lastError = error;

      if (options.signal?.aborted) {
        throw error;
      }

      console.log(
        `Request ${attempt} failed:`,
        error.cause?.code || error.message
      );

      if (attempt < 2) {
        await sleep(attempt * 1000);
      }
    }
  }

  throw lastError;
}

function cookieHeader(response) {
  const values =
    typeof response.headers.getSetCookie === "function"
      ? response.headers.getSetCookie()
      : [response.headers.get("set-cookie")].filter(Boolean);

  return values
    .map(value => String(value).split(";", 1)[0])
    .filter(Boolean)
    .join("; ");
}

async function resolveDownloadWella(
  url,
  options = {}
) {
  const signal =
    options.signal ||
    AbortSignal.timeout(
      Number(options.timeoutMs) || 25000
    );

  const referer =
    options.referer ||
    "https://thenkiri.com/";

  const first = await request(url, {
    headers: {
      ...HEADERS,
      referer
    },
    redirect: "follow",
    signal
  });

  if (!first.ok) {
    throw new Error(
      `DownloadWella GET returned HTTP ${first.status}`
    );
  }

  const firstHtml = await first.text();
  const $ = cheerio.load(firstHtml);
  const cookie = cookieHeader(first);

  const form = $("form").first();

  if (!form.length) {
    throw new Error(
      "DownloadWella download form not found"
    );
  }

  const params = new URLSearchParams();

  form.find("input").each((_, input) => {
    const name = $(input).attr("name");

    if (!name) return;

    params.set(
      name,
      $(input).attr("value") || ""
    );
  });

  const action = form.attr("action");

  const postUrl = action
    ? new URL(action, first.url).href
    : first.url;

  const second = await request(postUrl, {
    method: "POST",

    headers: {
      ...HEADERS,
      "content-type":
        "application/x-www-form-urlencoded",
      origin: new URL(first.url).origin,
      referer: first.url,
      ...(cookie ? { cookie } : {})
    },

    body: params.toString(),
    redirect: "follow",
    signal
  });

  if (!second.ok) {
    throw new Error(
      `DownloadWella POST returned HTTP ${second.status}`
    );
  }

  const html = await second.text();
  const $$ = cheerio.load(html);

  let directUrl = null;

  $$("a").each((_, element) => {
    const href = $$(element).attr("href");

    if (!href) return;

    const text = $$(element)
      .text()
      .replace(/\s+/g, " ")
      .trim()
      .toLowerCase();

    if (
      text.includes("start download") ||
      /\.(mkv|mp4|avi|mov)(?:$|\?)/i.test(href)
    ) {
      directUrl =
        new URL(href, second.url).href;

      return false;
    }
  });

  if (!directUrl) {
    throw new Error(
      "DownloadWella generated a page but no media URL was found"
    );
  }

  return {
    host: "downloadwella",
    pageUrl: url,
    directUrl
  };
}


async function resolveWithFallback(
  urls,
  options = {}
) {
  const candidates =
    [...new Set(
      (
        Array.isArray(urls)
          ? urls
          : [urls]
      ).filter(Boolean)
    )];

  if (!candidates.length) {
    throw new Error(
      "No download links available"
    );
  }

  const failures = [];

  for (
    let index = 0;
    index < candidates.length;
    index++
  ) {
    const url =
      candidates[index];

    console.log(
      `Trying download mirror ${index + 1}/${candidates.length}...`
    );

    try {
      const resolved =
        await resolveDownloadWella(
          url,
          options
        );

      return {
        ...resolved,
        mirror:
          index + 1,
        mirrors:
          candidates.length
      };

    } catch (error) {
      failures.push({
        url,
        error:
          error.message
      });

      console.log(
        `Mirror ${index + 1} failed:`,
        error.message
      );
    }
  }

  const error =
    new Error(
      `All ${candidates.length} download mirror(s) failed`
    );

  error.failures =
    failures;

  throw error;
}

module.exports = {
  resolveDownloadWella,
  resolveWithFallback
};
