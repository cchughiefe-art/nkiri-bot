#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/nkiri-bot
git checkout app-v1

echo "Patching version + update UX..."

python - <<'PY'
from pathlib import Path

gradle = Path("android-native/app/build.gradle.kts")
s = gradle.read_text()
s = s.replace('versionCode = 3', 'versionCode = 4')
s = s.replace('versionName = "2.1.0"', 'versionName = "2.2.0"')

if 'applicationId = "com.nkiridown.app"' not in s:
    raise SystemExit("applicationId changed or missing; aborting to protect upgrade compatibility.")

gradle.write_text(s)

ui = Path("android-native/app/src/main/java/com/nkiridown/app/NkiriV2App.kt")
u = ui.read_text()

home_marker = '''        if (
            state.continueWatching
                .isNotEmpty()
        ) {
'''

home_banner = '''        if (state.updateAvailable && !state.updateUrl.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B1E24)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Update available",
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "TheNkiri ${state.latestVersionName ?: "new version"} is ready.",
                                color = Muted
                            )
                        }
                        Button(
                            onClick = {
                                state.updateUrl?.let(onOpenUrl)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent
                            )
                        ) {
                            Text("Update")
                        }
                    }
                }
            }
        }

''' + home_marker

if 'Text("Update available"' not in u:
    if home_marker not in u:
        raise SystemExit("Could not find Home insertion point; aborting instead of making a risky edit.")
    u = u.replace(home_marker, home_banner, 1)

root_marker = '''    Scaffold(
        containerColor = Bg,
'''

dialog = '''    if (
        state.forceUpdate &&
        state.updateAvailable &&
        !state.updateUrl.isNullOrBlank()
    ) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Update required") },
            text = {
                Text(
                    "A newer TheNkiri version is required to continue. Your downloads, favorites and history stay on this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.updateUrl?.let(onOpenUrl)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent
                    )
                ) {
                    Text("Update now")
                }
            }
        )
    }

''' + root_marker

if 'Text("Update required")' not in u:
    if root_marker not in u:
        raise SystemExit("Could not find root Scaffold insertion point; aborting.")
    u = u.replace(root_marker, dialog, 1)

ui.write_text(u)

manifest = Path("android-native/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
if '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />' not in m:
    m = m.replace(
        '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
        '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />'
    )
manifest.write_text(m)

print("✓ Android update UX patched")
print("✓ applicationId remains com.nkiridown.app")
print("✓ versionCode=4, versionName=2.2.0")
PY

echo "Patching GitHub Actions artifact names..."

python - <<'PY'
from pathlib import Path

p = Path(".github/workflows/build-android-apk.yml")
s = p.read_text()

s = s.replace(
    "name: TheNkiri-Android-v1-debug",
    "name: TheNkiri-Android-v2-debug"
)
s = s.replace(
    "name: TheNkiri-Android-v1-release-apk",
    "name: TheNkiri-Android-v2-release-apk"
)
s = s.replace(
    "name: TheNkiri-Android-v1-release-aab",
    "name: TheNkiri-Android-v2-release-aab"
)

p.write_text(s)
print("✓ Workflow artifact names updated")
PY

echo
echo "IMPORTANT:"
echo "Only APKs signed with the SAME permanent release key can update one another."
echo "The workflow already supports ANDROID_KEYSTORE_* secrets."
echo
echo "✓ Updatable-build patch applied"
