# AMC A-List Personal Tracker

A native Android app for tracking AMC A-List movie visits: what you saw, where, in which format, and how much the membership has saved you. Built as a personal replacement for an Excel tracker.

> Unofficial personal project. Not affiliated with, endorsed by, or sponsored by AMC Theatres. Movie data and posters come from TMDB.

## Features

- **Log** – every visit with date, showtime, theater, screen, format, language, 3D, notes and swag. Search, filter by AMC / other theaters / watchlist.
- **Filters** – one Filters menu: rating (0–5), year (built from your data, new years appear automatically), format, language, ticket type (A-List / Gift Card / Voucher / No A-List) and 3D.
- **Ratings** – rate each movie 1–5 stars; ratings show in the log and detail screen and are saved to your sheet so a restore brings them back.
- **Other theaters** – visits outside A-List are tracked separately and never counted in A-List savings.
- **Movie detail** – poster, cast, rating and overview from TMDB, with a background gradient taken from the poster. Pick a different poster any time.
- **Stats** – savings vs. membership cost, value per $1, spending charts, format and theater breakdowns, monthly heatmap.
- **Concessions** – track popcorn and drink spend per visit and how you paid (Cash, Credit Card, Rewards, Gift Card, Other); Stats shows your true total cost.
- **Rewind** – year-in-review summary you can share as an image.
- **Ticket stub** – shareable high-resolution card for any visit, in three styles (Classic, Poster, Paper).
- **Home screen widget** – this month's visits and savings at a glance.
- **Watchlist** – search TMDB, add movies, convert to a logged visit.
- **Backup & sync** – JSON backup/restore, CSV export, duplicate finder, optional two-way Google Sheets sync (Apps Script) with a one-way "Restore from Sheet" and safeguards against bulk imports or deletes.

## Tech

Kotlin 2.0, Jetpack Compose (Material 3), Room, Coil, TMDB API. Min SDK 26 (Android 8.0), target SDK 36.

## Build

1. Open the folder in Android Studio and let Gradle sync.
2. Add your TMDB read access token to `local.properties` (not committed; the token is built into the app at compile time):
   ```
   TMDB_TOKEN=your_tmdb_v4_read_access_token
   ```
3. Run on a device or emulator. The debug build installs as "A-List DEV" (`com.alisttracker.dev`) with its own data, so it never touches your real app.

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
3. Deploy as a web app (Execute as: Me, Who has access: Anyone) and paste the URL and the same secret into the app's Settings.
4. After updating the script later, use Deploy → Manage deployments → pencil → New version → Deploy. The URL stays the same.

The script adds **Rating**, **Concessions** and **Paid with** columns (P, Q, R) only if those columns are empty or already have those headers. If they hold other data, the script leaves them alone and the app keeps those fields on the phone only. Try it on a copy of your sheet first.

## Data

All data is stored on the device. The sync link and secret are excluded from Android cloud backups. The app starts empty; add visits manually, restore a JSON backup, or sync from a Google Sheet. `app/src/main/assets/seed.json` is an empty placeholder.

## License

MIT

This product uses the TMDB API but is not endorsed or certified by TMDB.
