# ConvoLab for Android

Native Android companion to ConvoLab, built with Kotlin and Jetpack Compose.
Shares the production learning-os API with the iOS app.

## Current scope

A buildable welcome screen using ConvoLab's navy and cream palette. Authentication,
study, offline sync, and audio are not implemented yet. No account requests or
review mutations are made by this foundation.

## Local development

Install Android Studio for Apple Silicon and JDK 17. Open this repository in Android
Studio and let Gradle sync. Use JDK 17 for the Gradle JDK in project settings.

The checked-in Gradle wrapper downloads and verifies Gradle 9.9.1. Dependencies are
pinned: Android Gradle Plugin 9.5.1, Kotlin 2.2.21, and Compose BOM 2026.09.00.
The app supports Android 9+ (API 28) and currently compiles/targets API 37.

On Andrew's Mac the tools are installed at:

- Android Studio: `/Applications/Android Studio.app`
- SDK: `~/Library/Android/sdk`
- JDK 17: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`

For a new checkout, create an ignored `local.properties` with your SDK path:

```properties
sdk.dir=/Users/andrewlandry/Library/Android/sdk
```

Build and run static checks:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
./gradlew assembleDebug lintDebug testDebugUnitTest
```

There are no unit tests yet because this slice has no business logic. Add meaningful
behavioral tests alongside authentication, sync, and scheduling implementations.

## Pixel testing

Enable Developer options and USB debugging, connect the Pixel, and accept the
computer's debugging prompt. Then select it in Android Studio and click Run, or:

```sh
adb devices
./gradlew installDebug
adb shell am start -n com.cdcg.convolab.debug/com.cdcg.convolab.MainActivity
```

Debug builds use a separate application ID (`com.cdcg.convolab.debug`). The production
API default is `https://convo-lab.com` for both build types; HTTP is disabled.
No release signing key or Play Store distribution is configured yet.

An ARM64 Android 16 emulator named `ConvoLab_API_36` is installed on Andrew's Mac
and has been used to install and launch the starter app. Start it from Device Manager
or with `emulator -avd ConvoLab_API_36`. Other virtual devices can be added later.
The SDK and a physical Pixel are also enough for local builds and testing.

## GitHub checks

- **Android Build:** builds the debug APK, runs Android Lint with warnings treated
  as errors, and runs unit tests when present. Uploads a debug APK for 14 days.
- **Claude Code Review:** mirrors iOS review automation with Android-specific
  guidance in `CLAUDE.md`. Runs for non-draft, same-repository pull requests;
  fork PRs cannot access the credential. Uses the `CLAUDE_CODE_OAUTH_TOKEN` secret.
- **CodeScene:** the Android repository is included in the existing `convo-lab`
  project alongside iOS, web, and backend. Automated PR checks require the existing
  CodeScene Access GitHub app to include this repository.

Local source changes also require CodeScene MCP file reviews, staged safeguards,
and branch change-set analysis as described in `AGENTS.md`. CodeScene does not
support Gradle `.kts` scripts; Gradle compilation and Android Lint cover those.

## Port sequence

1. Account authentication and secure token storage against the existing API.
2. Account-scoped local storage, card sync, and a basic review session.
3. Retry-safe offline review/card outboxes and cross-device consistency tests.
4. Card presentation, furigana, audio caching, and Daily Audio playback.
5. Remaining current iOS features, with a fresh parity inventory from iOS `main`.

Use the current iOS main branch and backend contracts as references, not a stale
local iOS checkout. Port behavior and contract test cases, not Apple-specific APIs.
