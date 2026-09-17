#!/data/data/com.termux/files/usr/bin/bash
set -e

cd ~/nkiri-bot

test -d .git || {
  echo "nkiri-bot repo not found at ~/nkiri-bot"
  exit 1
}

git checkout app-v1

mkdir -p .github/workflows

cp ~/storage/downloads/nkiri-build-hotfix/.github/workflows/build-android-apk.yml \
  .github/workflows/build-android-apk.yml

git add .github/workflows/build-android-apk.yml
git commit -m "Fix Android build workflow conditions"
git push origin app-v1

echo
echo "✓ Hotfix pushed"
echo "GitHub Actions should start the Android build automatically."
