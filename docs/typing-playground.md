# Android Japanese typing playground

The main app opens authentication and Journal. Debug builds also contain a separate
standalone typing lab. After signing in, open the account menu → **Japanese typing
lab**, or Settings → **Japanese typing playground**. The activity is not exported,
so it cannot be launched directly from adb. Automated tests launch it inside the
app without a real account.

The lab uses a scratchpad rather than Journal data. Release builds include the
Journal engine, model and dictionary, but exclude the lab activity and test fixtures.
Use an English keyboard in **Romaji**, then tap a candidate, tap
**かな**, or press the keyboard's Done button to append the first candidate to the
scratchpad. Edit/copy the scratchpad with normal Android text controls. Scratchpad
and composition survive rotation in memory but are discarded when the screen's
ViewModel is destroyed; they are not saved to preferences, files or saved state.
The integrated Journal layout is documented separately in
[Journal keyboard](android-port/journal-keyboard.md) and [composition](android-port/composition.md).

Conversion is entirely on-device: AzooKey v0.11.2
`80b8204f1cdfb364bb2ed355cf52c7ebb2519a0c`, llama.cpp b4846
`10131b23ee0dbd9d03dfc618cec9fe3402919277`, and the same unchanged Zenz v3 small
Q5_K_M model used on iOS. The download and extracted model are SHA-256 verified; startup validates the
marker, model size/hash and dictionary index files. Interrupted or damaged copies
are rebuilt in a staging directory and atomically renamed. Startup failures expose
a Retry action. The model and dictionary
are packaged in both debug and release APKs, then copied to app-private, non-backed-up storage. No
runtime download is needed. The lab disables personal learning and emoji prediction;
Journal has separate account-scoped confirmed learning.
**Credits** includes the model's CC BY-SA 4.0 attribution and dependency licenses.

The Kotlin/Compose app calls a Swift shared library through a small JNI bridge.
Swift's built-in romaji table handles kana composition; `n'` and `n’` disambiguation
are normalized before it. All native access and disposal are serialized, including
across successive screen instances. Conversion never runs on the UI thread.
A 35 ms debounce coalesces rapid edits and obsolete results are discarded. Up to
32 exact-input results are retained in memory for the current composition, so
backspacing and retyping can restore candidates immediately. Clearing or committing
the composition discards that cache. Uncached input clears old candidates immediately. Input is limited to one
120-UTF-16-unit phrase (80 converted characters). Longer text belongs in the
scratchpad, entered in several phrases. No silent dictionary-only fallback is used
if neural loading fails.

The lab offers the normal ten whole-phrase candidates. Journal additionally includes
the expanded 160-candidate pool and section/boundary adjustment, using CPU inference
and exact-score prefix reuse. Metal acceleration does not apply to Android. See the
[current Pixel performance results](android-port/typing-performance.md).

## Build on this Mac

Android Studio and JDK 17 are installed. Native builds additionally require the
open-source Swift **6.4.2** toolchain and matching Android SDK (Xcode's Swift is not
interchangeable), NDK `30.0.16248370`, and CMake `3.31.6`.

```sh
sdkmanager 'ndk;30.0.16248370' 'cmake;3.31.6'
# With open-source Swift 6.4.2 on PATH, once:
bash scripts/setup_swift_android.sh
python3 scripts/prepare_typing.py
python3 scripts/build_typing.py --abi arm64-v8a
python3 scripts/build_typing.py --abi x86_64
./gradlew assembleDebug lintDebug testDebugUnitTest
```

On Andrew's Mac, set `TYPING_SWIFT` to
`/Users/andrewlandry/Library/Developer/Toolchains/swift-6.4.2-RELEASE.xctoolchain/usr/bin/swift`
for the Python build commands. The installed Swift SDK is already prepared.
Native libraries, dictionary, model and vendor checkouts are generated/ignored;
a missing preparation step fails APK packaging instead of producing a broken
app. JVM-only tests do not require these assets. Both ARM64 (Pixel / Apple Silicon emulator) and x86_64 (CI emulator)
are built. Native libraries use 16 KB-compatible alignment.

The pinned upstream source is prepared with `scripts/patch_typing.py` and the checked-in
Android adapters. `native/cpu_model.cpp` wraps the
model-loading call at link time to select `LAYER` with zero GPU layers: b4846's
`NONE` mode validates a GPU index even for CPU-only loading. Personalization's
legacy API is stubbed for the lab; Journal persists confirmed conversions through
its account-scoped learning layer.

## Tests, screenshots and performance

Start `ConvoLab_API_36` in Android Studio's Device Manager, then:

```sh
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell svc power stayon true
python3 scripts/test_typing.py
```

Unit tests cover request coalescing, stale native results, commit validation,
input limits, errors, percentile calculation, cache eviction and immediate cache hits. Instrumented tests use the actual
bundled engine for doubled consonants, contracted sounds, terminal n, apostrophe
n, Unicode, reset behavior and the shared 24 iOS fixtures. UI tests exercise a long
scratchpad, candidate commit, rapid English input and rotation, assert controls
are above a visible keyboard, and save portrait/landscape screenshots. CI runs
these tests on API 36 with emulator Wi-Fi and mobile data disabled.

Reports and screenshots are collected under
`app/build/typing-artifacts/` and uploaded with
GitHub Actions test results. `typing-trace.json` measures 114 public romaji edits
across three phrases, including deletion and retyping. It includes session
scheduling, debounce and conversion, but excludes keyboard events and drawing.
The trace asserts that repeated edits preserve candidates and avoid native work.
The test framework's reported edit latency includes
synchronization overhead; it is not a physical key-to-photon measurement.

**Benchmark** runs the 24 public phrases three times and copies a JSON report to
the clipboard on request. It never includes scratchpad text. Reports identify the
model, upstream revisions, device/ABI, app version, preload, wall/native durations,
accepted candidate coverage, native context-reset duration, sampled PSS memory and end-of-run thermal state.
The first pass and repeated passes remain individually visible. Timing excludes
keyboard events, debounce and rendering. PSS is sampled, not a true peak.

The usability target is p95 conversion wall time <=150 ms. The automated smoke
gate is intentionally broader (<2 seconds) to detect stalls on shared CI machines;
it does not certify the usability target. Emulator results measure this Mac or
the CI host, not the Pixel. The Pixel has now been tested; see the current results
linked above. The dated
measurements below describe the earlier lab implementation. Continue comparing
cold/warm performance and thermals before claiming the usability target is met.

## Initial local measurements (2026-11-18)

API 36 ARM64 emulator on this Mac, CPU path, 72 conversions: median 90.4 ms,
p95 151.3 ms, maximum 205.1 ms; highest sampled process PSS 223.2 MiB. An accepted
answer was first for 22/24 phrases and present in the candidate list for 23/24,
consistently across three passes. Preload in this run was a warm reload (82 ms),
not a fresh-install/cold-filesystem measurement. Earlier measurements under
concurrent build load were slower (p95 286 ms). The 150 ms target is **not yet
passed** by these measurements, and physical-device performance remains unverified.

The UI test measured 350 ms from its final edit to observing candidates and a
425 ms p95 test-driven edit duration. These include test-runner synchronization
and are useful regression signals, not direct touch responsiveness measurements.
Portrait and landscape screenshots were inspected with the real English Gboard
visible; the candidate row and Romaji field were fully above it in both cases.

## Responsiveness comparison (2026-11-19)

Same API 36 ARM64 emulator and unchanged model, 114 settled edits across three
public phrases. Baseline used the original 80 ms debounce and no result cache;
the updated session uses 35 ms and a 32-entry composition-local cache.

| Session measurement | Baseline | Updated |
| --- | ---: | ---: |
| All edits, median | 141.6 ms | 68.6 ms |
| All edits, p95 | 165.9 ms | 173.1 ms |
| Fresh inputs, median | 135.4 ms | 88.8 ms |
| Fresh inputs, p95 | 185.5 ms | 243.9 ms |
| Revisited inputs, median | 146.9 ms | 0.18 ms |
| Revisited inputs, p95 | 165.0 ms | 0.41 ms |
| Native conversions | 114 | 66 |

These are session-to-result timings, not physical keystroke/display timings.
The median improves; this first comparison does **not** demonstrate a p95
improvement for uncached input. Emulator scheduling and host load still affect
the tail. The separate native benchmark remained similar (median 68.9 → 70.7 ms,
p95 97.4 → 106.0 ms). All 72 complete candidate lists matched before/after, and
all 114 trace readings and first candidates matched. Context-reset profiling
found a median cost of only 4–5 ms, so its existing isolation semantics are retained.

The full offline device suite passed with portrait/landscape keyboard screenshots
checked. UI-test edit p95 was 264 ms and final-edit-to-candidate observation was
184 ms, including test synchronization. Real-device latency, sustained typing,
battery use and thermal behavior still need the Pixel.
