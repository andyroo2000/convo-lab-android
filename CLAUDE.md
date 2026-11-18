# Claude Review Guidance

This is the native Kotlin/Jetpack Compose Android client for ConvoLab.
The initial foundation contains a welcome screen only; authentication, study,
offline sync, and audio will arrive in focused follow-up changes.

Prioritize concrete correctness, crashes, data loss, security, accessibility,
performance, and missing behavioral tests. Respect the incremental rollout;
do not request speculative abstractions or unimplemented product features.

- Keep composables focused on UI; move I/O and business rules to dedicated layers.
- Check coroutine cancellation, lifecycle ownership, main-thread work, and state restoration.
- Preserve learning-os wire contracts, account isolation, stable client event IDs,
  retry-safe outboxes, tombstones, and checkpoint ordering.
- Never overwrite unacknowledged local changes with server refreshes.
- Store authentication material using Android Keystore-backed protection.
- Keep production API defaults for physical-device builds and require HTTPS.
- Review future media services for audio focus, interruptions, background restrictions,
  Bluetooth changes, lock-screen controls, and atomic cache writes.
- Keep comments concise and actionable. Report actual defects in the changed code.
