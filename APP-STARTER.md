# TheNkiri App Starter v0.1

This starter adds a small mobile API to the existing `nkiri-bot` project and a separate Expo mobile client.

## Backend routes

- `GET /health`
- `GET /api/search?q=toy%20story`
- `GET /api/title/:id`
- `GET /api/title/:id/seasons`
- `GET /api/title/:id/episodes?season=1`
- `GET /api/source/:id`
- `GET /api/source/:id?quality=720`
- `GET /api/source/:id?season=1&episode=1`

The API reuses the existing MovieX provider in `src/providers/moviex.js`, so the Telegram bot and app can share the same provider logic.

## Install in Termux

From `~/nkiri-bot`, extract the ZIP and run:

```bash
bash install-app-starter.sh
npm run api
```

The API defaults to port `3001` unless `API_PORT` or `PORT` is set.

Test:

```bash
curl -s http://127.0.0.1:3001/health | jq
curl -s 'http://127.0.0.1:3001/api/search?q=Toy%20Story' | jq
```

## Mobile client

The `mobile/` folder targets stable Expo SDK 57.

```bash
cd mobile
npm install
cp .env.example .env
npm start
```

For local testing, `.env.example` points to `http://127.0.0.1:3001`. For a production APK, replace it with the public HTTPS address of the deployed API.

## Important

Do not merge this into the live bot deployment until the API tests pass. Use a separate branch such as `app-v1` locally first.
