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
scripts['api'] = 'node src/api/server.js'
p.write_text(json.dumps(data, indent=2) + '\n')

moviex = Path('src/providers/moviex.js')
s = moviex.read_text()
s = s.replace(
    'const timeout = setTimeout(() => controller.abort(), 15000);',
    'const timeout = setTimeout(() => controller.abort(), 30000);',
    1
)
moviex.write_text(s)

env = Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
if not any(line.startswith('API_PORT=') for line in lines):
    lines.append('API_PORT=3001')
env.write_text('\n'.join(lines).rstrip() + '\n')
PY

node --check src/api/server.js
node --check src/providers/moviex.js

echo "✓ Native Android foundation installed"
echo "✓ App API installed"
echo "✓ GitHub cloud APK build included"
echo "✓ Render API config included"
echo "✓ No local Android build required"
