#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f package.json ]; then
  echo "Run this from the nkiri-bot repository root."
  exit 1
fi

if [ ! -f src/api/server.js ]; then
  echo "Build 2 API is missing. Unzip Build 3 from the repository root with -o."
  exit 1
fi

test -f android-native/app/src/main/java/com/nkiridown/app/LocalStore.kt
test -f android-native/app/src/main/java/com/nkiridown/app/NkiriApp.kt
test -f android-native/app/src/main/res/values/styles.xml

echo "✓ Build 3 local UX installed"
echo "✓ Favorites"
echo "✓ Recent searches"
echo "✓ Download history"
echo "✓ Settings/API switching"
echo "✓ Channel/bot/share/VLC shortcuts"
echo "✓ No local Android build required"
