# Claude Review Guidance

This is the native Kotlin/Jetpack Compose Android client for ConvoLab.
The initial foundation contains a welcome screen only. The product scope is Journal
and reduced Settings, including WaniKani sync. Exclude daily vocabulary transfer
options, calendar sync, Satori Reader shortcut detection, downloaded-media management,
Study, Readings, Daily Audio, Manage Cards,
Time, and their related settings. Journal's own audio remains in scope.
Do not request omitted features as parity gaps.

Prioritize concrete correctness, crashes, data loss, security, accessibility,
performance, and missing behavioral tests. Respect the incremental rollout;
do not request speculative abstractions or unimplemented product features.

- Keep composables focused on UI; move I/O and business rules to dedicated layers.
- Check coroutine cancellation, lifecycle ownership, main-thread work, and state restoration.
- Preserve learning-os wire contracts, account isolation, and retry-safe Journal writes.
- Never overwrite unacknowledged local changes with server refreshes.
- Store authentication material using Android Keystore-backed protection.
- Keep production API defaults for physical-device builds and require HTTPS.
- Review future media services for audio focus, interruptions, background restrictions,
  Bluetooth changes, lock-screen controls, and atomic cache writes.
- Keep comments concise and actionable. Report actual defects in the changed code.
