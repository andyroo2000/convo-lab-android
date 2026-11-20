# Notebook and Contents integration

Part of #8 and #5. Signed-in accounts now open the notebook. The controller and private disk store are scoped to the active account; disposing that account UI flushes accepted local edits. Keystrokes update the editor immediately and coalesce per entry on the disk writer. Navigation waits for local persistence; a failed write retains both the visible text and its queued retry. Local autosave and server acknowledgement are shown separately.

Contents combines cached server entries and local drafts in date/ID order, supports refresh/pagination/new entry/selection/rename/delete, and resumes a local draft after restart. Close offers keep-and-close, delete and keep-writing. A failed server save leaves the close sheet and writing intact. Pending deletion disables writing and offers retry; conflicts/missing remote entries offer save-as-new recovery while retaining the original local copy.

The paper uses the iOS brand colors, Klee font, ruled lines, red margin and torn lower edge. Native Android sheets/buttons and a two-line compact header adapt the reference. The footer stays above the keyboard and the editor scrolls with long text. The current action is **Save draft**; submission/clarification/feedback is the next feature slice (#9/#10), not simulated by this button. Tutor selection/avatar, appearance and integrated conversion follow in their own issues.

## Verification

Six controller tests cover latest-text persistence across entry switches, failed keep-and-close, blocked navigation after a disk failure and successful retry, offline resume, empty scratch cleanup and the Unicode code-point limit. Two emulator UI tests check real keyboard bounds for short/long entries, Contents selection and the close sheet. All test data is synthetic and API responses are fake.

Screenshots captured on API 36, 1080×2400, using production notebook components and a sample account:

- [Empty notebook](notebook-screenshots/notebook-empty.png)
- [Writing with keyboard](notebook-screenshots/notebook-keyboard.png)
- [Long entry with keyboard](notebook-screenshots/notebook-long-keyboard.png)
- [Contents](notebook-screenshots/notebook-contents.png)
- [Close draft](notebook-screenshots/notebook-close-draft.png)

The screenshots were visually checked against iOS references 04, 10, 15 and 21. There is no physical-device UI pass while the Pixel remains locked; underlying storage/API checks have passed on the Pixel.
