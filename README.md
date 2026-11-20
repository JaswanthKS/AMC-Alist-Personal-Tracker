<div align="center">

# AMC A-List Personal Tracker

**A native Android app for tracking AMC A-List movie visits, and what the membership is really saving you.**

[![Latest release](https://img.shields.io/github/v/release/JaswanthKS/AMC-Alist-Personal-Tracker?style=flat-square)](https://github.com/JaswanthKS/AMC-Alist-Personal-Tracker/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)

</div>

> **Unofficial personal project.** Not affiliated with, endorsed by, or sponsored by AMC Theatres. Movie data and posters come from [TMDB](https://www.themoviedb.org/).

## Overview

Built as a personal replacement for an Excel tracker. Log every visit, see how much the membership has paid for itself, and look back on your year in movies. All data stays on your device, with optional two-way sync to your own Google Sheet.

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Release build](#release-build)
- [Google Sheets sync (optional)](#google-sheets-sync-optional)
- [Data and privacy](#data-and-privacy)
- [Contributing](#contributing)
- [License](#license)

## Features

### Logging
- **Movie log**: date, showtime, theater, screen, format, language, 3D, notes, swag and rating for every visit, with search and filters.
- **Smart title search**: suggestions come from your own history first, then from every film TMDB knows, with poster and year.
- **Add-your-own options**: theaters, formats, languages and swag lists grow as you use them.
- **Filters**: rating, year, format, language, ticket type (A-List / Gift Card / Voucher / No A-List) and 3D.
- **Other theaters**: visits outside A-List are tracked separately and never counted in A-List savings.
- **Ratings**: 1–5 stars, saved to your sheet so a restore brings them back.
- **Concessions**: itemised food and drink spend per visit, and how you paid.

### Stats
- **Savings**: A-List value vs. membership cost, value per $1, and whether each month paid for itself.
- **Quick navigation**: jump buttons at the top of the Stats page, and tapping Stats again scrolls back to the top.
- **Breakdowns**: theaters, formats, languages, screens, average ticket by format, and a monthly heatmap.
- **Habits and records**: streaks, busiest days, multi-movie days, year vs. year, and per-film stats.
- **Membership**: payments added automatically each month, with an editable current price that never changes past entries.

### Sharing and extras
- **Rewind**: year-in-review summary you can share as an image.
- **Ticket stub**: shareable high-resolution card for any visit, in three styles (Classic, Poster, Paper).
- **Movie detail**: poster, cast, rating and overview from TMDB, with a gradient taken from the poster.
- **Watchlist**: search TMDB, add movies, get release reminders, and convert a title into a logged visit.
- **Home screen widget**: this month's visits and savings at a glance.
- **Backup and sync**: JSON backup and restore, CSV export, duplicate finder, and optional Google Sheets sync with safeguards against bulk imports or deletes.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 |
| Storage | Room |
| Images | Coil |
| Movie data | TMDB API |
| Sync (optional) | Google Apps Script web app |
| SDK | Min 26 (Android 8.0), target 36 |

## Getting started

**Requirements:** Android Studio, a device or emulator running Android 8.0+, and a free [TMDB](https://www.themoviedb.org/settings/api) read access token.

1. Clone the repository and open the folder in Android Studio. Let Gradle sync.
2. Add your TMDB token to `local.properties` (git-ignored; it is built into the app at compile time):
   ```properties
   TMDB_TOKEN=your_tmdb_v4_read_access_token
   ```
3. Run the `app` configuration on a device or emulator.

The debug build installs as **A-List DEV** (`com.alisttracker.dev`) with its own data, so it never touches your real app.

### Tests

```bash
./gradlew testDebugUnitTest
```

## Release build

Create a keystore and a `keystore.properties` file in the project root (both are git-ignored):

```properties
storeFile=../upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Select the `release` build variant, then choose **Build → Generate App Bundles or APKs → Generate Bundles**. The signed bundle is written to `app/build/outputs/bundle/release/`.

## Google Sheets sync (optional)

1. Open `AList_Sheet_Sync.gs` in the Apps Script editor of your tracking sheet.
2. Change `SECRET` to a long random string.
3. Deploy as a web app (Execute as: Me, Who has access: Anyone), then paste the URL and the same secret into the app's Settings.
4. After updating the script later, use **Deploy → Manage deployments → pencil → New version → Deploy**. The URL stays the same.

The script adds **Rating**, **Concessions** and **Paid with** columns (P, Q, R) only if those columns are empty or already have those headers. If they hold other data, the script leaves them alone and the app keeps those fields on the phone only. Try it on a copy of your sheet first.

## Data and privacy

All data is stored on the device. The sync link and secret are excluded from Android cloud backups. The app starts empty: add visits manually, restore a JSON backup, or sync from a Google Sheet. `app/src/main/assets/seed.json` is an empty placeholder. See [PRIVACY.md](PRIVACY.md) for details.

## Contributing

This is a personal project, but issues and suggestions are welcome. For code changes, please open a pull request from a feature branch with a clear description.

## License

Released under the [MIT License](LICENSE).

This product uses the TMDB API but is not endorsed or certified by TMDB.
