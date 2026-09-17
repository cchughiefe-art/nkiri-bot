#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/nkiri-bot
git checkout app-v1

cp -f nkiri-v2-complete/android-native/app/build.gradle.kts android-native/app/build.gradle.kts
cp -f nkiri-v2-complete/android-native/app/src/main/AndroidManifest.xml android-native/app/src/main/AndroidManifest.xml
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/Models.kt android-native/app/src/main/java/com/nkiridown/app/Models.kt
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/PlaybackStore.kt android-native/app/src/main/java/com/nkiridown/app/PlaybackStore.kt
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/PlayerActivity.kt android-native/app/src/main/java/com/nkiridown/app/PlayerActivity.kt
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/MainActivity.kt android-native/app/src/main/java/com/nkiridown/app/MainActivity.kt
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/MainViewModel.kt android-native/app/src/main/java/com/nkiridown/app/MainViewModel.kt
cp -f nkiri-v2-complete/android-native/app/src/main/java/com/nkiridown/app/NkiriV2App.kt android-native/app/src/main/java/com/nkiridown/app/NkiriV2App.kt

python - <<'PY'
from pathlib import Path

p = Path("src/api/server.js")
s = p.read_text()

if 'external: Boolean(source.external)' not in s:
    s = s.replace(
        '    format: source.format || "mp4",\n    url: source.directUrl || source.downloadUrl || source.url || null',
        '    format: source.format || "mp4",\n    url: source.directUrl || source.downloadUrl || source.url || null,\n    type: source.type || "direct",\n    external: Boolean(source.external),\n    pageUrl: source.pageUrl || null'
    )

if 'external: Boolean(resolved.external)' not in s:
    s = s.replace(
        '    format: resolved.format || "mp4",\n    url: resolved.directUrl || resolved.pageUrl || null',
        '    format: resolved.format || "mp4",\n    url: resolved.directUrl || resolved.pageUrl || null,\n    type: resolved.type || "direct",\n    external: Boolean(resolved.external),\n    pageUrl: resolved.pageUrl || null'
    )

p.write_text(s)
PY

node --check src/api/server.js

echo "✓ Complete v2 stack applied"
