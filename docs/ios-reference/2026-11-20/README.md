# Android Journal port reference

Reference: [iOS 6dae16a](https://github.com/andyroo2000/convo-lab-ios/commit/6dae16a), captured 2026-11-20 on iPhone 16e / iOS 26.2. This snapshot includes Journal-only Settings, notebook correction styling, section-aware Japanese typing and confirmed-conversion learning.

[Port tracker #4](https://github.com/andyroo2000/convo-lab-android/issues/4) is the source of implementation status. A screenshot is a design reference, not evidence of Android completion.

## Scope

Journal and reduced Settings only, for every Android account including administrators. Keep Journal narration/tutor audio, keyboard choices and learning, account operations, appearance and WaniKani known-kanji sync. Omit Study, Daily Audio, Readings, Cards, Time, daily vocabulary transfer, Calendar, Satori shortcut detection and downloaded-media management. Do not port the independent Time UI or its setup flows.

## Implementation sequence

- [Port branded authentication and the Journal-only app shell](https://github.com/andyroo2000/convo-lab-android/issues/5) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Auth/LoginView.swift)
- [Port account profile, password, sign-out and account deletion settings](https://github.com/andyroo2000/convo-lab-android/issues/6) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Settings/SettingsView.swift)
- [Build account-scoped Journal persistence and versioned API contracts](https://github.com/andyroo2000/convo-lab-android/issues/7) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalStore.swift)
- [Port the notebook editor, Contents, resume, rename and deletion](https://github.com/andyroo2000/convo-lab-android/issues/8) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalEntryView.swift)
- [Port submission, meaning clarification and feedback polling](https://github.com/andyroo2000/convo-lab-android/issues/9) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalClarificationModel.swift)
- [Port Draft/Final feedback, corrections, furigana and final editing](https://github.com/andyroo2000/convo-lab-android/issues/10) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalFeedbackView.swift)
- [Port tutor selection, questions and multi-entry conversations](https://github.com/andyroo2000/convo-lab-android/issues/11) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalConversationView.swift)
- [Port Journal narration, voice selection and sentence playback](https://github.com/andyroo2000/convo-lab-android/issues/12) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalAudioControls.swift)
- [Integrate on-device Japanese input with Journal and learned conversions](https://github.com/andyroo2000/convo-lab-android/issues/13) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalTypingCoordinator.swift)
- [Port Journal appearance, fonts and accessible adaptive layouts](https://github.com/andyroo2000/convo-lab-android/issues/14) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalAppearance.swift)
- [Port WaniKani connection, manual sync and known-kanji furigana](https://github.com/andyroo2000/convo-lab-android/issues/15) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/WaniKani/KnownKanjiService.swift)
- [Harden lifecycle, offline recovery and production Android packaging](https://github.com/andyroo2000/convo-lab-android/issues/16) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/App/RootView.swift)
- [Audit iOS parity and perform Pixel acceptance checks](https://github.com/andyroo2000/convo-lab-android/issues/17) — [iOS source](https://github.com/andyroo2000/convo-lab-ios/blob/6dae16a/ConvoLab/Journal/JournalEntryView.swift)

## Visual and behavior notes

- Header: green ConvoLab brand, fixed Journal title, Contents action and account initial. No mode switcher. Account menu groups Journal preferences above Settings and sign-out.
- Paper: warm cream, muted red margin, thin blue-gray ruled lines, shallow torn edge, Klee One default. Body grows with text. Draft title/date treatment differs from reviewed entries.
- Contents: merge device drafts over server entries; date/id descending; current-entry check; rename/delete row actions; paging, refresh and New entry. Empty notebook can resume unfinished writing.
- Draft: typing persists locally. Close offers keep/close, delete, or keep writing. Keeping may fail online while retaining the device copy; never imply it synced. Submit first saves then requests clarification and analysis.
- Clarification: replace the current question as answers arrive; support choices and free text, thinking, failure/retry, cancellation and source-version checks. No analysis submission before clarification resolves.
- Feedback: Draft/Final selector displays one version; Draft has highlighted segments and informational popovers. Final supports copy/edit and narration. Revision conflicts retain user edits. Legacy currentReply/correctedDocument shapes still matter.
- Conversation: each answer becomes an idempotent child entry with its own analysis and optional clarification. Preserve tutor attribution on old questions. Persist pending answer and exact request before sending.
- Audio: narrator catalog plus saved voice, timed sentence navigation/highlighting, play/pause/seek, question audio readiness/retry. Private cache keys must include account, entry, analysis revision and voice.
- Japanese input: first-use choice between English keyboard with built-in conversion and device Japanese keyboard. Inline caret-aware composition, expanded candidates, section adjustment, punctuation/cursor edits, selection replacement, paste, confirmed learning and reset apply to root/final/reply editors.
- Settings: Account / Connections / Experiments accordion. Account profile and password panels preserve the active journal. WaniKani remains useful without Study through adaptive furigana. Never retain the WaniKani API token locally.
- Appearance: Klee One / sans / serif plus 16–30 base size; preserve Dynamic Type equivalent scaling. Use Android-native navigation/insets/IME semantics. Keep paper readable under dark system appearance.

## Contracts and recovery

Journal JSON intentionally uses camelCase; auth token/password payloads include snake_case fields, while registration inviteCode stays camelCase. Journal resources usually use a data envelope; known-kanji responses are unwrapped. Read source contracts before implementing endpoints.

Draft writes are account-scoped, atomic and carry a stable ULID plus edit revision. The server uses expectedVersion / expectedRevision for conflicts. Acknowledging an earlier sent edit must not delete a newer local edit. Unknown/corrupt local data must be retained for recovery. Session changes invalidate pending callbacks; offline transport failure does not invalidate a cached verified login. Delete local account data only after server-confirmed account deletion.

## Verification and deferred device checks

Each implementation PR links its issue, tests, Android screenshots and any intentional platform differences. Run CodeScene MCP on changed supported source, staged changes and the verified base; resolve significant Claude review feedback and verify CI before merging. Keep simulator fixtures isolated from production credentials and APIs. Builds for Andrew use https://convo-lab.com.

Emulator checks cover small/large screens, portrait/landscape, keyboard open, font scaling, loading/empty/error states, offline/restart recovery and synthetic account switching. Use public sample text and fixture accounts in screenshots. No real account deletion, password change or paid generation is performed for automated tests.

Andrew is unavailable for Pixel testing until approximately noon 2026-11-21 (America/New_York). Continue implementation and emulator verification meanwhile. Real Gboard/Samsung keyboard behavior, sustained neural-conversion latency/thermals, audio routing and device lifecycle remain pending in issue #17. Do not call the device test complete based on emulator evidence.

## Captures

The capture manifest maps preserved XCTest attachment names to image filenames. Existing iOS UI tests launch isolated fixture services and sample journal entries, never the production AppModel data store. Portrait assets may use fixture placeholders where no portrait URL is provided; the production portrait layout and retrieval behavior are still required.

### Capture provenance and limitations

21 unedited PNGs are mapped in [manifest.json](manifest.json). The pinned iOS app source is unchanged; [PortReferenceUITests.swift](PortReferenceUITests.swift) is an additional local capture harness. To reproduce, add it to ConvoLabUITests at the pinned revision, prepare that repository’s native typing dependencies, and run `xcodebuild test -project ConvoLab.xcodeproj -scheme ConvoLab -destination 'platform=iOS Simulator,name=iPhone 16e' -only-testing:ConvoLabUITests/PortReferenceUITests`. Export attachments with `xcrun xcresulttool export attachments`.

Four capture tests passed in ios-portrait.xcresult. The writing/clarification test initially failed because the isolated navigation fixture has no save route for newly generated root-entry IDs; it returns an offline error. That failed clarification image is excluded. The final harness captures writing, relaunches the fixture, and uses its supported conversation route. That rerun passed in ios-conversation.xcresult, producing the remaining references. These are design captures, not a claim that the full iOS regression suite passed.

The final-editor image shows the keyboard obscuring the lower controls. Android must keep Save/Cancel reachable above the IME, including on compact screens, rather than reproduce this limitation. Avatar placeholders are fixture data, not the intended production portraits. Audio/connection screenshots establish appearance, not real playback or WaniKani-service verification.

| State | Reference |
| --- | --- |
| login | [01-login.png](01-login.png) |
| registration | [02-registration.png](02-registration.png) |
| password reset | [03-password-reset.png](03-password-reset.png) |
| empty notebook | [04-empty-notebook.png](04-empty-notebook.png) |
| account menu | [05-account-menu.png](05-account-menu.png) |
| settings | [06-settings.png](06-settings.png) |
| profile | [07-profile.png](07-profile.png) |
| wanikani | [08-wanikani.png](08-wanikani.png) |
| appearance | [09-appearance.png](09-appearance.png) |
| contents | [10-contents.png](10-contents.png) |
| draft feedback | [11-draft-feedback.png](11-draft-feedback.png) |
| final narration | [12-final-narration.png](12-final-narration.png) |
| final editor keyboard | [13-final-editor-keyboard.png](13-final-editor-keyboard.png) |
| narration voices | [14-narration-voices.png](14-narration-voices.png) |
| writing keyboard | [15-writing-keyboard.png](15-writing-keyboard.png) |
| clarification | [16-clarification.png](16-clarification.png) |
| clarification next | [17-clarification-next.png](17-clarification-next.png) |
| keyboard choice | [18-keyboard-choice.png](18-keyboard-choice.png) |
| tutor picker | [19-tutor-picker.png](19-tutor-picker.png) |
| tutor conversation | [20-tutor-conversation.png](20-tutor-conversation.png) |
| close draft | [21-close-draft.png](21-close-draft.png) |
