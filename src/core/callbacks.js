const crypto = require("crypto");

const callbacks =
  new Map();

const TTL =
  2 * 60 * 60 * 1000;

function create(
  type,
  payload
) {
  const id =
    crypto
      .randomBytes(5)
      .toString("hex");

  callbacks.set(id, {
    type,
    payload,
    createdAt: Date.now()
  });

  return `${type}:${id}`;
}

function get(
  callbackData,
  expectedType = null
) {
  const [
    type,
    id
  ] =
    String(callbackData)
      .split(":");

  if (!type || !id) {
    return null;
  }

  if (
    expectedType &&
    type !== expectedType
  ) {
    return null;
  }

  const item =
    callbacks.get(id);

  if (!item) {
    return null;
  }

  if (
    Date.now() -
      item.createdAt >
    TTL
  ) {
    callbacks.delete(id);
    return null;
  }

  return item.payload;
}

setInterval(() => {
  const cutoff =
    Date.now() - TTL;

  for (
    const [id, item]
    of callbacks
  ) {
    if (
      item.createdAt < cutoff
    ) {
      callbacks.delete(id);
    }
  }
}, 15 * 60 * 1000);

module.exports = {
  create,
  get
};
