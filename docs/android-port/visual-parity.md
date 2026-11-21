# Journal visual parity audit — 2026-11-21

The canonical design is iOS `00d1fea`, including its latest paper dialogs and
pickers. [Current reference captures](../ios-reference/2026-11-21/README.md)
replace the older screenshots where the design changed.

## Audit and changes

| Surface | Difference addressed |
| --- | --- |
| Authentication | Cover typography, notebook frame, square white form panel, external field labels, sign-up/reset link placement, blue submit controls |
| Main navigation | Single-row ConvoLab/Journal header, Contents icon, ringed account initial; stacked layout at large text sizes |
| Account menu | Paper popover with Journal and Account groups, tutor/appearance/keyboard access, logout confirmation |
| Settings | Rounded paper sheet, circular close control, icon disclosure rows, account initial, ordered account actions; WaniKani disclosure within Connections |
| Profile/password | Paper fields, serif labels, sage save buttons, scrollable keyboard-safe forms |
| Account deletion | Paper confirmation sheet; existing password requirement and cleanup behavior preserved |
| WaniKani | Inline connection disclosure and styled connect/sync controls; no daily-vocabulary-transfer settings |
| Appearance | Two-column font cards, selected checkmarks, Japanese preview, fine slider and round thumb |
| Keyboard choice | Paper sheet and selected choice cards; existing on-device engine, learning, and keyboard ownership retained |
| Draft notebook | Header and paper spacing, actions directly below the page when the keyboard is closed; fixed reachable actions while typing |
| Reviewed entry | Draft/Final capsule controls, handwriting title/date, paper menu, removal of draft-only footer actions |
| Corrections | Anchored white popover with green original/replacement passage, yellow original text, compact ruby, serif Japanese, bounded scrolling and outside/Back dismissal |
| Final editing | Root editor inside the notebook with header retained, matching date/title/paper/rules, locked version/navigation controls, visible save/cancel actions; reply edits use the same editor in an isolated full-screen dialog so the conversation stays mounted |
| Contents | Paper drawer, close control, white list rows, compact native-style row typography, paper action menus |
| Rename/delete/close | Compact paper sheets, green top edge, consistent capsule actions and deletion preview |
| Clarification | Paper card with green edge, white/sage choices, “Something else” answer, matching submit controls |
| Tutor choice | Portrait cards and selected borders; choice available from the account menu before an entry has feedback |
| Tutor conversation | Compact English toggle, portrait/audio header, reply paper with rules, matching submit control |
| Narration | Blue portrait/player area above final paper, round playback control, thin progress slider, voice portrait grid |
| Accessibility/system | Large-text layout checks, input labels, scrollable long explanations, native Android Back and IME handling |

Study, Readings, Daily Audio, Cards, Time, calendar sync, Satori Reader shortcut
detection, downloaded-media management, and daily vocabulary transfer remain out
of scope. The typing engine and its performance settings are unchanged.

## Verification

- iOS reference run: 17 UI tests passed.
- Debug/release builds, lint, and all 140 JVM tests pass.
- Pixel 10a / Android 16: the targeted UI suite covers authentication, account
  deletion, Settings/WaniKani, notebook navigation, feedback/final editing, tutor
  conversations, audio, appearance, and both keyboard modes: 32 distinct UI tests
  pass across the full pass and final 13-test editor/deletion rerun. One initial
  invocation used the wrong deletion-test package name; the corrected rerun passes.
  PR emulator results are recorded in the PR.
- [Pixel screenshots](visual-parity-screenshots/README.md) include complete notebook
  screens and isolated component fixtures; the gallery labels the latter.
- CodeScene MCP reviewed all 43 changed Kotlin files: no remaining findings.
  Three Compose-only files (`AccountMenu`, `JournalApp`, `ConvoLabTheme`) return
  no numeric score; build/lint/UI checks also cover those files. Staged and branch
  change-set coverage is checked before publishing.
- Pixel tests use isolated fixtures and replace the APK without clearing the
  installed account. Credential fixtures cancel their autofill sessions before
  leaving a form, avoiding spurious 1Password save prompts on the owner's phone.
- The system keyboard, status/navigation bars, and platform permission prompts
  remain Android UI. Screenshot comparisons focus on the app content.
