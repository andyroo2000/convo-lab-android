# Journal audio

Part of #12. Final entries and historical tutor questions now offer Listen/Pause, seeking, sentence navigation and exact-transcript highlighting. Final entries include narration voice selection. Question audio uses its original parent/question identity; final audio includes analysis ID and reply revision. A changed source cancels obsolete generation. Navigating away stops the source, while backgrounding the app keeps playback active. Account replacement stops and releases playback.

Media3 supplies a single speech player, audio focus, becoming-noisy handling and a media session/foreground notification. The service is not exported; the media session accepts this app and trusted system controllers. Media replacement accepts only this account's local audio and current playback lease. The view model survives rotation. Audio and selected voices are cached per account in no-backup storage. Atomic files plus a digest reject partial/mismatched audio. A 128 MiB cache budget evicts the oldest derived files while protecting the newly generated and selected playback recordings, including a delayed cancelled generation. Missing or invalid optional timing metadata falls back to ordinary playable text. There is no downloaded-media management screen.

The six bundled voice portraits are exact copies of learning-os `resources/images/journal/{female,male}-0{1,2,3}-v2.png` at a45444e0b51c9d36c828bde8bf2d7335b222a857. Unknown avatars fall back to a voice name. Playback uses [Media3 1.11.1](https://developer.android.com/jetpack/androidx/releases/media3) and its [background playback service](https://developer.android.com/media/media3/session/background-playback).

Eight JVM tests cover exact timing validation, ruby/emoji boundaries, offline reuse, voice changes, stale generation cancellation and account close. Five Android contract/cache tests cover routes/revisions, invalid timing fallback, stale-session rejection, account isolation, atomic backup recovery and corrupt audio. Four real-player UI tests cover seeking, voice choice, Home/background playback and close, including recovery from failed and disconnected player connections. The fixture is generated silent PCM audio; these checks do not synthesize production speech or alter a real account. Speaker quality, Bluetooth/call interruptions and human listening remain device acceptance checks in #17.

Build, lint and JVM checks pass. Nine audio Android tests pass on the emulator and Pixel 10a. Screenshots were inspected for text clipping, highlighting, controls and dialog visibility:

- [Playing with sentence highlighting](audio-screenshots/journal-audio-playing.png)
- [Paused after sentence navigation](audio-screenshots/journal-audio-paused.png)
- [Voice picker](audio-screenshots/journal-audio-voices.png)

The screenshots isolate playback for repeatable checks. Full Notebook parity, font scaling, dark paper and final visual polish remain in #14/#17. Compare with the audio/voice captures in [the iOS manifest](../ios-reference/2026-11-20/README.md).
