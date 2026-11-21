# Journal conversations

Part of #11: tutor questions can be answered inline. Saved replies appear in blue bordered cards with their original question, Draft/Final feedback, clarification, final editing and another composer at the current conversation leaf. Answers can be edited and resubmitted. The shared account repository preserves each unsent answer and its original parent/question in one atomic record before network submission. Stable child IDs and frozen payloads make retries safe even when the server created the child but its response was lost.

Refreshes preserve newer local writing. Root navigation cancels obsolete child work and waits for final writer flushes. A failed local save keeps the editor visible and blocks submission; Retry local save retries it. Confirmed discard is offered only for an unsent answer with no unacknowledged request. Conflicts retain the answer and offer Copy or Save as new entry. Root deletion checks child work and unsaved answers; an untouched empty composer does not block intentional deletion. Nested actions are hidden while the root is saving/deleting.

Cached child entries can reopen offline, and pending feedback uses the existing workflow checkpoints. Historical question cards currently reload from the server; broader offline media/question cache behavior remains in #12/#16. Native Android answer fields are an intermediate visual adaptation; handwriting/font/typing integration remains in #13/#14.

The Journal request wrapper now groups the HTTP method, path and optional body in a typed request. Existing Settings, feedback and tutor wire-contract tests cover this refactor.

## Validation

Eight new JVM tests cover frozen exact retries, acknowledgement with a newer local draft, question-specific restoration, root deletion safety, conflict copies, two-turn controller progression, navigation flushes and disk failures. Build, lint and 92 JVM tests pass. Three conversation UI tests plus nine existing API/storage contract tests pass on both emulator and Pixel 10a. The final discard-visibility/root-busy UI refinement is rechecked on the emulator.

Fixture screenshots, inspected against the iOS conversation references:

- [Answer with keyboard and visible Submit](conversation-screenshots/conversation-answer-keyboard.png)
- [Nested feedback](conversation-screenshots/conversation-reply-feedback.png)
- [Second turn with keyboard](conversation-screenshots/conversation-second-turn-keyboard.png)
- [Interrupted answer kept for exact retry](conversation-screenshots/conversation-answer-retry.png)

No tests change a production account. Question playback and final-text audio remain the next slice (#12).
