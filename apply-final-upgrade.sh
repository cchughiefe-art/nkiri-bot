#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f package.json ] || [ ! -f src/bot.js ]; then
  echo "Run this from the nkiri-bot repository root."
  exit 1
fi

python - <<'PY'
from pathlib import Path
p = Path('.env')
lines = p.read_text().splitlines() if p.exists() else []
updates = {
    'REQUIRED_CHANNEL': '@voidupdatezone',
    'REQUIRED_CHANNEL_URL': 'https://t.me/voidupdatezone',
    'SHARE_MODE': 'daily',
    'APP_TIMEZONE': 'Africa/Lagos',
    'BACKUP_ENABLED': 'true',
    'MAINTENANCE_MODE': 'false',
}
out=[]
seen=set()
for line in lines:
    if '=' in line and not line.lstrip().startswith('#'):
        key=line.split('=',1)[0].strip()
        if key in updates:
            out.append(f'{key}={updates[key]}')
            seen.add(key)
            continue
    out.append(line)
for key,val in updates.items():
    if key not in seen:
        out.append(f'{key}={val}')
p.write_text('\n'.join(out).rstrip()+'\n')
PY

node --check src/core/launch-upgrades.js
node --check src/bot.js

echo "✓ Final launch upgrades installed"
echo "✓ Existing TELEGRAM_BOT_TOKEN and admin secrets preserved"
echo "✓ Channel: @voidupdatezone"
echo "✓ Daily share mode enabled"
echo "✓ Daily admin backups enabled"
echo ""
echo "Next: git add launcher.ts src/core/launch-upgrades.js && git commit && git push origin v2"
