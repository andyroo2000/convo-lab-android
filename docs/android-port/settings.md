# Account settings and WaniKani

This slice of #6/#15 adds the Settings account-menu entry and native paper-colored panels for Account, Connections, and Experiments. Profile/password changes, sign-out, and the Japanese typing playground are available. Account deletion and complete local-data cleanup remain in the lifecycle/account-completion slice (#16); no nonfunctional deletion control is shown in this intermediate build.

WaniKani supports connect, sync, refresh, and confirmed disconnect. It uses the existing unwrapped known-kanji API contract. The provider token lives only in a secure input field and its active request, is cleared after connecting or closing the field, and is never cached. Sync has a bounded two-minute read timeout and cancellation still disconnects the network call. Cached knowledge is account-scoped and atomic; an older server knowledge version cannot replace a newer snapshot. Disconnect follows the server's preserved known-kanji set.

Journal reading views consume the active account's known-kanji set. A reading is hidden only when all kanji in that word are known; partial knowledge keeps the reading visible. Profile updates retain the existing session token and notebook workspace, and stale profile responses cannot change a replacement account.

No Study, daily-vocabulary transfer, Calendar, Satori Reader, downloaded-media, Cards, Time, Readings, or Daily Audio controls are included.

## Validation

Build, lint and JVM tests cover connection success followed by sync failure, explicit sync retry, older snapshot rejection, disconnect preservation, profile persistence and late responses after account replacement. Android contract tests verify JSON keys, routes, empty password responses, account-isolated atomic cache recovery, and absence of provider tokens or transfer settings from the cache.

Two UI tests exercise profile/password forms and WaniKani connect/sync/confirmed disconnect with the keyboard present. All five UI/contract tests passed on the emulator and Pixel 10a. The final test-fixture refactor also passes on the emulator; production behavior is unchanged.

| Android capture | iOS reference |
| --- | --- |
| [Account](settings-screenshots/settings-account.png) | [Settings](../ios-reference/2026-11-20/06-settings.png) |
| [Profile keyboard](settings-screenshots/settings-profile-keyboard.png) | [Profile](../ios-reference/2026-11-20/07-profile.png) |
| [Password keyboard](settings-screenshots/settings-password-keyboard.png) | Account password editor |
| [WaniKani token](settings-screenshots/settings-wanikani-token-keyboard.png) | [WaniKani](../ios-reference/2026-11-20/08-wanikani.png) |
| [WaniKani connected](settings-screenshots/settings-wanikani-connected.png) | [WaniKani](../ios-reference/2026-11-20/08-wanikani.png) |

All screenshots use isolated fixtures, never a real password, provider token, or live account mutation. Native Android dialogs and keyboard insets are intentional adaptations; final appearance/accessibility polish remains tracked in #14.
