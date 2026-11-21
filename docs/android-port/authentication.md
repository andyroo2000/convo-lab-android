# Android authentication foundation

Part of #5. The Android launcher now opens the Journal-branded sign-in flow. Invite registration and password-reset screens use the same API contracts as iOS 6dae16a. Successful login verifies the newly issued token against `/api/me` before publishing or storing the session. This foundation has a minimal signed-in shell; Journal data/editor and the full account menu are subsequent port issues, so #5 remains open until integration is complete.

Credentials and the verified profile share one Android Keystore AES-GCM encrypted AtomicFile in the app's no-backup directory. Plain preferences, logs and saved activity state never contain passwords or bearer tokens. Offline restoration retains the cached verified account; 401 removes the session. Operation generations reject obsolete login/restore responses. Local storage failure does not claim a successful sign-in or sign-out. Revocation uses the captured old token and is best effort after local sign-out.

The standalone typing playground is a separate debug-only activity. Instrumented typing tests launch it directly; production sign-in has no fixture bypass. Password fields use state-based secure text fields with memory-only state.

## Verification

- Eleven JVM session tests cover offline restore, invalid tokens, old login/restore callbacks, failed reauthentication, write/removal failures, revocation failure and password reset acknowledgement.
- Seven isolated instrumented tests verify wire spellings/token handoff and real Android Keystore encryption, reopen, deletion and tamper rejection. These passed on the Pixel 10a (Android 16).
- UI tests exercise sign-in/reset navigation, registration validation and keyboard visibility using fixture input only. No production account is created, modified or deleted by these tests.
- Use `./gradlew assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest` for the build gate, then the connected instrumentation suite on an unlocked device or emulator. Enable the emulator software keyboard. Set ANDROID_SERIAL when more than one device is connected.

The client base URL remains `https://convo-lab.com`. End-to-end production login requires the user's own interactive sign-in; contract tests are not a claim of production-account verification.

## Emulator screenshots

Captured on API 36 with isolated fixture input. Native Android text-field styling and scrolling adapt the iOS reference; the branded colors/fonts are shared. The keyboard-open assertion verifies that the submit button remains above the keyboard. Navigation resets scroll position for each form.

- [Sign in](auth-screenshots/auth-login.png)
- [Sign in with keyboard](auth-screenshots/auth-login-keyboard.png)
- [Registration validation](auth-screenshots/auth-registration-validation.png)
- [Password-reset confirmation](auth-screenshots/auth-reset-confirmation.png)

Validation: the initial full 13-test emulator suite passed. The final JVM suite contains 26 tests, including cancellation, response-size limits and non-transient credential recovery. After the final heading/capture/navigation adjustments, the build/lint/JVM gate and both authentication UI tests passed again. Seven authentication contract/storage tests also passed on the Pixel. No physical-device UI pass is claimed while its secure lock screen is active.

Review hardening: newly issued tokens are revoked best-effort when verification, credential persistence or generation acceptance fails. Cleanup uses only the unused token and keeps the active session intact. Registration values survive retryable failures; cancellation and successful completion clear secret form fields. The expanded session matrix tests successful and unauthorized restore responses after both login and sign-out. A failed credential read is never interpreted as an authenticated session.

Review hardening: non-transient credential corruption resets only the saved sign-in and its Keystore key; transient I/O errors preserve the encrypted record. Journal drafts are separate and never removed by this recovery. Cancellation disconnects in-flight HTTP requests. Registration failures offer a sign-in recovery hint for a lost creation response. Sign-out revocation is best effort with bounded timeouts; an offline revocation is not queued and may leave the server token valid until revoked server-side.

Credential error classification: only deterministic authentication-tag/ciphertext, permanently invalid or unrecoverable key, JSON and schema failures reset the saved sign-in. Temporary KeyStore/provider/I/O failures preserve it. A permanently unusable encryption key can be renewed when writing a new session. The transient-key test injects a temporary key-access error, verifies byte-for-byte preservation and successful reopening with the original key.
