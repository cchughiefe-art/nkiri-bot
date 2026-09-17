#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f package.json ]; then
  echo "Run this from the nkiri-bot repository root."
  exit 1
fi

python - <<'PY'
from pathlib import Path
import json

package = Path("package.json")
data = json.loads(package.read_text())

scripts = data.setdefault("scripts", {})
scripts["api"] = "node src/api/server.js"

package.write_text(
    json.dumps(data, indent=2) + "\n"
)

moviex = Path("src/providers/moviex.js")
if moviex.exists():
    text = moviex.read_text()
    text = text.replace(
        "const timeout = setTimeout(() => controller.abort(), 15000);",
        "const timeout = setTimeout(() => controller.abort(), 30000);",
        1
    )
    moviex.write_text(text)

env = Path(".env")
lines = env.read_text().splitlines() if env.exists() else []

wanted = {
    "API_PORT": "3001",
    "APP_LATEST_VERSION_CODE": "1",
    "APP_LATEST_VERSION_NAME": "1.0.0",
    "APP_FORCE_UPDATE": "false",
    "APP_CHANNEL_URL": "https://t.me/voidupdatezone",
    "APP_BOT_URL": "https://t.me/nkiridownbot",
}

present = {
    line.split("=", 1)[0]
    for line in lines
    if "=" in line and not line.lstrip().startswith("#")
}

for key, value in wanted.items():
    if key not in present:
        lines.append(f"{key}={value}")

env.write_text(
    "\n".join(lines).rstrip() + "\n"
)
PY

node --check src/api/server.js
node --check src/providers/moviex.js

echo "✓ Final native source installed"
echo "✓ API script configured"
echo "✓ Android source ready for GitHub cloud build"
echo "✓ No local Gradle/Expo build is required"
