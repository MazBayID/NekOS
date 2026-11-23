# NekOS Repair Log

Last updated: 2026-11-23

## Workflow
One repair at a time: inspect current source -> make the smallest targeted change -> build and run relevant tests -> record commit and result -> proceed only after verification.

## Stage 1 scope — image and asset pipeline

- Keep visual assets as files under Android resources; Kotlin should reference resource IDs rather than embed pixel arrays or hand-written bitmap data.
- Preserve original source artwork. Add build-time optimization/conversion only where format and visual content permit it; do not blindly convert transparent artwork, spritesheets, or assets whose sharp edges require lossless storage.
- Decode raster artwork near its rendered pixel size before recoloring or caching. Keep full source dimensions only for artwork that relies on fixed source coordinates or needs the full display resolution.
- Right-size the startup logo separately and verify its visual quality.

## Repair queue

| # | Task | Status | Completion evidence | Next |
|---|---|---|---|---|
| 1 | Optimize Tag icon decoding | IMPLEMENTED — build, unit tests, and lint pass | Commit `2a62f586`; CI #520 passed APK build, unit tests, and lint with the current source. | Visual verification on device remains outstanding |
| 2 | Optimize UI artwork decoding/recoloring | IMPLEMENTED — build, unit tests, and lint pass | CI #520 passed APK build, unit tests, and lint with the current source. Small UI icons target 128 px; title artwork and coordinate-based frames retain up to 512 px. | Visual verification on device remains outstanding |
| 3 | Right-size startup logo bitmap | IMPLEMENTED — build, unit tests, and lint pass | CI #520 passed APK build, unit tests, and lint with the current source. Original PNG remains untouched; decoding is off the main thread with a 1280 px maximum dimension. | Visual verification on device remains outstanding |
| 4 | Harden background lifecycle cleanup for transient gestures/overlays | IMPLEMENTED — build, unit tests, lint, and emulator UI tests pass | Commit `173176ab` resets drawer state, cancels active drags, rolls back incomplete Favorite/Tag reorder operations, clears temporary action overlays, and keys Home `pointerInput` to `backgroundSignal`. The missing parameter forwarding was fixed in `40ccee0`. CI #520 passed APK build, unit tests, lint, and emulator UI tests. | Verify the reported background/resume symptom on a physical device |
| 5 | Harden icon cache/catalog validation | IMPLEMENTED — build, unit tests, and emulator instrumentation pass | Commits `57d36c6`, `f893865`, `ba9899c`, `c56eefe`, `39d636f`, and `87b8c6e`. Disk icon dimensions are checked before and after decode; catalog equality includes density; entries and asset paths are validated for completeness and uniqueness. Current 100-entry JSON index has no invalid entries or duplicate IDs/paths. | Physical-device cache/catalog behavior remains unverified |
| 6 | Repair CI lint issue | IMPLEMENTED — lint passed | Commit `6209c78` replaces the invalid `fillMaxWidth(1.02f)` with full width plus a 1.02x graphics-layer scale anchored at the start, preserving the intentional visual bleed. CI #520 `lintDebug` passed. | Keep lint in CI |
| 7 | Build-time resource asset optimization | REVIEWED — native pipeline retained; raw catalog asset conversion deferred pending benchmark | AAPT2 already compiles and crunches PNG resources; release PNG crunching is enabled by default. The 100 icon PNGs under `src/main/assets` total about 12.44 MiB and are not AAPT2 drawable resources. WebP lossless is a possible candidate, but conversion is deferred until file-by-file pixel/alpha and size comparisons can prove a benefit without visual regression. | Benchmark lossless WebP against the original catalog icons before any conversion; keep originals unchanged |

## Completed
- Initial source-level audit and prioritization: completed 2026-11-23.
- Repairs 1–3: bounded icon/artwork decoding and startup logo downsampling are implemented; CI #520 passed APK build, unit tests, lint, and emulator UI tests. Physical-device visual verification remains outstanding.
- Repair 6: the existing width-range lint error is fixed without removing the intentional logo bleed; CI #520 lint passed.
- Stage 7 review: confirmed AAPT2 is already the build-time PNG resource compiler/cruncher for `res` assets. No duplicate custom crunch task was added. A local lossless WebP benchmark script is now available; no icon source assets have been changed.

## References
- Android Developers, Optimize bitmap images in Jetpack Compose: https://developer.android.com/develop/ui/compose/graphics/images/optimization
- Android Developers, Loading Large Bitmaps Efficiently: https://developer.android.com/topic/performance/graphics/load-bitmap.html
- Android Developers, BitmapFactory.Options / inSampleSize: https://developer.android.com/reference/android/graphics/BitmapFactory.Options
- Android Developers, Modifier.pointerInput: https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/pointerInput.modifier


- Android Developers, AAPT2 resource compilation and PNG crunching: https://developer.android.com/tools/aapt2
- Android Developers, Convert images to WebP (lossless support and alpha-channel considerations): https://developer.android.com/studio/write/convert-webp
- Android Developers, Optimize build speed / WebP trade-offs: https://developer.android.com/build/optimize-your-build
- Android Developers, optimized resource shrinking (AGP 8.12+): https://developer.android.com/topic/performance/app-optimization/enable-app-optimization

## CI efficiency pass — 2026-11-23
- [x] Android CI: use `gradle/actions/setup-gradle@v6` enhanced cache (the default) instead of basic cache. NekOS is a public repository, so the enhanced cache is available without a paid private-repository entitlement.
- [x] Android CI: ignore documentation-only changes (`**/*.md`, `docs/**`, `.gitignore`) on push and pull requests; Android resources and source remain covered.
- [x] Android CI: retain the existing branch-scoped concurrency cancellation and Ubuntu runner.
- [x] Android CI: reduce the generous 60-minute job timeout to 35 minutes; this still leaves room for Android emulator startup and Compose UI tests.
- [x] Android CI: remove `--no-daemon` from both Gradle invocations so the job can reuse its Gradle daemon between build/test and emulator test steps.
- [x] Release: switch to enhanced Gradle cache, reduce timeout to 35 minutes, and restore default shallow checkout because release validation only compares the tag name with app version; no git history is used.
- [x] Keep build, unit tests, and lint in one runner/job. Splitting them into parallel runners would duplicate JDK/SDK setup and Gradle configuration/cache work, increasing total billed minutes. No matrix is used, so `fail-fast` is not applicable.
- [x] CI #520 completed successfully: APK build, unit tests, lint, Compose UI tests on Android emulator, and debug APK artifact upload all passed.
- References: GitHub Actions dependency caching and workflow syntax; Gradle Actions setup-gradle v6 documentation.
