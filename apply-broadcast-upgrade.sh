#!/data/data/com.termux/files/usr/bin/bash
set -e

ROOT="${1:-$PWD}"
cd "$ROOT"

if [ ! -f package.json ] || [ ! -f launcher.ts ] || [ ! -f src/bot.js ]; then
  echo "❌ Run this from the nkiri-bot repository root."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p src/core

# If the ZIP was extracted into the repo, the file is already in place.
# Otherwise copy it from the installer directory.
if [ -f "$SCRIPT_DIR/src/core/broadcast.js" ] && [ "$SCRIPT_DIR/src/core/broadcast.js" != "$ROOT/src/core/broadcast.js" ]; then
  cp "$SCRIPT_DIR/src/core/broadcast.js" src/core/broadcast.js
fi

if [ ! -f src/core/broadcast.js ]; then
  echo "❌ src/core/broadcast.js not found. Extract the ZIP into the repo first."
  exit 1
fi

python - <<'PY'
from pathlib import Path

p = Path("launcher.ts")
s = p.read_text()
line = 'require("./src/core/broadcast.js");'

if line not in s:
    anchor = 'require("./src/core/launch-upgrades.js");'
    if anchor in s:
        s = s.replace(anchor, anchor + "\n" + line, 1)
    else:
        bot = 'require("./src/bot.js");'
        if bot not in s:
            raise SystemExit("❌ Could not find a safe launcher insertion point")
        s = s.replace(bot, line + "\n" + bot, 1)
    p.write_text(s)
    print("✓ launcher.ts updated")
else:
    print("✓ launcher.ts already includes broadcast center")
PY

node --check src/core/broadcast.js
node --check src/bot.js

echo "✓ Broadcast center installed"
echo "✓ /broadcast — audience picker + preview + confirm"
echo "✓ /broadcaststats — delivery statistics"
echo "✓ /cancelbroadcast — cancel draft or stop active send"
echo "✓ /notifications on|off|status — user opt-out"
echo "✓ Existing .env and user data were NOT replaced"
