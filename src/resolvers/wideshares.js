const { fetch } = require("undici");

async function resolveWideShares(url) {
  const parsed = new URL(url);

  const path =
    parsed.searchParams.get("path");

  if (!path) {
    throw new Error(
      "WideShares path missing"
    );
  }

  const forceUrl =
    "https://wideshares.org/force_download.php?path=" +
    encodeURIComponent(path);

  const response =
    await fetch(forceUrl, {
      redirect: "manual",
      headers: {
        "user-agent":
          "Mozilla/5.0 (Android 15; Mobile)"
      }
    });

  const directUrl =
    response.headers.get("location");

  if (!directUrl) {
    throw new Error(
      `WideShares did not return media redirect (${response.status})`
    );
  }

  return {
    host: "wideshares",
    type: "wideshares",
    pageUrl: url,
    directUrl,
    external: false,
    temporary: true
  };
}

module.exports = {
  resolveWideShares
};
