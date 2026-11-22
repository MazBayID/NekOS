# NekOS

*A cozy space for your everyday apps.*

NekOS is a simple Android launcher for everyday use, with a warm, cozy-tech look and a cat theme. It focuses on making app access comfortable, tidy, and easy, rather than on piling up complex features.

## Principles

- **Simple:** only features that are genuinely useful for everyday use.
- **Comfortable:** a warm, calm look that is easy on the eyes.
- **Easy to reach:** favorites and tag groups are always a tap or swipe away.
- **Personal:** you arrange your apps, tags, and Home appearance.
- **Stable:** a dependable daily experience matters more than a long feature list.

Flow: **Home → Favorite / Tag → App Drawer → Search → Settings**

## What NekOS has today

- **Home Screen:** a 5 × 3 Favorite Grid (15 slots) at the bottom and a vertical Tag Rail on the right. Tap to launch; long-press for app actions; drag after long-press to rearrange.
- **App Drawer:** swipe up from the Home canvas or the Favorite area to open a panel covering about 60% of the screen. It rises above the keyboard while you search. It shows all launchable apps with their native icons in alphabetical order, with partial-name search. Swipe down from the drawer handle, tap the area above the panel, or press Home to return.
- **App actions:** long-press an app for App info, Add/Remove favorite, and Uninstall. Uninstall is confirmed by Android's own system UI.
- **Tag System:** create, rename, reorder, and delete tags; pick a color and an optional icon; assign apps; tap a tag to open its apps.
- **Settings:** Home background presets, custom wallpaper, a gesture hint, and About (version, license, repository link).

For the detailed, code-verified status of every feature, see [`docs/FEATURE_INVENTORY.md`](docs/FEATURE_INVENTORY.md).

## What NekOS intentionally does not do

No widget engine, nested folders, multiple Home pages, news or recommendation feeds, complex automation, elaborate theme system, or large set of gestures. See [`docs/PRODUCT_DIRECTION.md`](docs/PRODUCT_DIRECTION.md).

## Status

Version `0.1.0`. Feature status reflects the source code on `main`; it is not a result of a fresh test pass on physical devices. Stabilizing the basics is the current priority.

## Project

- Application ID / namespace: `com.nekos.android`
- Kotlin + Jetpack Compose, single Activity
- `minSdk` 26, `targetSdk` 35

## Build

Open the repository in Android Studio, sync Gradle, and run the `app` module on a device or emulator. Command-line and Termux notes are in [`docs/BUILDING.md`](docs/BUILDING.md).

## Branding assets

- `main_assets/app_logo.png`: Android launcher icon.
- `main_assets/NekOS_text_logo.png`: wordmark shown on the short startup splash.
- `main_assets/text_logo_sec.png`: secondary wordmark shown in the Home header.
- Image assets applied in the app are copied into `app/src/main/res/drawable-nodpi/` to keep their proportions.

## License

MIT. See [`LICENSE`](LICENSE).
