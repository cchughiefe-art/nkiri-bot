#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f package.json ] || [ ! -f src/providers/moviex.js ]; then
  echo "Run this from the nkiri-bot repository root."
  exit 1
fi

python - <<'PY'
from pathlib import Path
import json

p = Path('package.json')
data = json.loads(p.read_text())
scripts = data.setdefault('scripts', {})
scripts.setdefault('api', 'node src/api/server.js')
p.write_text(json.dumps(data, indent=2) + '\n')

moviex = Path('src/providers/moviex.js')
s = moviex.read_text()
s = s.replace(
    'const timeout = setTimeout(() => controller.abort(), 15000);',
    'const timeout = setTimeout(() => controller.abort(), 30000);',
    1
)
moviex.write_text(s)
PY

node --check src/api/server.js
node --check src/providers/moviex.js

echo "✓ TheNkiri API starter installed"
echo "✓ npm run api added"
echo "✓ MovieX timeout set to 30 seconds when the old 15s line was present"
echo ""
echo "Next: npm run api"
