#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/nkiri-bot
git checkout app-v1

python - <<'PY'
from pathlib import Path

# Add API source classification to the Android model.
p = Path('android-native/app/src/main/java/com/nkiridown/app/Models.kt')
s = p.read_text()
old = '''data class SourceItem(
    val quality: Int,
    val size: Long,
    val sizeText: String,
    val format: String,
    val url: String?
)'''
new = '''data class SourceItem(
    val quality: Int,
    val size: Long,
    val sizeText: String,
    val format: String,
    val url: String?,
    val type: String = "direct",
    val external: Boolean = false,
    val pageUrl: String? = null
)'''
if old in s:
    s = s.replace(old, new)
elif 'val external: Boolean = false' not in s:
    raise SystemExit('Models.kt SourceItem pattern not found')
p.write_text(s)

# Parse classification fields from API responses.
p = Path('android-native/app/src/main/java/com/nkiridown/app/NkiriApi.kt')
s = p.read_text()
old = '''            url =
                optStringOrNull(
                    "url"
                )
        )'''
new = '''            url =
                optStringOrNull(
                    "url"
                ),
            type =
                optString(
                    "type",
                    "direct"
                ),
            external =
                optBoolean(
                    "external",
                    false
                ),
            pageUrl =
                optStringOrNull(
                    "pageUrl"
                )
        )'''
if old in s:
    s = s.replace(old, new)
elif 'pageUrl =' not in s:
    raise SystemExit('NkiriApi.kt source parser pattern not found')
p.write_text(s)

# Preserve direct-vs-external classification through the app API.
p = Path('src/api/server.js')
s = p.read_text()
old = '''function summarizeSource(source) {
  return {
    quality: Number(source.quality || 0),
    size: Number(source.size || 0),
    sizeText: source.sizeText || (source.size ? formatSize(source.size) : ""),
    format: source.format || "mp4",
    url: source.directUrl || source.downloadUrl || source.url || null
  };
}'''
new = '''function summarizeSource(source) {
  return {
    quality: Number(source.quality || 0),
    size: Number(source.size || 0),
    sizeText: source.sizeText || (source.size ? formatSize(source.size) : ""),
    format: source.format || "mp4",
    url: source.directUrl || source.downloadUrl || source.url || null,
    type: source.type || "direct",
    external: Boolean(source.external),
    pageUrl: source.pageUrl || null
  };
}'''
if old in s:
    s = s.replace(old, new)
elif 'pageUrl: source.pageUrl' not in s:
    raise SystemExit('server.js summarizeSource pattern not found')

old = '''  return {
    quality: Number(resolved.quality || fallback.quality || 0),
    size: Number(resolved.size || fallback.size || 0),
    sizeText: resolved.sizeText || (resolved.size ? formatSize(resolved.size) : ""),
    format: resolved.format || "mp4",
    url: resolved.directUrl || resolved.pageUrl || null
  };
}'''
new = '''  return {
    quality: Number(resolved.quality || fallback.quality || 0),
    size: Number(resolved.size || fallback.size || 0),
    sizeText: resolved.sizeText || (resolved.size ? formatSize(resolved.size) : ""),
    format: resolved.format || "mp4",
    url: resolved.directUrl || resolved.pageUrl || null,
    type: resolved.type || "direct",
    external: Boolean(resolved.external),
    pageUrl: resolved.pageUrl || null
  };
}'''
if old in s:
    s = s.replace(old, new)
elif 'external: Boolean(resolved.external)' not in s:
    raise SystemExit('server.js resolveNkiri pattern not found')
p.write_text(s)

print('✓ source classification patched')
PY

node --check src/api/server.js

echo '✓ v2 premium foundation applied'
