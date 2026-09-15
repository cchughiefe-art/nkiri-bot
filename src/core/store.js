const fs = require("fs");
const path = require("path");

const DATA_DIR =
  process.env.DATA_DIR ||
  path.join(process.cwd(), "data");

const STORE_FILE =
  path.join(DATA_DIR, "store.json");

const EMPTY = {
  cache: {},
  brokenLinks: {},
  stats: {
    searches: 0,
    downloads: 0,
    failedDownloads: 0
  }
};

function ensureStore() {
  fs.mkdirSync(DATA_DIR, {
    recursive: true
  });

  if (!fs.existsSync(STORE_FILE)) {
    fs.writeFileSync(
      STORE_FILE,
      JSON.stringify(EMPTY, null, 2)
    );
  }
}

function readStore() {
  ensureStore();

  try {
    return JSON.parse(
      fs.readFileSync(
        STORE_FILE,
        "utf8"
      )
    );
  } catch {
    return structuredClone(EMPTY);
  }
}

function writeStore(data) {
  ensureStore();

  const temp =
    `${STORE_FILE}.tmp`;

  fs.writeFileSync(
    temp,
    JSON.stringify(data, null, 2)
  );

  fs.renameSync(
    temp,
    STORE_FILE
  );
}

function getCache(key) {
  const data = readStore();
  const item = data.cache?.[key];

  if (!item) return null;

  if (
    item.expiresAt &&
    Date.now() > item.expiresAt
  ) {
    delete data.cache[key];
    writeStore(data);
    return null;
  }

  return item.value;
}

function setCache(
  key,
  value,
  ttlMs = 3600000
) {
  const data = readStore();

  data.cache ||= {};

  data.cache[key] = {
    value,
    createdAt: Date.now(),
    expiresAt:
      ttlMs > 0
        ? Date.now() + ttlMs
        : null
  };

  writeStore(data);

  return value;
}

function incrementStat(name) {
  const data = readStore();

  data.stats ||= {};
  data.stats[name] =
    (data.stats[name] || 0) + 1;

  writeStore(data);
}

function reportBroken(
  url,
  details = {}
) {
  const data = readStore();

  data.brokenLinks ||= {};

  const current =
    data.brokenLinks[url] || {
      count: 0
    };

  data.brokenLinks[url] = {
    ...current,
    ...details,
    count:
      (current.count || 0) + 1,
    lastReportedAt:
      Date.now()
  };

  writeStore(data);
}

function cleanup() {
  const data = readStore();
  const now = Date.now();

  for (
    const [key, item]
    of Object.entries(
      data.cache || {}
    )
  ) {
    if (
      item.expiresAt &&
      now > item.expiresAt
    ) {
      delete data.cache[key];
    }
  }

  writeStore(data);
}

module.exports = {
  getCache,
  setCache,
  incrementStat,
  reportBroken,
  cleanup
};
