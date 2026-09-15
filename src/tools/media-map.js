const fs = require("fs");
const path = require("path");
const cheerio = require("cheerio");
const { fetch, Agent } = require("undici");

const BASE = "https://thenkiri.com";

const OUTPUT =
  path.join(process.cwd(), "data", "media-map.json");

const CHECKPOINT =
  path.join(process.cwd(), "data", "media-map-checkpoint.json");

const dispatcher = new Agent({
  connect: { timeout: 30000 },
  headersTimeout: 60000,
  bodyTimeout: 60000
});

const HEADERS = {
  "user-agent":
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Safari/537.36",
  accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
};

const sleep = ms =>
  new Promise(resolve => setTimeout(resolve, ms));

function normalize(url, base = BASE) {
  try {
    const u = new URL(url, base);

    if (!/^https?:$/.test(u.protocol))
      return null;

    u.hash = "";

    return u.href;
  } catch {
    return null;
  }
}

function classify(url) {
  try {
    const u = new URL(url);
    const host = u.hostname.toLowerCase();

    if (
      host === "downloadwella.com" ||
      host.endsWith(".downloadwella.com")
    ) {
      return "downloadwella";
    }

    if (
      host === "nkiserv.com" ||
      host.endsWith(".nkiserv.com")
    ) {
      return /\.(mkv|mp4|avi|mov)(?:$|[?#])/i.test(url)
        ? "nkiserv-direct"
        : "nkiserv";
    }

    if (
      host === "wetafiles.com" ||
      host.endsWith(".wetafiles.com")
    ) {
      return "wetafiles";
    }

    if (
      /\.(mkv|mp4|avi|mov)(?:$|[?#])/i.test(url)
    ) {
      return "external-direct-media";
    }

    return "external";
  } catch {
    return "invalid";
  }
}

function pageType(title) {
  const s = String(title || "").toLowerCase();

  if (
    s.includes("korean drama") ||
    s.includes("k-drama")
  ) return "k-drama";

  if (
    s.includes("tv series") ||
    /\bs\d{1,2}\b/i.test(s)
  ) return "series";

  if (s.includes("movie"))
    return "movie";

  return "unknown";
}

function isDownloadCandidate(text, url) {
  const t = String(text || "").toLowerCase();

  let host = "";

  try {
    host = new URL(url).hostname.toLowerCase();
  } catch {
    return false;
  }

  if (
    host === "downloadwella.com" ||
    host.endsWith(".downloadwella.com") ||
    host === "nkiserv.com" ||
    host.endsWith(".nkiserv.com")
  ) return true;

  if (
    /\.(mkv|mp4|avi|mov)(?:$|[?#])/i.test(url)
  ) return true;

  return (
    t.includes("download movie") ||
    t.includes("download episode") ||
    t.includes("create download") ||
    t === "download"
  );
}

async function request(url) {
  let last;

  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const r = await fetch(url, {
        dispatcher,
        headers: HEADERS,
        redirect: "follow"
      });

      if (!r.ok)
        throw new Error(`HTTP ${r.status}`);

      return r;
    } catch (e) {
      last = e;

      if (attempt < 3)
        await sleep(attempt * 1500);
    }
  }

  throw last;
}

function loadCheckpoint() {
  if (!fs.existsSync(CHECKPOINT))
    return null;

  try {
    return JSON.parse(
      fs.readFileSync(CHECKPOINT, "utf8")
    );
  } catch {
    return null;
  }
}

function saveCheckpoint(state) {
  fs.writeFileSync(
    CHECKPOINT,
    JSON.stringify(state, null, 2)
  );
}

function saveReport(state) {
  const hostCounts = {};
  const typeCounts = {};

  for (const link of state.mediaLinks) {
    let host = "invalid";

    try {
      host = new URL(link.url).hostname;
    } catch {}

    hostCounts[host] =
      (hostCounts[host] || 0) + 1;

    typeCounts[link.deliveryType] =
      (typeCounts[link.deliveryType] || 0) + 1;
  }

  const report = {
    generatedAt: new Date().toISOString(),

    summary: {
      pagesDiscovered: state.seen.length,
      pagesScanned: state.scanned.length,
      mediaLinks: state.mediaLinks.length,
      failedPages: state.failures.length
    },

    hosts: Object.fromEntries(
      Object.entries(hostCounts)
        .sort((a, b) => b[1] - a[1])
    ),

    deliveryTypes: typeCounts,

    mediaLinks: state.mediaLinks,

    failures: state.failures
  };

  fs.writeFileSync(
    OUTPUT,
    JSON.stringify(report, null, 2)
  );
}

async function discoverFromPage(url, state) {
  const r = await request(url);
  const html = await r.text();
  const $ = cheerio.load(html);

  const title =
    $("h1").first().text().trim() ||
    $("title").text().trim();

  const type = pageType(title);

  $("a[href]").each((_, el) => {
    const href = $(el).attr("href");
    const text = $(el)
      .text()
      .replace(/\s+/g, " ")
      .trim();

    const target = normalize(href, r.url);

    if (!target) return;

    let targetHost;

    try {
      targetHost =
        new URL(target).hostname.toLowerCase();
    } catch {
      return;
    }

    /*
     * Discover additional TheNkiri pages.
     */
    if (
      targetHost === "thenkiri.com" ||
      targetHost === "www.thenkiri.com"
    ) {
      if (
        !state.seen.includes(target) &&
        !/\.(jpg|jpeg|png|gif|webp|css|js|xml)(?:$|\?)/i.test(target)
      ) {
        state.seen.push(target);
      }
    }

    /*
     * Record download/storage links.
     */
    if (
      targetHost !== "thenkiri.com" &&
      targetHost !== "www.thenkiri.com" &&
      isDownloadCandidate(text, target)
    ) {
      const key =
        `${url}|${target}`;

      if (!state.mediaKeys.includes(key)) {
        state.mediaKeys.push(key);

        state.mediaLinks.push({
          page: url,
          pageTitle: title,
          pageType: type,
          anchorText: text,
          url: target,
          host: targetHost,
          deliveryType: classify(target)
        });
      }
    }
  });

  return title;
}

async function main() {
  fs.mkdirSync(
    path.dirname(OUTPUT),
    { recursive: true }
  );

  let state =
    loadCheckpoint() || {
      seen: [
        `${BASE}/`,
        `${BASE}/movies-menu/`,
        `${BASE}/tv-series-menu/`,
        `${BASE}/korean-drama-menu/`
      ],
      scanned: [],
      mediaLinks: [],
      mediaKeys: [],
      failures: []
    };

  /*
   * Sets make duplicate checking much faster than
   * repeatedly scanning large arrays.
   */
  const scanned =
    new Set(state.scanned);

  const seenSet =
    new Set(state.seen);

  const mediaKeySet =
    new Set(state.mediaKeys);

  /*
   * Keep the existing functions compatible while making
   * includes() use O(1) Set lookups during this run.
   */
  state.seen.includes =
    value => seenSet.has(value);

  state.mediaKeys.includes =
    value => mediaKeySet.has(value);

  const originalSeenPush =
    state.seen.push.bind(state.seen);

  state.seen.push = (...items) => {
    let length = state.seen.length;

    for (const item of items) {
      if (!seenSet.has(item)) {
        seenSet.add(item);
        length = originalSeenPush(item);
      }
    }

    return length;
  };

  const originalMediaPush =
    state.mediaKeys.push.bind(state.mediaKeys);

  state.mediaKeys.push = (...items) => {
    let length = state.mediaKeys.length;

    for (const item of items) {
      if (!mediaKeySet.has(item)) {
        mediaKeySet.add(item);
        length = originalMediaPush(item);
      }
    }

    return length;
  };

  const MAX_PER_RUN =
    Number(process.env.MAP_LIMIT || 1000);

  const WORKERS =
    Math.max(
      1,
      Number(process.env.MAP_WORKERS || 5)
    );

  const DELAY =
    Math.max(
      0,
      Number(process.env.MAP_DELAY || 100)
    );

  let claimed = 0;
  let cursor = 0;
  let completed = 0;

  /*
   * Only one checkpoint write at a time.
   */
  let checkpointTimer = null;

  function scheduleCheckpoint() {
    if (checkpointTimer)
      return;

    checkpointTimer = setTimeout(() => {
      checkpointTimer = null;
      saveCheckpoint(state);
    }, 1000);
  }

  function nextUrl() {
    while (cursor < state.seen.length) {
      const url = state.seen[cursor++];

      if (scanned.has(url))
        continue;

      if (claimed >= MAX_PER_RUN)
        return null;

      /*
       * Claim immediately so another worker cannot
       * take the same URL.
       */
      scanned.add(url);
      claimed++;

      return url;
    }

    return null;
  }

  async function worker(id) {
    while (true) {
      const url = nextUrl();

      if (!url)
        return;

      const number =
        state.scanned.length + completed + 1;

      console.log(
        `[W${id}] [${number}] ${url}`
      );

      try {
        const title =
          await discoverFromPage(url, state);

        console.log(
          `    W${id}: ${title.slice(0, 90)}`
        );

      } catch (error) {
        console.log(
          `    W${id}: FAILED: ${error.message}`
        );

        state.failures.push({
          url,
          error: error.message,
          at: new Date().toISOString()
        });
      }

      state.scanned.push(url);
      completed++;

      scheduleCheckpoint();

      if (DELAY)
        await sleep(DELAY);
    }
  }

  console.log(
    `Starting ${WORKERS} workers | limit=${MAX_PER_RUN} | delay=${DELAY}ms`
  );

  await Promise.all(
    Array.from(
      { length: WORKERS },
      (_, i) => worker(i + 1)
    )
  );

  if (checkpointTimer) {
    clearTimeout(checkpointTimer);
    checkpointTimer = null;
  }

  saveCheckpoint(state);
  saveReport(state);

  console.log("\n=== MEDIA MAP ===");
  console.log(
    "Discovered:",
    state.seen.length
  );
  console.log(
    "Scanned:",
    state.scanned.length
  );
  console.log(
    "Media links:",
    state.mediaLinks.length
  );
  console.log(
    "Failures:",
    state.failures.length
  );

  console.log(
    "\nSaved:",
    OUTPUT
  );

  const remaining =
    state.seen.filter(
      url => !scanned.has(url)
    ).length;

  console.log(
    "Remaining:",
    remaining
  );

  if (remaining > 0) {
    console.log(
      "\nMore pages remain. Run the command again to resume."
    );
  } else {
    console.log(
      "\nCrawl complete."
    );
  }
}
main().catch(error => {
  console.error(error);
  process.exit(1);
});
