# Account deletion

Settings → Account → Delete Account requires the current password and explains that deletion removes the entire ConvoLab / Learning OS account, including study data outside this Android app. Cancel performs no request. The password uses the existing secure text field, is not saved across recreation, and is cleared when submitted.

The bearer-token contract is DELETE /api/me with current_password; the server acknowledges with an empty 204 response. A rejected password or unacknowledged network request preserves local data. No real account was deleted during development: all automated deletion calls use a fixture transport.

After acknowledgement, an atomic per-account cleanup marker survives restart. Cleanup blocks new account work, cancels and joins Journal, feedback/conversation, WaniKani, playback and typing owners, removes only matching credentials, then removes that account’s journal, journal-workflow, journal-audio, known-kanji and journal-typing directories. Other accounts remain intact. Directory traversal does not follow symlinks. A failed cleanup retains its marker and exposes Retry cleanup; startup retries before restoring sign-in. If writing the acknowledgement marker itself fails, the process retains the intent in memory and signs out, reporting the failure rather than claiming cleanup finished.

A process death before the server response/marker cannot prove deletion was acknowledged. The app therefore preserves unconfirmed local writing instead of guessing that it should be erased.

Validation: six deterministic unit checks cover rejected password, final-write ordering, restart/retry, account replacement, failed marker write and unreadable startup markers. Five Android contract/storage checks pass on Pixel and emulator, including account isolation and symlink handling. Two confirmation UI checks and the storage checks pass on Pixel; screenshot records the password/confirmation layout. Build, lint and the full unit suite pass. No production deletion requests are part of these checks.
