TheNkiri Broadcast Center Upgrade

Admin commands:
  /broadcast
  /broadcaststats
  /cancelbroadcast

User command:
  /notifications on
  /notifications off
  /notifications status

Audience segments:
  - All users
  - Started but no activity
  - Searched but never downloaded
  - Downloaded at least once
  - Active in last 7 days
  - Android users
  - iPhone/iPad users

Broadcast content supported:
  text, photo, video, GIF/animation, document, audio, voice, sticker, location/contact when Telegram copyMessage supports it.

Every broadcast is previewed and requires explicit admin confirmation.
Users can mute future broadcasts. Delivery is rate-limited and Telegram 429 retry_after is honored.
Broadcast history and opt-out preferences are stored in data/ux.json, so existing daily backups include them.

This upgrade does NOT replace .env, store.json or ux.json.
