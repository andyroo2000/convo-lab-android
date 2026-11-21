# Submission, clarification, and corrected Journal text

Implements the next part of #9 and #10 against the pinned iOS reference, after the notebook/storage foundations. Tutor conversation and narration remain the next separate slices (#11/#12).

- Submit first flushes local writing and saves the draft. Only its acknowledged server version can start clarification and analysis. Editing hides feedback from the old version.
- Queued/processing jobs poll off the UI thread. Read failures have bounded retries; returning to the draft, switching entries, or changing accounts cancels obsolete work. Generation checks reject stale callbacks.
- Answers are checkpointed before POST with their clarification/question identifiers. A lost response retries the same answer, including after reopening the app. A read-only refresh never starts a submission.
- Draft/Final tabs display correction highlights and explanations, Japanese dates, handwriting, and inline furigana. Plain text, punctuation, supplementary characters, and paragraph breaks are preserved. The parser supports hiding readings for fully known words, ready for WaniKani integration.
- Final editing captures the opening analysis/revision. Save persists that exact revision and text before the request. A genuine conflict keeps the edit; an already-applied lost-response retry is reconciled only when the server's analysis and exact text match. Cancel explicitly discards the pending final edit. Unsubmitted final-editor text survives rotation in the retained workspace; process-death preservation before Save is tracked in #16.
- Each account's raw analysis/clarification response and pending work live in atomic no-backup files. Unknown-schema/corrupt files are preserved. No credentials or real user Journal data appear in fixtures.

## Verification

Build, Android lint, and JVM tests pass. New behavior tests cover lost/cancelled answer requests, stale versions, final-edit conflict/lost-response reconciliation, read retries, stale callbacks, bounded polling, furigana parsing/known-kanji filtering, and notebook-to-workflow binding.

Four contract/AtomicFile tests passed on the connected Pixel 10a (Android 16) while locked. Two emulator UI tests verify custom clarification answers and long final edits with the software keyboard present; the captured screens were visually inspected. That inspection caught and fixed clipped multi-kanji ruby text. The tests use isolated fixtures and never submit production entries.

| Android capture | iOS reference |
| --- | --- |
| [Draft feedback](feedback-screenshots/feedback-draft.png) | [Draft](../ios-reference/2026-11-20/11-draft-feedback.png) |
| [Final](feedback-screenshots/feedback-final.png) | [Final and narration](../ios-reference/2026-11-20/12-final-narration.png) |
| [Clarification](feedback-screenshots/feedback-clarification.png) | [Clarification](../ios-reference/2026-11-20/16-clarification.png) |
| [Answer keyboard](feedback-screenshots/feedback-clarification-keyboard.png) | [Clarification next](../ios-reference/2026-11-20/17-clarification-next.png) |
| [Final-edit keyboard](feedback-screenshots/feedback-final-editor-keyboard.png) | [Final editor](../ios-reference/2026-11-20/13-final-editor-keyboard.png) |

Native Android dialogs and keyboard insets are intentional adaptations. Final parity still includes tutor/audio controls, appearance settings, the integrated English-to-Japanese engine, and the user's physical-device acceptance test.
