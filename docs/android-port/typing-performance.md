# Exact CPU prefix scoring and cancellation

Android now reuses the neural prompt and already scored token prefixes while evaluating complete boundary candidates. Internal token sorting does not change the returned candidate order, normalization, full-vocabulary probability calculation, or final scores. CPU batching remains disabled: a measured batch experiment introduced up to 0.0119 score drift and was rejected. The CPU uses at most four threads, selected from measurements on the Pixel.

The public-fixture 160-candidate Pixel probe preserved every score and ranking (0.0001 comparison tolerance) while reducing reference evaluation from 35,282 ms to 13,047 ms. A repeated cached query took 53 ms. The ARM64 emulator measured 5,700 ms versus 1,591 ms and a 19 ms cached query. Three further phrase sets were compared at 2, 4, and 6 threads on both devices.

Cancellation is separate from coroutine cancellation: a Swift atomic flag interrupts candidate scoring between native evaluations. A process-wide lock is held until physical native work and its cancellation watcher finish. Destruction cannot race an active call. Cancelling a fresh boundary request took 215 ms on Pixel and 37 ms on the emulator; the immediately following conversion succeeded.

Ordinary Pixel phrase conversion remains above the 150 ms p95 target: this run measured median 229 ms, p95 340 ms, maximum 380 ms. Cold full boundary ranking also remains expensive. Journal UI integration must update kana independently, debounce suggestions, reject stale results, and cancel replaced requests. These measurements exclude rendering and keyboard delivery and do not claim that the latency target is met.

Native ARM64 and x86_64 builds, Android build/lint/JVM checks, eight numerical/engine tests on each local device, and the corrected cancellation contract on both devices pass. The initial emulator cancellation test waited 1.5 seconds, by which point that faster device had already finished; it now synchronizes native entry and cancels after 100 ms. Public reports are stored alongside this note; no personal writing is included.

Numerical robustness follow-up: always shifting the softmax normalizer changed the pinned engine’s Float rounding by 0.000101 for one full-pool score, exceeding the existing 0.0001 parity tolerance. That experiment was rejected. `BoundaryNormalization` retains the original finite calculation and applies maximum-shifted log-sum-exp only for overflow or underflow. The standalone Swift test verifies bit-identical ordinary results and finite extreme-logit results. Both ABI builds and the unchanged full score/ranking/cancellation tests pass on Pixel and emulator; updated full-pool reports are `stable-prefix-{pixel,emulator}.json`. The duplicate Swift split-mode patch was removed: the existing documented CPU loader wrapper already selects the LAYER mode required by the pinned llama revision.


## CPU dot products and persistent workers — November 21, 2026

The Pixel now uses ARM dot-product instructions for the pinned model's integer
matrix operations. Native initialization checks `HWCAP_ASIMDDP` before loading
`libggml-cpu-dotprod.so`; unsupported hardware and a missing optimized library
fall back to `libggml-cpu.so`. x86_64 keeps the generic backend. Backend selection
and loading failures are logged without any user text. Packaging verifies both
CPU libraries on ARM64 and the generic library on x86_64.

Each model context now owns a persistent GGML worker pool. Previously the CPU
backend created and joined workers for every graph evaluation; profiles showed
substantial time spent coordinating workers. Idle workers now sleep between
graphs. The context's active worker count remains capped at four, with capacity
for the diagnostic sweep bounded by online CPUs. Context teardown detaches the
pool, frees the context, then joins/frees its workers. Native cancellation and
the process-wide operation lock remain unchanged. External C++ object filenames
include their content hash so SwiftPM reliably relinks incremental adapter edits.

The historical 13-second result above came from instrumentation without a
visible activity. Android's scheduling materially changes these timings. The
probe now opens a foreground activity, matching interactive typing, and records
the backend and worker count. Do not count that scheduling change as part of
the optimization gain.

Matched foreground Pixel 10a (Android 16/API 36) results for the public
`はしでたべました` fixture, boundary `[2]`, all 160 candidates, four active workers:

| Measurement | Previous build | Optimized build |
| --- | ---: | ---: |
| Cold boundary request | 6,714 ms | 2,593 ms |
| Prefix scoring alone | 6,641 ms | 2,834 ms |
| Cached repeat | 44 ms | 50 ms |

The cold request improved by approximately **61%** in this comparison. Four
fresh-process cold requests measured 2,380 / 2,469 / 2,593 / 4,892 ms (median
2,531 ms); two earlier runs measured 2,515 and 2,606 ms. The tail remains variable,
and even the typical result is not instantaneous. Dot products alone previously
measured about 5,747 ms; worker reuse provided the larger second improvement.

Every score matched the previous build exactly and candidate order was identical.
The model, complete pool, normalization, scoring algorithm, 0.0001 parity
tolerance, and ranking assertions are unchanged. A sparse-prefix-tree experiment
changed scores and was discarded. Public raw reports remain in
[expanded-latency](expanded-latency) to make cross-build parity independently
auditable.

The final full-pool worker sweep measured isolated prefix scoring at 6,341 /
3,905 / 2,834 / 3,290 ms with 1 / 2 / 4 / 6 workers; four remains the best tested
setting. Reproduce with `-e poolThreads N` on
`BoundaryScoringProbeTest#completeBoundaryPoolKeepsReferenceScoresRankingAndWarmCache`.
This argument changes the isolated scoring comparison; cold requests always
use the production worker count.

Fault injection removed the optional plugin from a developer-signed APK after
normal packaging checks. The Pixel selected `generic`, logged the failed optional
load, and passed full-pool score/ranking/cache checks with zero score difference.
Two cold requests took 3,354 and 3,594 ms. This checks missing-plugin recovery on
real hardware, not a physical older CPU without dot-product support. Before
worker reuse, one generic repeat exhibited a substantial scheduling outlier
(15-second cold request and 81-second isolated scoring); timing variability is
not considered solved by this change.

Validation includes both native ABIs, build/lint and 136 JVM tests, and 19 Pixel
native/keyboard tests covering score parity, cancellation/recovery, repeated model
cleanup, learning persistence/reset, immediate kana, real English keys, and
portrait/landscape keyboard clearance. A separate test asserts backend selection
against `/proc/cpuinfo`. The current release configuration (not minified) was
built as an AAB, split with bundletool, developer-signed, installed on the Pixel,
and passed backend selection/model cleanup tests. Missing-library packaging
checks were also exercised for both ABIs.

An earlier dot-product-only build passed 17 API 30 ARM64 emulator tests. Later
local emulator performance runs were affected by substantial concurrent Mac
load: one ordinary-conversion p95 was 2,051 ms, just above the unchanged 2-second
smoke ceiling, and a restarted emulator eventually reported hung virtual CPUs
and crashed. These are not passing performance results. A later attempt to repeat the original
headless full reference diagnostic on the Pixel was stopped after more than
three minutes without a complete report, and is excluded from the speedup
calculation. Use isolated CI emulator
checks for the final change and the physical Pixel for performance comparisons.
The final Pixel ordinary-conversion suite measured p95 242 ms, still above the
150 ms target. Cold full boundary ranking remains a known usability limitation.
