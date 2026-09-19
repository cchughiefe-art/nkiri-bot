const { fetch } = require("undici");

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

  for (
    let step = 1;
    step <= maxSteps;
    step++
  ) {
    const response =
      await fetch(
        url,
        {
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
            "manual"
        }
      );

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

    const match =
      html.match(
        /dlTimer\(\{\s*seconds:\s*\d+,\s*link:\s*'([^']+)'/i
      );

    if (!match) {
      throw new Error(
        `LoadedFiles resolver stopped at step ${step}: no next download link`
      );
    }

    const next =
      decodeJsString(
        match[1]
      );

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
