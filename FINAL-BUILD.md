# TheNkiri Native Android — Final v1

This is the production-oriented v1 source package.

The visual goal is a premium movie-service experience without copying another app's branding or proprietary assets.

## Final Android experience

- native Kotlin + Jetpack Compose
- branded launcher icon + Android 12 splash
- premium dark visual system
- featured hero title
- two-column poster catalog
- Discover / Movies / Series / K-Drama filters
- search + recent searches
- movie and series details
- season and episode browsing
- quality / file-size selection
- native Android DownloadManager
- favorites / library
- download history
- API status
- editable API endpoint
- remote notice system
- remote update banner
- forced-update flag support
- updates channel button
- Telegram bot button
- share button
- VLC Play Store link
- local crash capture
- local privacy information
- release minification/resource shrinking

## API

- health
- remote app config
- combined catalog/search
- title details
- episodes
- source selection
- caching
- rate limiting
- basic security headers

### Routes

- `GET /health`
- `GET /api/config`
- `GET /api/search?q=...`
- `GET /api/latest?type=all|movie|series|drama`
- `GET /api/title/:id`
- `GET /api/title/:id/episodes?season=1`
- `GET /api/source/:id`
- `GET /api/source/:id?season=1&episode=1&quality=720`

## Remote update configuration

The API reads:

- `APP_LATEST_VERSION_CODE`
- `APP_LATEST_VERSION_NAME`
- `APP_UPDATE_URL`
- `APP_FORCE_UPDATE`
- `APP_NOTICE`
- `APP_CHANNEL_URL`
- `APP_BOT_URL`

When a later version code is higher than the installed APK's version code, the app shows an update banner.

## Cloud build

GitHub Actions builds a debug APK automatically.

If signing secrets are configured, the same workflow also builds:

- signed release APK
- signed AAB for Play Store

### GitHub repository variable

Set:

`NKIRI_API_BASE_URL`

to the public HTTPS URL of the deployed app API.

### Optional release signing secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The first final device test does not require release signing. The debug APK is enough.

## Phone-only installation workflow

From the nkiri-bot repository root:

```bash
unzip -o nkiri-native-final.zip
bash apply-final-native-build.sh
```

Then push once:

```bash
git checkout app-v1 2>/dev/null || git checkout -b app-v1

git add \
  android-native \
  src/api \
  render.yaml \
  .github/workflows \
  package.json \
  src/providers/moviex.js

git commit -m "Complete TheNkiri native Android v1"

git push -u origin app-v1
```

Do not run Gradle or Expo on the Android phone.

## Final deployment order

1. Push the final source.
2. Deploy the API from `render.yaml` or another Node host.
3. Copy the public HTTPS API URL.
4. Set GitHub repository variable `NKIRI_API_BASE_URL` to that URL.
5. Run **Build TheNkiri Android** in GitHub Actions.
6. Download `TheNkiri-Android-v1-debug`.
7. Extract and install `app-debug.apk`.
8. Perform the single final device test.

## Final test checklist

- launch / splash / icon
- API shows ONLINE
- Discover loads
- Movies filter
- Series filter
- K-Drama filter
- search
- recent search
- title details
- favorites
- movie quality
- movie download
- series season
- episode
- episode quality
- episode download
- download history
- native Downloads screen
- API settings
- updates channel
- bot link
- share
- VLC link
- remote update banner

Only download content you are authorized to access.
