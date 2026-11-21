# NekOS

A calm, customizable Android launcher.

## MVP

- Clean home screen with a minimal tag placeholder and a 5 × 3 favorite-slot layout.
- Favorites persist locally; tap to launch, long-press to open app actions, and drag after long-press to reorder.
- Swipe up to open the installed-app drawer; swipe down to return home.
- App drawer uses a five-column grid, native Android application icons, alphabetical order, and prefix search.
- Long-press an app in the drawer to open App info, Add to favorite, and Uninstall actions.
- Uninstall requests are handed to Android's system confirmation UI.
- Tag collection persistence foundation is in place: names, stable IDs, optional icon IDs, colors, and assigned package names are stored locally.

## Branding assets

- `main_assets/app_logo.png`: Android launcher icon.
- `main_assets/NekOS_text_logo.png`: transparent wordmark with the “Purrfectly Minimal” slogan; intended for a short, calm startup splash screen, not persistent home-screen decoration.
- Applied image assets should be copied into `drawable-nodpi` to preserve their source proportions.

## Android project

- Application ID / namespace: `com.nekos.android`
- Kotlin + Jetpack Compose
- Launcher icon copied from `main_assets/app_logo.png` to `app/src/main/res/drawable-nodpi/app_logo.png`

## Build

Open this repository in Android Studio, sync Gradle, and run the `app` module on an Android device or emulator.

The current vertical slice includes local favorite persistence, the bundled 100-icon tag catalog, and the tag-storage model. The catalog-backed icon picker, tag management UI, app assignment flow, and settings remain under development.
