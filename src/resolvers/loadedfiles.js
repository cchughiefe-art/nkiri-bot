const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const dispatcher = new Agent({
  connect: { timeout: 15000 },
  headersTimeout: 25000,
  bodyTimeout: 25000
});

const UA =
  "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36";

function decodeJsString(raw) {
  return JSON.parse(
    `"${String(raw).replace(/"/g, '\\"')}"`
  );
}

function cookieFromHeaders(headers, previous = "") {
  const setCookie =
    headers.get("set-cookie");

  if (!setCookie) {
    return previous;
  }

  return setCookie
    .split(",")
    .map(
      value =>
        value
          .split(";")[0]
          .trim()
    )
    .filter(Boolean)
    .join("; ");
}

async function resolveLoadedFiles(
  input,
  options = {}
) {
  const startUrl =
    String(input || "").trim();

  if (
    !/^https?:\/\/(?:www\.)?loadedfiles\.net\//i.test(
      startUrl
    )
  ) {
    throw new Error(
      "Not a LoadedFiles URL"
    );
  }

  const maxSteps =
    Math.max(
      1,
      Math.min(
        10,
        Number(options.maxSteps) || 6
      )
    );

  let url =
    startUrl;

  let referer = "";
  let cookie = "";
  const signal =
    options.signal ||
    AbortSignal.timeout(
      Number(options.timeoutMs) || 25000
    );

  for (
    let step = 1;
    step <= maxSteps;
    step++
  ) {
    const response =
      await fetch(
        url,
        {
          dispatcher,
          headers: {
            "user-agent":
              UA,

            accept:
              "text/html,application/xhtml+xml,*/*",

            ...(referer
              ? { referer }
              : {}),

            ...(cookie
              ? { cookie }
              : {})
          },

          redirect:
            "manual",

          signal
        }
      );

    if (
      response.status >= 400
    ) {
      throw new Error(
        `LoadedFiles HTTP ${response.status} at step ${step}`
      );
    }

    cookie =
      cookieFromHeaders(
        response.headers,
        cookie
      );

    const location =
      response.headers.get(
        "location"
      );

    const contentType =
      String(
        response.headers.get(
          "content-type"
        ) || ""
      ).toLowerCase();

    const disposition =
      response.headers.get(
        "content-disposition"
      );

    /*
     * LoadedFiles eventually responds with
     * a normal redirect to the temporary file.
     */
    if (location) {
      const directUrl =
        new URL(
          location,
          url
        ).href;

      return {
        host:
          new URL(
            directUrl
          ).hostname,

        type:
          "direct",

        provider:
          "loadedfiles",

        pageUrl:
          startUrl,

        directUrl,

        temporary:
          true,

        contentType:
          contentType ||
          null,

        disposition:
          disposition ||
          null,

        steps:
          step
      };
    }

    /*
     * Also support cases where LoadedFiles
     * directly returns the media response.
     */
    if (
      !contentType.includes(
        "text/html"
      )
    ) {
      return {
        host:
          new URL(
            url
          ).hostname,

        type:
          "direct",

        provider:
          "loadedfiles",

        pageUrl:
          startUrl,

        directUrl:
          url,

        temporary:
          true,

        contentType:
          contentType ||
          null,

        disposition:
          disposition ||
          null,

        steps:
          step
      };
    }

    const html =
      await response.text();

    const $ = cheerio.load(html);

    const candidates = [];

    const patterns = [
      /dlTimer\([\s\S]*?link\s*:\s*["']([^"']+)["']/i,
      /(?:window\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']/i,
      /(?:downloadUrl|download_url|fileUrl|file_url|link)\s*[:=]\s*["']([^"']+)["']/i
    ];

    for (const pattern of patterns) {
      const match = html.match(pattern);
      if (match?.[1]) candidates.push(match[1]);
    }

    $("a[href]").each((_, element) => {
      const anchor = $(element);
      const href = anchor.attr("href");
      const text = anchor.text().replace(/\s+/g, " ").trim();

      if (
        href &&
        (/download|continue|get file|start/i.test(text) ||
          /\.(mkv|mp4|avi|mov)(?:$|[?#])/i.test(href))
      ) {
        candidates.push(href);
      }
    });

    const nextRaw =
      candidates.find(Boolean);

    if (!nextRaw) {
      throw new Error(
        `LoadedFiles resolver stopped at step ${step}: no next download link`
      );
    }

    const next =
      new URL(
        decodeJsString(nextRaw),
        url
      ).href;

    if (
      !/^https?:\/\//i.test(
        next
      )
    ) {
      throw new Error(
        `LoadedFiles returned invalid URL at step ${step}`
      );
    }

    referer =
      url;

    url =
      next;
  }

  throw new Error(
    `LoadedFiles exceeded ${maxSteps} resolution steps`
  );
}

module.exports = {
  resolveLoadedFiles
};
