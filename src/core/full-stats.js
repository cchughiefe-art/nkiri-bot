require("dotenv").config();

const fs = require("fs");
const path = require("path");

const DATA_DIR =
  process.env.DATA_DIR ||
  path.join(process.cwd(), "data");

const STORE_FILE =
  path.join(DATA_DIR, "store.json");

const UX_FILE =
  path.join(DATA_DIR, "ux.json");

const APP_TIMEZONE =
  process.env.APP_TIMEZONE ||
  "Africa/Lagos";

const REQUIRED_CHANNEL =
  process.env.REQUIRED_CHANNEL ||
  "@voidupdatezone";

const SHARE_MODE =
  String(
    process.env.SHARE_MODE ||
    "daily"
  ).toLowerCase();

const BACKUP_ENABLED =
  String(
    process.env.BACKUP_ENABLED ||
    "true"
  ).toLowerCase() !== "false";

function readJson(file, fallback) {
  try {
    return JSON.parse(
      fs.readFileSync(file, "utf8")
    );
  } catch {
    return fallback;
  }
}

function formatUserName(user = {}) {
  return [
    user.firstName,
    user.lastName
  ]
    .filter(Boolean)
    .join(" ") || "No name";
}

function todayKey() {
  const parts =
    new Intl.DateTimeFormat(
      "en-GB",
      {
        timeZone: APP_TIMEZONE,
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
      }
    ).formatToParts(new Date());

  const out = {};

  for (const part of parts) {
    out[part.type] = part.value;
  }

  return (
    `${out.year}-` +
    `${out.month}-` +
    `${out.day}`
  );
}

function buildFullStats() {
  const core =
    readJson(
      STORE_FILE,
      {
        cache: {},
        brokenLinks: {},
        reports: [],
        users: {},
        downloads: [],
        stats: {}
      }
    );

  const ux =
    readJson(
      UX_FILE,
      {
        users: {},
        maintenance: false,
        lastBackupDate: null,
        metrics: {},
        broadcastHistory: []
      }
    );

  const coreUsers =
    Object.values(
      core.users || {}
    );

  const uxById =
    ux.users || {};

  const uxUsers =
    Object.values(uxById);

  const downloads =
    Array.isArray(core.downloads)
      ? core.downloads
      : [];

  const reports =
    Array.isArray(core.reports)
      ? core.reports
      : [];

  const brokenLinks =
    core.brokenLinks || {};

  const cache =
    core.cache || {};

  const metrics =
    ux.metrics || {};

  const broadcasts =
    Array.isArray(
      ux.broadcastHistory
    )
      ? ux.broadcastHistory
      : [];

  const now = Date.now();
  const hour =
    60 * 60 * 1000;
  const day =
    24 * hour;

  const n = value =>
    Number(value || 0);

  const num = value =>
    n(value)
      .toLocaleString(
        "en-US"
      );

  const pct =
    (part, total) =>
      total > 0
        ? (
            (
              part /
              total
            ) *
            100
          ).toFixed(1)
        : "0.0";

  const fileSize =
    file => {
      try {
        const bytes =
          fs.statSync(file)
            .size;

        if (bytes < 1024) {
          return `${bytes} B`;
        }

        if (
          bytes <
          1024 * 1024
        ) {
          return (
            (
              bytes /
              1024
            ).toFixed(1) +
            " KB"
          );
        }

        return (
          (
            bytes /
            1024 /
            1024
          ).toFixed(2) +
          " MB"
        );
      } catch {
        return "Unavailable";
      }
    };

  const fmtDate =
    value => {
      if (!value) {
        return "Never";
      }

      try {
        return new Intl
          .DateTimeFormat(
            "en-GB",
            {
              timeZone:
                APP_TIMEZONE,
              year:
                "numeric",
              month:
                "short",
              day:
                "2-digit",
              hour:
                "2-digit",
              minute:
                "2-digit",
              hourCycle:
                "h23"
            }
          )
          .format(
            new Date(value)
          );
      } catch {
        return new Date(
          value
        ).toISOString();
      }
    };

  const fmtUptime =
    seconds => {
      let s =
        Math.max(
          0,
          Math.floor(
            seconds
          )
        );

      const d =
        Math.floor(
          s / 86400
        );

      s %= 86400;

      const h =
        Math.floor(
          s / 3600
        );

      s %= 3600;

      const m =
        Math.floor(
          s / 60
        );

      return (
        `${d}d ` +
        `${h}h ` +
        `${m}m`
      );
    };

  const topMap =
    (
      items,
      keyFn,
      limit = 8
    ) => {
      const counts =
        new Map();

      for (
        const item
        of items
      ) {
        const key =
          keyFn(item);

        if (!key) {
          continue;
        }

        counts.set(
          key,
          (
            counts.get(key) ||
            0
          ) + 1
        );
      }

      return [
        ...counts.entries()
      ]
        .sort(
          (a, b) =>
            b[1] -
            a[1]
        )
        .slice(
          0,
          limit
        );
    };

  /*
   * Merge core users and
   * UX users.
   */
  const merged =
    new Map();

  for (
    const user
    of coreUsers
  ) {
    if (!user?.id) {
      continue;
    }

    merged.set(
      String(user.id),
      { ...user }
    );
  }

  for (
    const user
    of uxUsers
  ) {
    if (!user?.id) {
      continue;
    }

    const key =
      String(user.id);

    const old =
      merged.get(key) ||
      {};

    merged.set(
      key,
      {
        ...old,
        ...user,
        id:
          user.id ||
          old.id,
        firstSeenAt:
          old.firstSeenAt ||
          user.firstSeenAt ||
          null,
        lastSeenAt:
          Math.max(
            n(
              old.lastSeenAt
            ),
            n(
              user.lastSeenAt
            )
          ) ||
          null,
        searches:
          n(
            old.searches
          ),
        downloads:
          n(
            old.downloads
          ),
        reports:
          n(
            old.reports
          )
      }
    );
  }

  const users =
    [...merged.values()];

  /*
   * User activity.
   */
  const active1h =
    users.filter(
      u =>
        u.lastSeenAt &&
        now -
          u.lastSeenAt <=
          hour
    ).length;

  const active24h =
    users.filter(
      u =>
        u.lastSeenAt &&
        now -
          u.lastSeenAt <=
          day
    ).length;

  const active7d =
    users.filter(
      u =>
        u.lastSeenAt &&
        now -
          u.lastSeenAt <=
          7 * day
    ).length;

  const active30d =
    users.filter(
      u =>
        u.lastSeenAt &&
        now -
          u.lastSeenAt <=
          30 * day
    ).length;

  const new24h =
    users.filter(
      u =>
        u.firstSeenAt &&
        now -
          u.firstSeenAt <=
          day
    ).length;

  const new7d =
    users.filter(
      u =>
        u.firstSeenAt &&
        now -
          u.firstSeenAt <=
          7 * day
    ).length;

  const unused =
    users.filter(
      u =>
        n(u.searches) ===
          0 &&
        n(u.downloads) ===
          0 &&
        n(u.reports) ===
          0
    ).length;

  const searchedNoDownload =
    users.filter(
      u =>
        n(u.searches) >
          0 &&
        n(u.downloads) ===
          0
    ).length;

  const downloaders =
    users.filter(
      u =>
        n(
          u.downloads
        ) > 0
    ).length;

  const repeatDownloaders =
    users.filter(
      u =>
        n(
          u.downloads
        ) >= 2
    ).length;

  const reporters =
    users.filter(
      u =>
        n(
          u.reports
        ) > 0
    ).length;

  /*
   * Devices/onboarding.
   */
  const android =
    uxUsers.filter(
      u =>
        u.device ===
        "android"
    ).length;

  const ios =
    uxUsers.filter(
      u =>
        u.device ===
        "ios"
    ).length;

  const deviceUnknown =
    Math.max(
      0,
      users.length -
        android -
        ios
    );

  const verified =
    uxUsers.filter(
      u =>
        Boolean(
          u.channelVerifiedAt
        )
    ).length;

  const sharedToday =
    uxUsers.filter(
      u =>
        u.lastShareDate ===
        todayKey()
    ).length;

  const referredUsers =
    uxUsers.filter(
      u =>
        Boolean(
          u.referrerId
        )
    ).length;

  const resumableUsers =
    uxUsers.filter(
      u =>
        Boolean(
          u.lastProgress
            ?.kind &&
          u.lastProgress
            ?.payload
        )
    ).length;

  /*
   * Downloads.
   */
  const lifetimeDownloads =
    Math.max(
      n(
        core.stats
          ?.downloads
      ),
      downloads.length
    );

  const failedDownloads =
    n(
      core.stats
        ?.failedDownloads
    );

  const attempts =
    lifetimeDownloads +
    failedDownloads;

  const downloads24h =
    downloads.filter(
      d =>
        d.createdAt &&
        now -
          d.createdAt <=
          day
    ).length;

  const downloads7d =
    downloads.filter(
      d =>
        d.createdAt &&
        now -
          d.createdAt <=
          7 * day
    ).length;

  const movieDownloads =
    downloads.filter(
      d =>
        d.type ===
        "movie"
    ).length;

  const episodeDownloads =
    downloads.filter(
      d =>
        d.type ===
        "episode"
    ).length;

  const batchDownloads =
    downloads.filter(
      d =>
        d.batch ===
        true
    ).length;

  const providerCounts =
    topMap(
      downloads,
      d =>
        d.provider ||
        "Unknown",
      10
    );

  const titleCounts =
    topMap(
      downloads,
      d =>
        d.title ||
        "Unknown",
      10
    );

  /*
   * Reports/errors.
   */
  const searches =
    n(
      core.stats
        ?.searches
    );

  const openReports =
    reports.filter(
      r =>
        !r.status ||
        r.status ===
          "open"
    ).length;

  const closedReports =
    reports.filter(
      r =>
        [
          "closed",
          "resolved"
        ].includes(
          String(
            r.status ||
            ""
          ).toLowerCase()
        )
    ).length;

  const reportCategories =
    topMap(
      reports,
      r =>
        r.categoryKey ||
        r.category ||
        "Other",
      8
    );

  const brokenEntries =
    Object.values(
      brokenLinks
    );

  const brokenReportCount =
    brokenEntries.reduce(
      (
        sum,
        item
      ) =>
        sum +
        n(
          item?.count
        ),
      0
    );

  /*
   * Access gate.
   */
  const membershipChecks =
    n(
      metrics
        .membershipChecks
    );

  const membershipFailures =
    n(
      metrics
        .membershipFailures
    );

  const membershipPasses =
    Math.max(
      0,
      membershipChecks -
        membershipFailures
    );

  const referralCount =
    Math.max(
      n(
        metrics
          .referrals
      ),
      referredUsers
    );

  const topReferrers =
    [...uxUsers]
      .filter(
        u =>
          n(
            u.referrals
          ) > 0
      )
      .sort(
        (a, b) =>
          n(
            b.referrals
          ) -
          n(
            a.referrals
          )
      )
      .slice(
        0,
        8
      );

  /*
   * Broadcasts.
   */
  const deliveredHistory =
    broadcasts.reduce(
      (
        sum,
        b
      ) =>
        sum +
        n(
          b.delivered
        ),
      0
    );

  const blockedHistory =
    broadcasts.reduce(
      (
        sum,
        b
      ) =>
        sum +
        n(
          b.blocked
        ),
      0
    );

  const failedHistory =
    broadcasts.reduce(
      (
        sum,
        b
      ) =>
        sum +
        n(
          b.failed
        ),
      0
    );

  const targetedHistory =
    broadcasts.reduce(
      (
        sum,
        b
      ) =>
        sum +
        n(
          b.targeted
        ),
      0
    );

  const broadcastOptOut =
    uxUsers.filter(
      u =>
        u.broadcastOptOut ===
        true
    ).length;

  const lastBroadcast =
    broadcasts.at(-1);

  /*
   * Cache/system.
   */
  const activeCache =
    Object.values(
      cache
    ).filter(
      item =>
        !item?.expiresAt ||
        item.expiresAt >
          now
    ).length;

  const expiredCache =
    Math.max(
      0,
      Object.keys(
        cache
      ).length -
        activeCache
    );

  const memory =
    process.memoryUsage();

  const uptime =
    process.uptime();

  const startTime =
    now -
    uptime * 1000;

  const crashProtection =
    process.listenerCount(
      "unhandledRejection"
    ) > 0 &&
    process.listenerCount(
      "uncaughtException"
    ) > 0;

  const topUsers =
    [...users]
      .sort(
        (a, b) =>
          n(
            b.downloads
          ) -
            n(
              a.downloads
            ) ||
          n(
            b.searches
          ) -
            n(
              a.searches
            )
      )
      .slice(
        0,
        8
      );

  const recentUsers =
    [...users]
      .sort(
        (a, b) =>
          n(
            b.lastSeenAt
          ) -
          n(
            a.lastSeenAt
          )
      )
      .slice(
        0,
        10
      );

  const recentDownloads =
    [...downloads]
      .sort(
        (a, b) =>
          n(
            b.createdAt
          ) -
          n(
            a.createdAt
          )
      )
      .slice(
        0,
        10
      );

  const lines = [];

  lines.push(
    "📊 THE NKIRI — FULL BOT STATISTICS"
  );

  lines.push(
    `Generated: ${fmtDate(now)} (${APP_TIMEZONE})`
  );

  lines.push(
    "\n👥 USERS & GROWTH"
  );

  lines.push(
    `Total known users: ${num(users.length)}`
  );

  lines.push(
    `Active 1h: ${num(active1h)} | ` +
    `24h: ${num(active24h)} | ` +
    `7d: ${num(active7d)} | ` +
    `30d: ${num(active30d)}`
  );

  lines.push(
    `New users 24h: ${num(new24h)} | ` +
    `7d: ${num(new7d)}`
  );

  lines.push(
    `No activity yet: ${num(unused)}`
  );

  lines.push(
    `Searched, no download: ${num(searchedNoDownload)}`
  );

  lines.push(
    `Downloaded ≥1: ${num(downloaders)} ` +
    `(${pct(downloaders, users.length)}%)`
  );

  lines.push(
    `Downloaded ≥2: ${num(repeatDownloaders)}`
  );

  lines.push(
    `Reported a problem: ${num(reporters)}`
  );

  lines.push(
    `Avg searches/user: ${
      users.length
        ? (
            searches /
            users.length
          ).toFixed(2)
        : "0.00"
    }`
  );

  lines.push(
    `Avg downloads/user: ${
      users.length
        ? (
            lifetimeDownloads /
            users.length
          ).toFixed(2)
        : "0.00"
    }`
  );

  lines.push(
    "\n🔎 SEARCH"
  );

  lines.push(
    `Lifetime searches: ${num(searches)}`
  );

  lines.push(
    "\n⬇️ DOWNLOADS"
  );

  lines.push(
    `Lifetime successful: ${num(lifetimeDownloads)}`
  );

  lines.push(
    `Retained download history: ${num(downloads.length)}`
  );

  lines.push(
    `Successful 24h: ${num(downloads24h)} | ` +
    `7d: ${num(downloads7d)}`
  );

  lines.push(
    `Failed: ${num(failedDownloads)}`
  );

  lines.push(
    `Success rate: ${pct(lifetimeDownloads, attempts)}%`
  );

  lines.push(
    `Movies: ${num(movieDownloads)} | ` +
    `Episodes: ${num(episodeDownloads)} | ` +
    `Batch links: ${num(batchDownloads)}`
  );

  lines.push(
    "Providers:"
  );

  lines.push(
    providerCounts.length
      ? providerCounts
          .map(
            ([k, v]) =>
              `• ${k}: ${num(v)}`
          )
          .join("\n")
      : "• No provider data yet"
  );

  lines.push(
    "\n🏆 MOST DOWNLOADED TITLES"
  );

  lines.push(
    titleCounts.length
      ? titleCounts
          .map(
            (
              [k, v],
              i
            ) =>
              `${i + 1}. ${k} — ${num(v)}`
          )
          .join("\n")
      : "No downloads yet."
  );

  lines.push(
    "\n📱 DEVICES & ONBOARDING"
  );

  lines.push(
    `Android: ${num(android)} | ` +
    `iPhone/iPad: ${num(ios)} | ` +
    `Unknown: ${num(deviceUnknown)}`
  );

  lines.push(
    `Starts tracked: ${num(metrics.starts)}`
  );

  lines.push(
    `Device selections: ${num(metrics.deviceSelections)}`
  );

  lines.push(
    `Users with resumable progress: ${num(resumableUsers)}`
  );

  lines.push(
    "\n🔒 CHANNEL / SHARE GATE"
  );

  lines.push(
    `Channel: ${REQUIRED_CHANNEL || "Disabled"}`
  );

  lines.push(
    `Users ever verified: ${num(verified)}`
  );

  lines.push(
    `Membership checks: ${num(membershipChecks)}`
  );

  lines.push(
    `Passed: ${num(membershipPasses)} | ` +
    `Failed: ${num(membershipFailures)} ` +
    `(${pct(membershipFailures, membershipChecks)}%)`
  );

  lines.push(
    `Share mode: ${SHARE_MODE}`
  );

  lines.push(
    `Shares confirmed lifetime: ${num(metrics.sharesConfirmed)}`
  );

  lines.push(
    `Shared today: ${num(sharedToday)}`
  );

  lines.push(
    "\n🔗 REFERRALS"
  );

  lines.push(
    `Referral starts: ${num(referralCount)}`
  );

  lines.push(
    `Users with a referrer: ${num(referredUsers)}`
  );

  lines.push(
    topReferrers.length
      ? topReferrers
          .map(
            (
              u,
              i
            ) =>
              `${i + 1}. ${
                u.username
                  ? "@" +
                    u.username
                  : "ID " +
                    u.id
              } — ${num(u.referrals)}`
          )
          .join("\n")
      : "No referrals yet."
  );

  lines.push(
    "\n⚠️ REPORTS & BROKEN LINKS"
  );

  lines.push(
    `Stored reports: ${num(reports.length)} | ` +
    `Open: ${num(openReports)} | ` +
    `Resolved/closed: ${num(closedReports)}`
  );

  lines.push(
    `Broken URLs tracked: ${num(brokenEntries.length)}`
  );

  lines.push(
    `Broken-link failure reports: ${num(brokenReportCount)}`
  );

  lines.push(
    "Report categories:"
  );

  lines.push(
    reportCategories.length
      ? reportCategories
          .map(
            ([k, v]) =>
              `• ${k}: ${num(v)}`
          )
          .join("\n")
      : "• No reports yet"
  );

  lines.push(
    "\n📣 BROADCAST CENTER"
  );

  lines.push(
    `Lifetime broadcasts: ${num(
      metrics.broadcastsSent ||
      broadcasts.length
    )}`
  );

  lines.push(
    `Lifetime delivered: ${num(
      metrics.broadcastDelivered ||
      deliveredHistory
    )}`
  );

  lines.push(
    `Lifetime blocked: ${num(
      metrics.broadcastBlocked ||
      blockedHistory
    )}`
  );

  lines.push(
    `Lifetime failed: ${num(
      metrics.broadcastFailed ||
      failedHistory
    )}`
  );

  lines.push(
    `Retained history: ${num(broadcasts.length)} broadcasts`
  );

  lines.push(
    `Retained targeted: ${num(targetedHistory)} | ` +
    `delivered: ${num(deliveredHistory)} ` +
    `(${pct(deliveredHistory, targetedHistory)}%)`
  );

  lines.push(
    `Notifications muted: ${num(broadcastOptOut)}`
  );

  lines.push(
    lastBroadcast
      ? `Last: ${
          lastBroadcast
            .audienceLabel ||
          lastBroadcast
            .audience ||
          "Unknown audience"
        } • ${
          lastBroadcast
            .kind ||
          "message"
        } • ${num(
          lastBroadcast
            .delivered
        )}/${num(
          lastBroadcast
            .targeted
        )} delivered • ${
          fmtDate(
            lastBroadcast
              .createdAt
          )
        }`
      : "Last broadcast: None"
  );

  lines.push(
    "\n🛠 SYSTEM / HEALTH"
  );

  lines.push(
    `Maintenance: ${
      ux.maintenance
        ? "ON"
        : "OFF"
    }`
  );

  lines.push(
    `Crash protection: ${
      crashProtection
        ? "ACTIVE"
        : "NOT DETECTED"
    }`
  );

  lines.push(
    `Process uptime: ${fmtUptime(uptime)}`
  );

  lines.push(
    `Process started: ${fmtDate(startTime)}`
  );

  lines.push(
    `Node: ${process.version} | PID: ${process.pid}`
  );

  lines.push(
    `RSS memory: ${
      (
        memory.rss /
        1024 /
        1024
      ).toFixed(1)
    } MB`
  );

  lines.push(
    `Heap: ${
      (
        memory.heapUsed /
        1024 /
        1024
      ).toFixed(1)
    } / ${
      (
        memory.heapTotal /
        1024 /
        1024
      ).toFixed(1)
    } MB`
  );

  lines.push(
    `Health port: ${
      process.env.PORT ||
      8080
    }`
  );

  lines.push(
    `Backup enabled: ${
      BACKUP_ENABLED
        ? "YES"
        : "NO"
    }`
  );

  lines.push(
    `Last backup date: ${
      ux.lastBackupDate ||
      "Never"
    }`
  );

  lines.push(
    `store.json: ${fileSize(STORE_FILE)} | ` +
    `ux.json: ${fileSize(UX_FILE)}`
  );

  lines.push(
    `Cache entries: ${num(Object.keys(cache).length)} ` +
    `(${num(activeCache)} active, ${num(expiredCache)} expired)`
  );

  lines.push(
    "\n👑 TOP USERS BY DOWNLOADS"
  );

  lines.push(
    topUsers.length
      ? topUsers
          .map(
            (
              u,
              i
            ) =>
              `${i + 1}. ${
                u.username
                  ? "@" +
                    u.username
                  : formatUserName(
                      u
                    )
              } — ⬇️ ${num(u.downloads)} | ` +
              `🔎 ${num(u.searches)} | ` +
              `⚠️ ${num(u.reports)}`
          )
          .join("\n")
      : "No users yet."
  );

  lines.push(
    "\n🕘 RECENT DOWNLOADS"
  );

  lines.push(
    recentDownloads.length
      ? recentDownloads
          .map(
            d => {
              const ep =
                d.episode
                  ? ` • ${d.episode}`
                  : "";

              const user =
                d.username
                  ? `@${d.username}`
                  : d.userId
                    ? `ID ${d.userId}`
                    : "Unknown user";

              return (
                `• ${
                  d.title ||
                  "Unknown"
                }${ep}\n` +
                `  ${user} • ${
                  d.provider ||
                  "Unknown provider"
                } • ${
                  fmtDate(
                    d.createdAt
                  )
                }`
              );
            }
          )
          .join("\n")
      : "No downloads yet."
  );

  lines.push(
    "\n👥 RECENT USERS"
  );

  lines.push(
    recentUsers.length
      ? recentUsers
          .map(
            u => {
              const uxUser =
                uxById[
                  String(
                    u.id
                  )
                ] || {};

              const device =
                uxUser.device ===
                "android"
                  ? "Android"
                  : uxUser.device ===
                      "ios"
                    ? "iPhone"
                    : "Unknown";

              return (
                `• ${formatUserName(u)} ${
                  u.username
                    ? "@" +
                      u.username
                    : ""
                }\n` +
                `  ID ${u.id} • ${device} • ` +
                `🔎 ${num(u.searches)} • ` +
                `⬇️ ${num(u.downloads)} • ` +
                `⚠️ ${num(u.reports)} • ` +
                `${fmtDate(u.lastSeenAt)}`
              );
            }
          )
          .join("\n")
      : "No users yet."
  );

  return lines.join("\n");
}

module.exports = {
  buildFullStats
};
