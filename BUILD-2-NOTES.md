# TheNkiri Native App — Build 2

Build 2 completes the provider/backend merge while keeping Android-phone resource use near zero.

## Added in Build 2

- Unified app search through the existing `searchNkiri()` flow
- TheNkiri + MovieX results in one API
- Safe opaque IDs for TheNkiri URLs
- TheNkiri movie details
- TheNkiri series details
- TheNkiri season/episode mapping
- TheNkiri download resolving through the existing resolver stack
- MovieX title/season/source support retained
- `/api/latest?type=all|movie|series|drama`
- API rate limiting and short metadata cache
- Render-ready backend
- Cloud APK build remains enabled

## Current Android UI

The native UI already supports:

- search
- posters/details
- movies
- series
- season selection
- episode selection
- quality/source selection
- DownloadManager downloads

The next build layer is local UX: Favorites, recent searches, download history, settings/API switching, channel/bot/VLC links, and UI polish.

No phone-side build or test is required yet.
