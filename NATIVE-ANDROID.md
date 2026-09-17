# TheNkiri Native Android v0.1

This replaces the need for Expo during app testing/building.

## What it contains

- Native Kotlin Android app
- Jetpack Compose UI
- Movie/series search
- Movie details
- Seasons and episodes
- Quality selection
- Android DownloadManager downloads
- Coil poster loading
- Existing `src/api/server.js` backend
- GitHub Actions APK build

## Local API

The debug APK defaults to:

`http://127.0.0.1:3001`

That works for local testing when the TheNkiri API is running in Termux on the same Android phone.

Run:

```bash
cd ~/nkiri-bot
npm run api
```

Keep that process running while using the APK.

## Install files

From the repository root:

```bash
unzip -o nkiri-native-android-v0.1.zip
bash install-native-android.sh
```

## Git branch

If you are still on `v2`, you can make a separate app branch:

```bash
git checkout -b app-v1
```

If `app-v1` already exists:

```bash
git checkout app-v1
```

Then:

```bash
git add android-native .github/workflows/build-android-apk.yml
git commit -m "Add native Android app"
git push -u origin app-v1
```

## Get the APK

After the push:

1. Open the repository on GitHub.
2. Open **Actions**.
3. Open **Build Android APK**.
4. Open the successful run.
5. Download the artifact named **TheNkiri-Android-debug**.
6. Extract it and install `app-debug.apk`.

## Hosted API later

For a normal public app, the API should be hosted over HTTPS.

The workflow supports a GitHub repository variable named:

`NKIRI_API_BASE_URL`

Example:

`https://api.example.com`

When this repository variable is set, future APK builds use it automatically.

Do not put API secrets in this variable. It is compiled into the app and can be discovered by users.

## Expo

The existing `mobile/` folder is not deleted by this package. It can be removed later after the native APK is verified.
