# TheNkiri Updatable APK System

This patch makes the app update strategy consistent.

## What changes

- Keeps the permanent package ID: `com.nkiridown.app`
- Bumps to `versionCode 4`, `versionName 2.2.0`
- Adds an in-app "Update available" banner
- Supports a forced-update dialog from the existing API config
- Uses the existing API fields:
  - `latestVersionCode`
  - `latestVersionName`
  - `updateUrl`
  - `forceUpdate`
- Renames CI artifacts to v2

## Critical Android rule

An APK can update an older installed APK only when BOTH are true:

1. Package ID is identical.
2. APK signing certificate is identical.

GitHub Actions debug APKs should not be used as permanent public update builds because
ephemeral runners may use different debug signing keys.

Use the signed release APK after configuring the permanent release key.

## One-time signing setup

Run:

```bash
bash nkiri-updatable-build/generate-release-key.sh
```

The signing key is created only on your phone. Never send it in chat and never commit it.

Then add the four generated values to GitHub Actions repository secrets.

After that, every release APK produced with those same secrets can install over earlier
TheNkiri release builds while keeping the app's local data.
