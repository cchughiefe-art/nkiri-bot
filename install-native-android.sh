#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f package.json ] || [ ! -f src/api/server.js ]; then
  echo "Run this from the nkiri-bot repository root after installing the API starter."
  exit 1
fi

if [ ! -f android-native/app/build.gradle.kts ]; then
  echo "android-native files are missing. Unzip the package from the repo root."
  exit 1
fi

echo "✓ Native Android project installed"
echo "✓ Expo is no longer required for this app"
echo "✓ Existing mobile/ Expo folder was left untouched"
echo "✓ Default API URL: http://127.0.0.1:3001"
echo ""
echo "Next:"
echo "  git add android-native .github/workflows/build-android-apk.yml"
echo "  git commit -m \"Add native Android app\""
echo "  git push origin HEAD"
echo ""
echo "GitHub Actions will build app-debug.apk."
