# Grafit

A Hungarian-first, native Android KRÉTA client built with Kotlin, Jetpack Compose, and Material 3. The screen structure follows Firka with a new visual treatment and persistent appearance settings.

## Current features

- KRÉTA-hosted sign-in with OAuth authorization code + S256 PKCE and callback state validation
- Encrypted on-device tokens and school cache using Android Keystore, automatic refresh, and logout that clears session data
- Home dashboard with next lesson, recent grades, daily schedule, homework, announced tests, and school noticeboard
- Week navigation, lesson details, subject-filtered grades, weighted midyear averages, and absence status
- Light, dark, and system themes, Android 12+ wallpaper colors, four accent palettes, card radius, compact density, hidden grades, and navigation visibility
- Explicit demo mode with fictional school data, separate from real account storage

## Build

Use Android Studio with JDK 17 or 21, Android SDK Platform 35, and Build Tools 35.0.0. Android 8.0 (API 26) or newer is supported.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Install it on an Android device with `adb install -r app/build/outputs/apk/debug/app-debug.apk` or run the app from Android Studio. `local.properties` may specify `sdk.dir` for a local SDK. Gradle 8.13 is pinned with a distribution checksum.

For the prepared cloud machine, first run `source scripts/cloud-env.sh`. To reinstall its toolchain and validate the project, run `bash scripts/cloud-setup.sh`.

Eight JVM tests cover PKCE, callback origin and state validation, text grades, weighted averages, timezone handling, and demo data. A device UI test covers demo navigation and theme selection. Build it with `./gradlew :app:assembleDebugAndroidTest` and run it on a connected device with `./gradlew :app:connectedDebugAndroidTest`.

## Account and privacy

Sign in through the KRÉTA page inside a restricted HTTPS WebView. School selection is handled by that page. Grafit does not collect the account password or send credentials to a Grafit server. Tokens and cached school responses are encrypted in app-private storage, backups are disabled, and logout clears the cache and WebView cookies. Network failures retain the last successful cache and show the last synchronization time. The timetable downloads a 15-day window around the selected day. An expired session can be reauthenticated from the error banner.

Wear OS is outside the project scope.

## First-version boundaries

One account is active at a time. This version reads homework, tests, and the school noticeboard. Homework submission, attachments, e-ügyintézés messaging, push notifications, home-screen widgets, and multi-account switching are pending. Grade averages include numeric marks with a positive KRÉTA weight, weighted by that percentage. Real login and school-specific response compatibility require validation with an authorized account on a device. Demo data does not validate the live service.

## License and upstream

Adapted from [Firka by QwIT Development and contributors](https://github.com/QwIT-Development/firka). See [ATTRIBUTION.md](ATTRIBUTION.md) and [LICENSE.txt](LICENSE.txt). CC BY-NC-SA 4.0 includes noncommercial and share-alike conditions. Grafit is an independent client.
