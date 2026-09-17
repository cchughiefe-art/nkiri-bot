#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f "launcher.ts" ] || [ ! -f "src/bot.js" ]; then
  echo "Run this from ~/nkiri-bot"
  exit 1
fi

mkdir -p src/core
cp launcher.ts launcher.ts.before-batch
cp nkiri-batch-downloads/src/core/batch-downloads.js src/core/batch-downloads.js

python - <<'PY'
from pathlib import Path
p = Path("launcher.ts")
s = p.read_text()
line = 'require("./src/core/batch-downloads.js");'
if line not in s:
    anchor = 'require("./src/core/launch-upgrades.js");'
    if anchor not in s:
        raise SystemExit("Could not find launch-upgrades require in launcher.ts")
    s = s.replace(anchor, anchor + "\n" + line, 1)
p.write_text(s)
print("✓ launcher.ts updated")
PY

node --check src/core/batch-downloads.js
node --check src/core/launch-upgrades.js
node --check src/bot.js

echo "✓ Batch downloads installed"
echo "✓ Full Season Links added automatically"
echo "✓ Select Episodes added automatically"
echo "✓ Existing daily-share/channel gate preserved"
echo "✓ .env was NOT changed"
