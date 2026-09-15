const users = new Map();

const WINDOW =
  60 * 1000;

const SEARCH_LIMIT = 12;
const DOWNLOAD_LIMIT = 8;

function consume(
  userId,
  type
) {
  const now = Date.now();

  let state =
    users.get(userId);

  if (
    !state ||
    now - state.startedAt > WINDOW
  ) {
    state = {
      startedAt: now,
      search: 0,
      download: 0
    };

    users.set(
      userId,
      state
    );
  }

  const limit =
    type === "download"
      ? DOWNLOAD_LIMIT
      : SEARCH_LIMIT;

  state[type] =
    (state[type] || 0) + 1;

  if (state[type] > limit) {
    return {
      allowed: false,
      retryAfter:
        Math.ceil(
          (
            WINDOW -
            (now - state.startedAt)
          ) / 1000
        )
    };
  }

  return {
    allowed: true
  };
}

setInterval(() => {
  const cutoff =
    Date.now() -
    WINDOW * 2;

  for (
    const [id, state]
    of users
  ) {
    if (
      state.startedAt < cutoff
    ) {
      users.delete(id);
    }
  }
}, WINDOW);

module.exports = {
  consume
};
