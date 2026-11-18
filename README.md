# ConvoLab for Android

Native Android companion to ConvoLab, built with Kotlin and Jetpack Compose.
Shares the production learning-os API with the iOS app.

## Current scope

A buildable welcome screen using ConvoLab's navy and cream palette. Authentication
and Journal functionality are not implemented yet. This foundation makes no account
requests or user-data mutations.

## Initial product scope

Build **Journal** and a reduced **Settings** section. Study, Readings, Daily Audio,
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
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew assembleDebug lintDebug testDebugUnitTest
```

There are no unit tests yet because this slice has no business logic. Add meaningful
behavioral tests alongside authentication, Journal, and WaniKani sync implementations.

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
  CodeScene Access GitHub app, now enabled for this repository.

Local source changes also require CodeScene MCP file reviews, staged safeguards,
and branch change-set analysis as described in `AGENTS.md`. CodeScene does not
support Gradle `.kts` scripts; Gradle compilation and Android Lint cover those.

## Port sequence

1. Inventory current iOS Journal flows and the Settings subset above against iOS `main`.
2. Account authentication, secure token storage, and account-scoped Journal state.
3. Journal screens and their existing backend contracts, including Journal audio.
4. Reduced Settings and WaniKani sync, without vocabulary transfer options.
5. Pixel testing of Journal editing, Japanese input, playback, and account lifecycle.

Use the current iOS main branch and backend contracts as references, not a stale
local iOS checkout. Port behavior and contract test cases, not Apple-specific APIs.
