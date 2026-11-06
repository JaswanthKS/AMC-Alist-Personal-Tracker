# AMC A-List Personal Tracker

A native Android app for tracking AMC A-List movie visits: what you saw, where, in which format, and how much the membership has saved you. Built as a personal replacement for an Excel tracker.

> Unofficial personal project. Not affiliated with, endorsed by, or sponsored by AMC Theatres. Movie data and posters come from TMDB.

## Features

- **Log** – every visit with date, showtime, theater, screen, format, language, 3D, notes and swag. Search, filter by AMC / other theaters / watchlist.
- **Other theaters** – visits outside A-List are tracked separately and never counted in A-List savings.
- **Movie detail** – poster, cast, rating and overview from TMDB, with a background gradient taken from the poster. Pick a different poster any time.
- **Stats** – savings vs. membership cost, value per $1, spending charts, format and theater breakdowns, monthly heatmap.
- **Rewind** – year-in-review summary you can share as an image.
- **Ticket stub** – shareable stub card for any visit.
- **Watchlist** – search TMDB, add movies, convert to a logged visit.
- **Backup & sync** – JSON backup/restore, CSV export, duplicate finder, optional two-way Google Sheets sync (Apps Script).

## Tech

Kotlin 2.0, Jetpack Compose (Material 3), Room, Coil, TMDB API. Min SDK 26 (Android 8.0), target SDK 36.

## Build

1. Open the folder in Android Studio and let Gradle sync.
2. Add your TMDB read access token to `local.properties` (not committed):
   ```
   TMDB_TOKEN=your_tmdb_v4_read_access_token
   ```
3. Run on a device or emulator.

### Release build

Create a keystore and a `keystore.properties` file in the project root (both git-ignored):

```
storeFile=../upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then select the `release` variant and run Build → Generate App Bundles.

## Google Sheets sync (optional)

1. Open `AList_Sheet_Sync.gs` in the Apps Script editor of your tracking sheet.
2. Change `SECRET` to a long random string.
3. Deploy as a web app and paste the URL and the same secret into the app's Settings.

## Data

All data is stored on the device. The app starts empty; add visits manually, restore a JSON backup, or sync from a Google Sheet. `app/src/main/assets/seed.json` is an empty placeholder.

## License

MIT

This product uses the TMDB API but is not endorsed or certified by TMDB.
