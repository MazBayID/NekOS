# Building NekOS

## Android Studio and standard Linux/macOS environments

Open the repository in Android Studio, allow Gradle sync to complete, then run the `app` configuration. The repository intentionally does not pin an absolute AAPT2 executable path: Android Gradle Plugin should resolve the matching build-tools executable from the installed Android SDK.

For command-line builds, install the Android SDK platform and build-tools version required by the app module, then run the Gradle tasks used by CI.

## Termux on Android

Termux may need its locally packaged AAPT2 executable because the standard Android SDK toolchain is not always available in the same form as desktop environments.

Keep this machine-specific override outside the repository, in the user's Gradle configuration:

`$HOME/.gradle/gradle.properties`

Add the following line, adjusting the path only if AAPT2 is installed elsewhere:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

This keeps the repository portable: the Termux override applies to builds on that device without forcing the same absolute path on Android Studio, other developers, or GitHub Actions.

## CI

GitHub Actions supplies the Android SDK build-tools AAPT2 path as a Gradle project property for its build and instrumentation-test tasks. The workflow does not need to edit tracked project configuration.
