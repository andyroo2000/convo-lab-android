# Draft / Final parity pass

Compared the stable iOS source at 6dae16a (`JournalFeedbackView`, `JournalVersionPicker`, `JournalVersionCopy`, `JournalEntryToolbar`) and reference screenshots 11–13 with the Android Journal.

- Draft now renders both the original feedback segments and a final-edit reply’s feedback, in order. Final continues to use the authoritative corrected document.
- More journal options provides Copy text in either view and Edit final in Final. Draft copying joins original and reply text with a blank line, matching iOS; correction explanations and furigana markup are not copied.
- Draft/Final selection persists per account and entry, survives recreation, and is removed during acknowledged account deletion. The account’s existing writer scope owns the preference writes and waits for them before cleanup. A preference-write failure leaves the on-screen selection usable; it never affects journal text.
- The rounded serif Draft/Final toolbar sits above the notebook paper, matching the iOS grouping. Android keeps its native menu, text selection, navigation and system keyboard behavior. Header layout adapts to available width and remains separate from writing controls.

Validation: three copy-contract unit tests, two device persistence/isolation/cleanup tests, and all three feedback UI tests pass on Pixel and emulator. The final toolbar layout was checked again on Pixel, including opening the final editor and keeping Save above the keyboard. Screenshots in `parity-screenshots` show the final Pixel layout. Debug build, lint and the complete unit suite pass.
