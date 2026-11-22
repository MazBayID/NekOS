# Building NekOS

## Toolchain

The current Android app module uses:

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle: 8.9 (the version used by CI)
- Java: 17
- Android compile SDK: 35
- Android Build Tools: 35.0.0
- Minimum Android version: 8.0 (API 26)

The repository currently has no Gradle Wrapper. Use a compatible local Gradle 8.9 installation or Android Studio's configured Gradle installation. Do not use the AndroidIDE default Gradle 6.1.1 / Java 8 combination; it is too old for this project.

## Android Studio

1. Open the cloned repository in Android Studio.
2. Use JDK 17 for Gradle.
3. Install Android SDK Platform 35 and Build Tools 35.0.0.
4. Sync the project and run the `app` configuration.

## Command-line build

With Gradle 8.9 and Android SDK configured:

```bash
gradle --no-daemon assembleDebug testDebugUnitTest lintDebug
```

To run the instrumented Compose tests, start an API 35 emulator and run:

```bash
gradle --no-daemon connectedDebugAndroidTest
```

## Release signing

Release builds read signing credentials from environment variables; no keystore or passwords belong in the repository.

For a local signed release build, set these variables in your shell:

- `ANDROID_KEYSTORE_FILE`: path to your private keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias
- `ANDROID_KEY_PASSWORD`: private key password

Then run:

```bash
gradle --no-daemon assembleRelease
```

If the signing variables are absent, Gradle can still configure the project, but the release APK is unsigned and must not be distributed. Verify any release APK with:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Keep at least two secure backups of the keystore. Losing the signing key can prevent users from installing future updates over this release.

## GitHub Release

The `NekOS Release` workflow runs when a version tag such as `v0.1.0` is pushed. Before pushing a release tag, configure these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded keystore file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow checks that the tag matches `versionName`, runs the build and automated tests, verifies the APK signature, and attaches the APK plus SHA-256 checksum to the GitHub Release.

Create a tag only after the release APK has been tested on a physical device. Example:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Do not put signing credentials in source files, commits, issue comments, or chat messages.

## Termux on Android

Termux may need its locally packaged AAPT2 executable because the standard Android SDK toolchain is not always available in the same form as desktop environments.

Keep this machine-specific override outside the repository, in:

`$HOME/.gradle/gradle.properties`

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Adjust the path only if AAPT2 is installed elsewhere. Do not commit this absolute device-specific path to the repository.

## CI

GitHub Actions uses JDK 17, Gradle 8.9, Android SDK Platform 35, and Build Tools 35.0.0. It runs the debug APK build, unit tests, lint, and Compose instrumented tests on an API 35 emulator. The workflow supplies the matching AAPT2 path as a Gradle project property rather than changing tracked project configuration.

A successful CI run verifies the configured build and automated tests; it does not replace testing on physical Android devices.
