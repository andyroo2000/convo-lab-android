# ConvoLab for Android

Native Android companion to ConvoLab, built with Kotlin and Jetpack Compose.
Shares the production learning-os API with the iOS app.

## Current scope

The app opens authentication and an account-scoped Journal, with reduced Settings.
It includes durable drafts, Contents, clarification and corrections, Draft/Final
editing, tutor conversations, narration, on-device Japanese input, appearance,
WaniKani known-kanji sync, and account management.

The port follows the [captured iOS reference](docs/ios-reference/2026-11-20/README.md).
Feature screenshots, contracts and verification notes are in `docs/android-port/`.
[Acceptance issue #17](https://github.com/andyroo2000/convo-lab-android/issues/17)
tracks the remaining real-account and human checks.

## Product scope

This port contains **Journal** and a reduced **Settings** section. Study, Readings, Daily Audio,
Manage Cards, and Time are disabled for non-admin users in the product and are out
of scope for this initial Android port, including their related settings.

Keep account settings, Journal-related settings, and **WaniKani sync**. Omit daily
vocabulary transfer options, calendar sync, Satori Reader shortcut detection,
and the downloaded-media management section.
This does not remove audio playback or caching needed by Journal itself.

## Local development

Install Android Studio for Apple Silicon and JDK 17. Open this repository in Android
Studio and let Gradle sync. Use JDK 17 for the Gradle JDK in project settings.

The checked-in Gradle wrapper downloads and verifies Gradle 9.9.1. Dependencies are
pinned: Android Gradle Plugin 9.5.1, Kotlin 2.2.21, and Compose BOM 2026.11.01.
The app supports Android 9+ (API 28) and compiles against API 37.1 and targets API 37.

On Andrew's Mac the tools are installed at:

- Android Studio: `/Applications/Android Studio.app`
- SDK: `~/Library/Android/sdk`
- JDK 17: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`

For a new checkout, create an ignored `local.properties` with your SDK path:

```properties
sdk.dir=/Users/andrewlandry/Library/Android/sdk
```

Prepare the offline engine, model and dictionary for a new checkout using the
[native setup instructions](docs/typing-playground.md#build-on-this-mac). Both debug
and release APKs require those generated assets. Then build and run checks:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew assembleDebug lintDebug testDebugUnitTest
```

The completed port passed 136 JVM tests and 89 instrumented tests on each of the
Pixel 10a and API 36 ARM64 emulator. CI also runs the complete suite on x86_64.
See [lifecycle verification](docs/android-port/lifecycle.md) for process-restart,
packaging and keyboard-layout evidence. These fixture checks do not establish
live-account behavior or human audio/accessibility acceptance.

## Pixel testing

Enable Developer options and USB debugging, connect the Pixel, and accept the
computer's debugging prompt. Then select it in Android Studio and click Run, or:

```sh
adb devices
./gradlew assembleDebug
# Set ANDROID_SERIAL to the chosen device when an emulator is also connected.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.cdcg.convolab.debug/com.cdcg.convolab.MainActivity
```

Debug builds use a separate application ID (`com.cdcg.convolab.debug`). The production
API default is `https://convo-lab.com` for both build types; HTTP is disabled.
No release signing key or Play Store distribution is configured yet.

An ARM64 Android 16 emulator named `ConvoLab_API_36` is installed on Andrew's Mac
and has passed the integrated app tests. Start it from Device Manager
or with `emulator -avd ConvoLab_API_36`. Other virtual devices can be added later.
The SDK and a physical Pixel are also enough for local builds and testing.

## GitHub checks

- **Android Build:** prepares both native ABIs, builds debug and release APKs, runs
  Android Lint with warnings treated as errors, JVM tests and offline emulator tests.
  Uploads the debug APK and test/screenshot reports for 14 days.
- **Claude Code Review:** mirrors iOS review automation with Android-specific
  guidance in `CLAUDE.md`. Runs for non-draft, same-repository pull requests;
  fork PRs cannot access the credential. Uses the `CLAUDE_CODE_OAUTH_TOKEN` secret.
- **CodeScene:** the Android repository is included in the existing `convo-lab`
  project alongside iOS, web, and backend. Automated PR checks require the existing
  CodeScene Access GitHub app, now enabled for this repository.

Local source changes also require CodeScene MCP file reviews, staged safeguards,
and branch change-set analysis as described in `AGENTS.md`. CodeScene does not
support Gradle `.kts` scripts; Gradle compilation and Android Lint cover those.

## Japanese input and playground

Journal includes immediate romaji-to-kana composition, kanji suggestions, section
selection/resizing, expanded candidates, and account-scoped confirmed learning.
The device keyboard can be selected instead. See [composition](docs/android-port/composition.md),
[keyboard integration](docs/android-port/journal-keyboard.md), and
[performance results](docs/android-port/typing-performance.md).

Typing remains independent of native inference. On the Pixel, ordinary native
suggestions measured roughly 340 ms p95, above the 150 ms target. A cold expanded
request still takes about 13 seconds; a repeated warm request is about 57 ms.
These are measured inference results, not physical key-to-display latency.

The standalone typing playground remains a debug-only activity. It is not the app's
launch screen and does not require an account:

```sh
adb shell am start -n com.cdcg.convolab.debug/com.cdcg.convolab.TypingLabActivity
```

See [playground setup and historical benchmarks](docs/typing-playground.md).
Release builds package the Journal engine/model/dictionary but exclude playground
fixtures. Play Store distribution and release signing are not configured.
