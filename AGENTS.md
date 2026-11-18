# ConvoLab Android agent instructions

- Build the native Android client with Kotlin and Jetpack Compose.
- Use the existing learning-os API. Preserve its wire contracts and stable retry identifiers.
- Device builds must use https://convo-lab.com. Never ship localhost or staging to Andrew's Pixel.
- Keep credentials out of Git, build artifacts, logs, and plain preferences. Use Android Keystore-backed storage when authentication is implemented.
- Preserve offline writes until acknowledged by the server. Scope local records, work, and caches by account.
- Keep networking, persistence, synchronization, and playback outside composable UI functions.
- Before changing existing source, run CodeScene MCP code_health_review for a baseline. Review all created/changed source and tests after edits; repeat after further edits.
- Fix new findings and Code Health declines. Run pre_commit_code_health_safeguard on staged changes before commits and analyze_change_set against verified origin/main before PR creation or updates. Confirm intended files were checked.
- If MCP analysis fails, run its diagnostics and report unmet gates. For confirmed unsupported formats, use language/framework checks and disclose the limitation.
- Run ./gradlew assembleDebug lintDebug testDebugUnitTest before handing off source changes. Add meaningful tests as behavior is introduced; do not add placeholder tests.
- Use codex/ branches. Put temporary worktrees under /Users/andrewlandry/.codex/worktrees and remove them only after checking for valuable changes and safe preservation.
