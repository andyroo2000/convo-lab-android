# Android Journal conversion engine

Refs #13. This ports the native engine and adapters from iOS `6dae16a` while preserving the existing Android romaji playground contract. The richer internal API accepts a complete reading, expanded-candidate requests, and UTF-16 clause boundaries. Responses carry whole-phrase text, sections, units and inference timing. No isolated-word inference is substituted for phrase context.

Explicit engine-backed selections can learn in an account-scoped app-private directory. Arbitrary strings cannot teach the model. Confirmed preferences survive reopening; reset clears both converter memory and explicit preferences. Android owns directory isolation under noBackupFilesDir; the native owner serializes calls. Benchmark instances omit a learning directory.

The iOS context-buffer reset is shared on Android. The model, dictionary and dependency revisions remain pinned. `prepare_typing.py` reapplies adapters from pristine pinned sources; repeated preparation is deterministic. The native constructor now warms and validates the model itself, avoiding a duplicate Kotlin warmup.

## Verification and limits

- arm64-v8a and x86_64 builds pass with Swift 6.4.2 / NDK r30.
- Build/lint and 107 JVM tests passed before integrating the newer appearance baseline.
- Six native/trace tests pass on both Pixel 10a and API 36 emulator. These exercise legacy romaji, repeatability, public candidate quality, expanded choices, boundary alignment, invalid input, validated learning, restart and reset.
- Public benchmark: 69/72 samples contain an accepted candidate on both devices. Pixel median 241ms, p95 335ms, max 354ms, preload 306ms; an earlier run measured p95 309ms. The pre-port Pixel p95 was 406ms. Emulator p95 160ms. These are inference wall times, excluding debounce/keyboard/rendering, and **do not meet the 150ms device target**.
- Expanded choices for a public 13-character phrase take roughly 650ms. **Cold CPU boundary reranking still takes around 40 seconds** because the iOS CPU path scores up to 160 complete sentences individually. This API is not connected to Journal controls yet. It needs optimization and responsive cancellation before that UI is enabled. The reports preserve the measured result rather than asserting usability.
- This PR supplies the native foundation, not the completed inline keyboard. Composition UI, account-owned engine lifecycle, learning settings and release packaging follow in #13.

Raw public-fixture reports are in [typing-engine-results](typing-engine-results/). No journal or credential data is used.
