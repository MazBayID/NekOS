# NekOS

<p align="center">
  <img src="main_assets/NekOS_text_logo.png" alt="NekOS" width="260">
</p>

<p align="center">
  <em>A cozy space for your everyday apps.</em>
</p>

NekOS is a simple Android launcher with a cozy-tech, cat-themed look. It brings everyday apps together through a small Favorite Grid, colorful Tag groups, and a searchable App Drawer—without turning the Home Screen into a complicated dashboard.

The goal is simple: **make your apps comfortable to find, organize, and open.**

## Screenshots

### Home Screen

<p align="center">
  <img src="user_screenshot/ss_main_a.png" alt="NekOS Home Screen with Tag Rail and Favorite Grid" width="300">
</p>

The Home Screen keeps favorite apps within easy reach and places app groups in the Tag Rail.

### App Drawer and Search

<p align="center">
  <img src="user_screenshot/ss_01.png" alt="NekOS App Drawer showing installed apps" width="260">
  &nbsp;&nbsp;
  <img src="user_screenshot/ss_02.png" alt="NekOS App Drawer with search results and keyboard" width="260">
</p>

Swipe up to browse installed apps. Search filters app names as you type, so you can get to an app without scrolling through the whole list.

## Features

- **Favorite Grid** — 15 slots arranged in a 5 × 3 grid. Keep frequently used apps on Home.
- **Tag Rail** — create, rename, reorder, and delete app groups. Choose a color and an optional icon, then assign installed apps to each tag.
- **App Drawer** — browse launchable apps with their original Android icons.
- **App Search** — find apps by any part of their name, without case sensitivity.
- **App Actions** — open App info, add or remove favorites, and request uninstall through Android's system interface.
- **Personal appearance** — choose a built-in Home background or use a custom wallpaper.
- **Multi-profile awareness** — app entries are identified by app and Android profile, helping keep work or other profiles distinct.

For implementation-level details and current limitations, see the [Feature Inventory](docs/FEATURE_INVENTORY.md).

## How it works

| Area | Interaction |
| --- | --- |
| Open an app | Tap its icon |
| Open app actions | Long-press an app |
| Rearrange favorites | Long-press, then drag |
| Open the App Drawer | Swipe up from the Home canvas or Favorite area |
| Search apps | Type in the App Drawer search field |
| Open a Tag | Tap the Tag |
| Manage a Tag | Use its handle to open Tag actions |
| Return to Home | Close the drawer or press the Android Home button |

## Design philosophy

NekOS intentionally favors a focused everyday experience:

- **Simple:** only keep features that help with everyday app access.
- **Comfortable:** warm, calm colors and rounded surfaces.
- **Personal:** arrange favorites, tags, and the Home appearance.
- **Predictable:** familiar Android interactions and system UI for sensitive actions.
- **Stable:** reliability comes before adding more features.

NekOS is not intended to become a feature-heavy launcher. It deliberately avoids a widget engine, nested folders, multiple Home pages, news feeds, complex automation, and an elaborate theme system. Read [Product Direction](docs/PRODUCT_DIRECTION.md) for the design boundaries.

## Project status

- **Version:** 0.6.1
- **Application ID:** `com.nekos.android`
- **Platform:** Android 8.0 (API 26) and newer
- **Target SDK:** 35
- **Status:** in active development; not yet a stable production release

The feature list describes what is implemented in the source on `main`; it does not mean every interaction has been verified on every Android device. Physical-device checks and stability improvements remain part of development.

## Build

### Android Studio

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Allow Gradle sync to finish.
4. Run the `app` configuration on an Android device or emulator.

The project uses Kotlin, Jetpack Compose, Android Gradle Plugin, and Java 17. This repository does not currently include a Gradle Wrapper.

### Termux

Termux builds may need a local AAPT2 override. Keep this machine-specific setting in `$HOME/.gradle/gradle.properties`, not in the repository:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Adjust the path only if AAPT2 is installed elsewhere. See [Building NekOS](docs/BUILDING.md) for additional notes.

## Repository map

- `app/` — Android application source and resources.
- `main_assets/` — source artwork and branding assets.
- `user_screenshot/` — screenshots used in this README.
- `docs/FEATURE_INVENTORY.md` — implementation status and known gaps.
- `docs/PRODUCT_DIRECTION.md` — product goals and intentionally excluded features.
- `docs/BUILDING.md` — build environment notes.
- `THIRD_PARTY_NOTICES.md` — third-party software attributions and license details.

## License

NekOS is released under the [MIT License](LICENSE). Third-party dependencies retain their respective licenses; see [Third-Party Notices](THIRD_PARTY_NOTICES.md).
