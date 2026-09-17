const {
  resolveDownloadWella,
  resolveWithFallback
} = require("./downloadwella");

const {
  resolveWideShares
} = require("./wideshares");

const {
  resolveSabiShares
} = require("./sabishares");

const {
  isMovieXMediaUrl,
  resolveMovieXUrl
} = require("../providers/moviex");

const MEDIA_RE =
  /\.(mkv|mp4|avi|mov)(?:$|[?#])/i;

function getHost(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return "";
  }
}

function classifyDownload(url) {
  const host = getHost(url);

  if (
    host === "downloadwella.com" ||
    host.endsWith(".downloadwella.com")
  ) {
    return "downloadwella";
  }

  if (
    host === "wideshares.org" ||
    host.endsWith(".wideshares.org")
  ) {
    return "wideshares";
  }

  if (
    host === "sabishares.com" ||
    host.endsWith(".sabishares.com")
  ) {
    return "sabishares";
  }

  if (
    host === "nkiserv.com" ||
    host.endsWith(".nkiserv.com")
  ) {
    return MEDIA_RE.test(url)
      ? "direct"
      : "nkiserv";
  }

  if (
    host === "wetafiles.com" ||
    host.endsWith(".wetafiles.com")
  ) {
    return "wetafiles";
  }

  if (MEDIA_RE.test(url))
    return "direct";

  return "external";
}

async function resolveDownload(input) {
  const urls = Array.isArray(input)
    ? input.filter(Boolean)
    : [input].filter(Boolean);

  if (!urls.length)
    throw new Error("No download URL supplied");

  /* MOVIEX_RESOLVER_V1 */
  for (const url of urls) {
    if (isMovieXMediaUrl(url)) {
      return await resolveMovieXUrl(url);
    }
  }

  /*
   * Authorized FZMovies delivery hosts.
   */
  for (const url of urls) {
    const type =
      classifyDownload(url);

    if (type === "wideshares") {
      return await resolveWideShares(
        url
      );
    }

    if (type === "sabishares") {
      return await resolveSabiShares(
        url
      );
    }
  }

  /*
   * Try direct media first if present.
   */
  for (const url of urls) {
    const type = classifyDownload(url);

    if (type === "direct") {
      return {
        host: getHost(url),
        type: "direct",
        pageUrl: url,
        directUrl: url,
        external: false
      };
    }
  }

  /*
   * DownloadWella supports automatic resolution and
   * sequential mirror fallback.
   */
  const dw = urls.filter(
    url =>
      classifyDownload(url) ===
      "downloadwella"
  );

  if (dw.length) {
    const result =
      dw.length > 1
        ? await resolveWithFallback(dw)
        : await resolveDownloadWella(dw[0]);

    return {
      ...result,
      type: "downloadwella",
      external: false
    };
  }

  /*
   * WetaFiles uses its own public download page.
   * Keep the user on that page rather than attempting
   * to bypass CAPTCHA/delay/access controls.
   */
  const weta = urls.find(
    url =>
      classifyDownload(url) ===
      "wetafiles"
  );

  if (weta) {
    return {
      host: getHost(weta),
      type: "wetafiles",
      pageUrl: weta,
      directUrl: weta,
      external: true
    };
  }

  /*
   * Unknown systems remain usable as external links.
   */
  const fallback = urls[0];

  return {
    host: getHost(fallback),
    type: classifyDownload(fallback),
    pageUrl: fallback,
    directUrl: fallback,
    external: true
  };
}

module.exports = {
  classifyDownload,
  resolveDownload
};
