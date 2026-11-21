# Exact CPU prefix scoring and cancellation

Android now reuses the neural prompt and already scored token prefixes while evaluating complete boundary candidates. Internal token sorting does not change the returned candidate order, normalization, full-vocabulary probability calculation, or final scores. CPU batching remains disabled: a measured batch experiment introduced up to 0.0119 score drift and was rejected. The CPU uses at most four threads, selected from measurements on the Pixel.

The public-fixture 160-candidate Pixel probe preserved every score and ranking (0.0001 comparison tolerance) while reducing reference evaluation from 35,282 ms to 13,047 ms. A repeated cached query took 53 ms. The ARM64 emulator measured 5,700 ms versus 1,591 ms and a 19 ms cached query. Three further phrase sets were compared at 2, 4, and 6 threads on both devices.

Cancellation is separate from coroutine cancellation: a Swift atomic flag interrupts candidate scoring between native evaluations. A process-wide lock is held until physical native work and its cancellation watcher finish. Destruction cannot race an active call. Cancelling a fresh boundary request took 215 ms on Pixel and 37 ms on the emulator; the immediately following conversion succeeded.

Ordinary Pixel phrase conversion remains above the 150 ms p95 target: this run measured median 229 ms, p95 340 ms, maximum 380 ms. Cold full boundary ranking also remains expensive. Journal UI integration must update kana independently, debounce suggestions, reject stale results, and cancel replaced requests. These measurements exclude rendering and keyboard delivery and do not claim that the latency target is met.

Native ARM64 and x86_64 builds, Android build/lint/JVM checks, eight numerical/engine tests on each local device, and the corrected cancellation contract on both devices pass. The initial emulator cancellation test waited 1.5 seconds, by which point that faster device had already finished; it now synchronizes native entry and cancels after 100 ms. Public reports are stored alongside this note; no personal writing is included.

Numerical robustness follow-up: always shifting the softmax normalizer changed the pinned engine’s Float rounding by 0.000101 for one full-pool score, exceeding the existing 0.0001 parity tolerance. That experiment was rejected. `BoundaryNormalization` retains the original finite calculation and applies maximum-shifted log-sum-exp only for overflow or underflow. The standalone Swift test verifies bit-identical ordinary results and finite extreme-logit results. Both ABI builds and the unchanged full score/ranking/cancellation tests pass on Pixel and emulator; updated full-pool reports are `stable-prefix-{pixel,emulator}.json`. The duplicate Swift split-mode patch was removed: the existing documented CPU loader wrapper already selects the LAYER mode required by the pinned llama revision.


## Hardware-selected ARM dot products — November 21, 2026

The Pixel now uses ARM dot-product instructions for the pinned model's integer
matrix operations. Native initialization checks `HWCAP_ASIMDDP` before loading
`libggml-cpu-dotprod.so`; unsupported hardware and a missing optimized library
fall back to `libggml-cpu.so`. Both plugins use the same pinned GGML sources and
model. x86_64 keeps the generic backend. Initialization is process-wide and
thread-safe; neither plugin is unloaded while model contexts exist.

The 13-second result above came from instrumentation with no visible activity.
Android's CPU scheduling materially changes these timings. The probe now opens
a foreground activity, matching interactive typing, and records the chosen
backend and worker count. Do not describe the difference between that historical
13-second result and the following foreground result as an optimization gain.

Matched foreground measurements on the Pixel 10a (Android 16/API 36), using the
same public `はしでたべました` fixture, boundary `[2]`, all 160 candidates, and
four workers:

| Measurement | Previous generic build | Optimized build |
| --- | ---: | ---: |
| Cold boundary request | 6,714 ms | 5,747 ms |
| Prefix scoring alone | 6,641 ms | 5,724 ms |
| Cached repeat | 44 ms | 42 ms |

This is approximately a **14% reduction** in cold request time, not an
instantaneous expanded list. Additional optimized runs measured 5,641–5,775 ms
for the cold request. The model, complete candidate pool, normalization,
ranking, and scoring algorithm are unchanged. Every candidate's score matched
the previous build exactly in the full-pool comparison, and the existing
0.0001 tolerance and ranking assertions remain unchanged. A sparse-prefix-tree
experiment changed scores and was discarded.

The full-pool worker sweep measured prefix scoring at 6,203 / 8,431 / 5,652 /
7,405 ms with 1 / 2 / 4 / 6 workers, respectively; the production cap remains
four. Reproduce with the instrumentation argument `-e poolThreads N` on
`BoundaryScoringProbeTest#completeBoundaryPoolKeepsReferenceScoresRankingAndWarmCache`.
That argument affects the isolated scoring comparison, not the cold engine's
production worker count. See the public JSON reports in
[expanded-latency](expanded-latency).

An APK built without the optional dot-product plugin selected `generic` on the
Pixel and passed the same 160-candidate score/ranking/cache test, with zero
score difference from the previous build and a 6,727 ms cold request. This tests
the missing-plugin fallback on real hardware; it is not a claim of physical
testing on an older CPU without dot-product instructions.

Validation: 17 native and keyboard instrumentation tests pass on the Pixel and
an Android 11/API 30 ARM64 emulator. They include three phrase sets across
2/4/6 workers, full-pool parity, cancellation and recovery, account-scoped
learning persistence/reset, immediate kana editing, real English software keys,
and portrait/landscape keyboard clearance. The emulator's cold request took
1,242 ms and its cached repeat took 20 ms; emulator timings are not phone
performance claims. Both native ABIs build, debug/release APKs contain the
appropriate plugins, and build/lint plus all 136 JVM tests pass. The ordinary
72-conversion Pixel suite measured p95 306 ms, still above its 150 ms target.
Cold full boundary ranking remains a known usability limitation.
