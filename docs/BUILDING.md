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
