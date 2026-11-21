# Current iOS visual reference

Canonical source: `andyroo2000/convo-lab-ios` commit
`00d1feaef6733b1953fe72f838f6ae06e5ff830a` (2026-11-21).
This includes the notebook picker/control and entry-dialog redesigns in #366 and
#367. The 2026-11-20 captures predate those changes.

Captured on iPhone 16e, iOS 26.2, from an isolated reference checkout. All 17
selected UI tests passed. The captures use the app's isolated fixture composition;
no production account or journal was used. Some fixtures expose the admin mode
switcher; Android remains Journal-only. Font-size tests deliberately change the
Journal font or device text scale.

The original XCTest attachment names and test IDs are in
[capture-manifest.json](capture-manifest.json). Selected images are named by
screen for convenient comparison:

- [Correction popover](correction-popover.png)
- [Account menu](account-menu.png), [Settings](settings.png), [Profile](profile.png)
- [Appearance](appearance.png), [Narrator picker](narration-voices.png), [Tutor picker](tutor-picker.png)
- [Empty notebook](empty-notebook.png), [Final/audio](final-audio.png), [Final editor](final-editor.png)
- [Contents](contents.png), [Rename](rename.png), [Deletion](delete.png)
- [Tutor reply and keyboard](tutor-reply-keyboard.png)

Authentication and keyboard-choice references from 2026-11-20 remain applicable;
the corresponding iOS source was also checked at the commit above.
