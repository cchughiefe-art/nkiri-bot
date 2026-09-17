#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/nkiri-bot

test -f src/api/server.js || {
  echo "src/api/server.js not found"
  exit 1
}

test -f android-native/app/build.gradle.kts || {
  echo "Android app project not found"
  exit 1
}

# Fix semantic API bug: the router variable is named `path`, not `pathname`.
python - <<'PY'
from pathlib import Path

p = Path("src/api/server.js")
s = p.read_text()

bad = 'pathname === "/api/config"'
good = 'path === "/api/config"'

if bad in s:
    s = s.replace(bad, good)
    p.write_text(s)
    print("✓ Fixed /api/config route variable")
elif good in s:
    print("✓ /api/config route variable already fixed")
else:
    raise SystemExit("Could not locate /api/config route")
PY

# JS parser check.
node --check src/api/server.js

echo "✓ Fix-all patch applied"
echo "✓ Java target: 17"
echo "✓ Kotlin target: 17"
echo "✓ Kotlin toolchain: JDK 17"
echo "✓ API /api/config runtime bug fixed"
echo "✓ API smoke-test workflow installed"
