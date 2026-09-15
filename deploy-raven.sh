#!/data/data/com.termux/files/usr/bin/bash
set -e

HOST="node.ravenhost.space"
PORT="2022"
USER="heyits_void.b248c90a"
REMOTE="/home/container"

echo "Running syntax checks..."

node --check src/bot.js
node --check src/nkiri/search.js
node --check src/nkiri/discover.js

echo
echo "Pushing v2 to GitHub..."

git add -A

if ! git diff --cached --quiet; then
  git commit -m "Update TheNkiri bot"
fi

git push origin v2

echo
echo "Uploading changed runtime files to Raven..."
echo "Enter your Raven SFTP password when asked."
echo

sftp -P "$PORT" \
  "$USER@$HOST" <<EOF2
cd $REMOTE
put launcher.ts
put package.json
put package-lock.json
put src/bot.js
put src/nkiri/search.js
put src/nkiri/discover.js
bye
EOF2

echo
echo "================================"
echo "✓ GitHub updated"
echo "✓ Raven files uploaded"
echo "================================"
echo
echo "Now press Restart in Raven."
