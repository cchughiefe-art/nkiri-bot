#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/nkiri-bot
git checkout app-v1

echo "Removing legacy Compose UI that is no longer used..."
rm -f android-native/app/src/main/java/com/nkiridown/app/NkiriApp.kt

echo "Fixing the v2 download snapshot map..."
python - <<'PY'
from pathlib import Path

p = Path("android-native/app/src/main/java/com/nkiridown/app/NkiriV2App.kt")
s = p.read_text()

old = """            snapshots =
                downloads
                    .mapNotNull {
                        record ->

                        queryDownloadSnapshot(
                            context,
                            record.downloadId
                        )?.let {
                            record.downloadId
                                to it
                        }
                    }
                    .toMap()
"""

new = """            snapshots =
                downloads
                    .mapNotNull { record ->
                        val snapshot =
                            queryDownloadSnapshot(
                                context,
                                record.downloadId
                            )

                        if (snapshot != null) {
                            Pair(
                                record.downloadId,
                                snapshot
                            )
                        } else {
                            null
                        }
                    }
                    .toMap()
"""

if old not in s:
    # Accept already-fixed copies.
    if "Pair(" not in s or "val snapshot =" not in s:
        raise SystemExit("Could not find the broken download snapshot block. Stop instead of making a risky edit.")
else:
    s = s.replace(old, new, 1)

p.write_text(s)
print("✓ NkiriV2App.kt fixed")
PY

echo "Checking that the legacy UI is gone..."
test ! -f android-native/app/src/main/java/com/nkiridown/app/NkiriApp.kt

echo "Checking for the exact old compile-breakers..."
if grep -R -n \
  -e 'viewModel.clearError' \
  -e 'viewModel.clearRecentSearches' \
  -e 'viewModel.clearCrashLog' \
  android-native/app/src/main/java/com/nkiridown/app; then
  echo "Legacy ViewModel references still exist. Aborting."
  exit 1
fi

echo "✓ Build-fix checks passed"
