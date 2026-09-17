const { fetch } = require("undici");

async function resolveSabiShares(url) {
  const parsed =
    new URL(url);

  parsed.searchParams.delete(
    "preview"
  );

  const stableUrl =
    parsed.href;

  const response =
    await fetch(stableUrl, {
      redirect: "manual",
      headers: {
        "user-agent":
          "Mozilla/5.0 (Android 15; Mobile)"
      }
    });

  const directUrl =
    response.headers.get(
      "location"
    );

  if (!directUrl) {
    throw new Error(
      `SabiShares did not return media redirect (${response.status})`
    );
  }

  return {
    host: "sabishares",
    type: "sabishares",
    pageUrl: url,
    stableUrl,
    directUrl,
    external: false,
    temporary: true
  };
}

module.exports = {
  resolveSabiShares
};
