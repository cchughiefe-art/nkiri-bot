const { fetch } = require("undici");

const url =
  "https://wetafiles.com/9m3x3nyj2vkl/4400.S01E01.(NKIRI.COM).nciodsnoincinsdcnsndoicnsdcndsonicoisdc.mkv.html";

(async () => {
  try {
    const r = await fetch(url, {
      redirect: "follow",
      headers: {
        "user-agent":
          "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
        referer: "https://thenkiri.com/"
      }
    });

    console.log("WETAFILES TLS PASSED");
    console.log("HTTP:", r.status);
    console.log("FINAL:", r.url);
    console.log(
      "CONTENT-TYPE:",
      r.headers.get("content-type")
    );

    const html = await r.text();

    console.log(
      "HTML BYTES:",
      html.length
    );

    console.log(
      "CREATE DOWNLOAD:",
      /create\s+download\s+link/i.test(html)
    );

  } catch (e) {
    console.error("WETAFILES TLS FAILED");
    console.error(e);
    process.exitCode = 1;
  }
})();
