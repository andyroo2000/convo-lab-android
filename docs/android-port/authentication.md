# Android authentication foundation

Part of #5. The Android launcher now opens the Journal-branded sign-in flow. Invite registration and password-reset screens use the same API contracts as iOS 6dae16a. Successful login verifies the newly issued token against `/api/me` before publishing or storing the session. This foundation has a minimal signed-in shell; Journal data/editor and the full account menu are subsequent port issues, so #5 remains open until integration is complete.

Credentials and the verified profile share one Android Keystore AES-GCM encrypted AtomicFile in the app's no-backup directory. Plain preferences, logs and saved activity state never contain passwords or bearer tokens. Offline restoration retains the cached verified account; 401 removes the session. Operation generations reject obsolete login/restore responses. Local storage failure does not claim a successful sign-in or sign-out. Revocation uses the captured old token and is best effort after local sign-out.

The standalone typing playground is a separate debug-only activity. Instrumented typing tests launch it directly; production sign-in has no fixture bypass. Password fields use state-based secure text fields with memory-only state.

## Verification

- Nine JVM session tests cover offline restore, invalid tokens, old login/restore callbacks, failed reauthentication, write/removal failures, revocation failure and password reset acknowledgement.
- Five isolated instrumented tests verify wire spellings/token handoff and real Android Keystore encryption, reopen, deletion and tamper rejection. These passed on the Pixel 10a (Android 16).
- UI tests exercise sign-in/reset navigation, registration validation and keyboard visibility using fixture input only. No production account is created, modified or deleted by these tests.
- Use `./gradlew assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest` for the build gate, then the connected instrumentation suite on an unlocked device or emulator. Enable the emulator software keyboard. Set ANDROID_SERIAL when more than one device is connected.

The client base URL remains `https://convo-lab.com`. End-to-end production login requires the user's own interactive sign-in; contract tests are not a claim of production-account verification.

## Emulator screenshots

Captured on API 36 with isolated fixture input. Native Android text-field styling and scrolling adapt the iOS reference; the branded colors/fonts are shared. The keyboard-open assertion verifies that the submit button remains above the keyboard. Navigation resets scroll position for each form.

- [Sign in](auth-screenshots/auth-login.png)
- [Sign in with keyboard](auth-screenshots/auth-login-keyboard.png)
- [Registration validation](auth-screenshots/auth-registration-validation.png)
- [Password-reset confirmation](auth-screenshots/auth-reset-confirmation.png)

Validation at this change: 22 JVM tests and 13 emulator tests passed. After the final heading/capture/navigation adjustments, the build/lint/JVM gate and both authentication UI tests passed again. Five authentication contract/storage tests also passed on the Pixel. No physical-device UI pass is claimed while its secure lock screen is active.
