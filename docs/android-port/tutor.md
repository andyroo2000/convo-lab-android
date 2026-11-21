# Tutor questions and preference

Part of #11: ready Journal feedback now shows its tutor question with furigana, the original tutor portrait and an English translation toggle. The picker saves the preference for future questions; existing questions retain the backend’s versioned tutor identity. The two bundled portraits are exact copies of learning-os `resources/images/journal/professor-{female,male}-v2.png` at a45444e0b51c9d36c828bde8bf2d7335b222a857. Unknown future avatars fall back to the tutor name.

Question requests include the current analysis/reply identifiers. Source changes cancel obsolete requests and reject late UI publication. Opening the picker, recovering a failed catalog load, and saving a preference retain the question. This intermediate slice does not yet expose answer composition or question playback; those remain in #11/#12.

Four JVM tests cover historical attribution, catalog retry, late responses and an unassigned question. Two Android contract tests verify the wire identifiers and tutor/audio decoding. The UI test exercises English, both portraits and selection while preserving historical attribution. Build, lint and JVM tests pass; three Android tests pass on emulator and Pixel 10a. Screenshots below use fixture accounts without production mutations.

- [Question and English](tutor-screenshots/tutor-question.png)
- [Tutor picker](tutor-screenshots/tutor-picker.png)
- [Preference changed, historical tutor retained](tutor-screenshots/tutor-preference-changed.png)

Compare with the tutor/conversation captures in [the iOS reference manifest](../ios-reference/2026-11-20/README.md). Native Android dialogs and labeled controls are intentional adaptations. Appearance and accessibility across large font settings remain in #14.
