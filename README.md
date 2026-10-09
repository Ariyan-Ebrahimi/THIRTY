# THIRTY

**Do it in 30 days or make it a habit.**

THIRTY is a minimal, offline-first Android app for turning a personal goal into a daily practice. Choose a challenge, show up each day, and see your progress build over time.

## Highlights

- Native Android app built with Kotlin, Jetpack Compose, and Material 3
- Local-first data storage with Room and DataStore
- Daily progress, streaks, journey views, and challenge history
- Journal notes and milestone tracking
- Local reminders and an Android home-screen widget
- JSON backup and restore
- Dark, minimal visual identity with an electric-lime accent
- No account, backend, or cloud service required

> THIRTY is designed to work offline. Android system features such as notifications and widgets are subject to the device's OS settings and manufacturer-specific battery restrictions.

## Requirements

- JDK 17
- Android SDK with platform 36 installed
- Gradle 8.7 (the included scripts download it when needed)
- Internet access on the first build to download Gradle and Maven dependencies

## Build on Windows

1. Install JDK 17 and Android SDK.
2. Open PowerShell in the project root.
3. Make sure `local.properties` points to your local Android SDK. This file is intentionally excluded from Git.
4. Run:

   ```powershell
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat assembleDebug
   ```

The debug APK is normally generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Build on macOS or Linux

Install JDK 17 and Android SDK, configure `local.properties` for your machine, then run:

```bash
chmod +x ./gradlew
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Project structure

```text
app/src/main/java/dev/thirty/app/
├── backup/       # JSON backup and restore
├── data/         # Room database, DataStore, repository
├── domain/       # Challenge rules and domain logic
├── navigation/   # Compose navigation
├── notifications/# Local reminders and receivers
├── ui/           # Screens, components, and theme
├── util/         # Date helpers
└── widget/       # Android home-screen widget
```

## Tests

- Local unit tests: `testDebugUnitTest`
- Instrumented Android tests: `connectedDebugAndroidTest` (requires a connected device or emulator)

A GitHub Actions workflow runs unit tests and builds the debug APK on pushes and pull requests.

## Privacy

THIRTY's core experience is local-first. Challenge and progress data are stored on the device. Backup files are created only when the user initiates an export. Review Android permissions and the source code before relying on any particular privacy or security guarantee.

## Project status

This repository is being prepared for its first public release. The included code and tests should be treated as a work in progress until the CI build and manual device testing pass.

## License

No open-source license has been added yet. Unless a license is added, standard copyright applies and reuse, modification, and redistribution are not automatically permitted.
