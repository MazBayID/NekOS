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
| 1 | Optimize Tag icon decoding (decode near rendered size; retain a larger size only where preview quality requires it) | IMPLEMENTED — build/unit tests passed; lint blocked by existing issue | Commit `2a62f586` downsamples Tag icons to max 128 px before recoloring. CI run #500 completed: `assembleDebug` and `testDebugUnitTest` passed; `lintDebug` failed only on existing `NekOSRoot.kt:1217` `fillMaxWidth(1.02f)` Range lint. | Keep lint issue tracked separately; continue with UI artwork decoding/recoloring |
| 2 | Optimize UI artwork decoding/recoloring | IN PROGRESS | New bounded decoding path and per-call target sizes are being added; small 28 dp UI icons target 128 px while title artwork and coordinate-based frames retain up to 512 px. | Verify compile, tests, recolor output, and cache behavior |
| 3 | Right-size startup logo bitmap | QUEUED | Pending | Add a display-sized asset variant and compare visual quality |
| 4 | Harden background lifecycle cleanup for transient gestures/overlays | QUEUED | Pending | Reset only transient interaction state; preserve dialogs that should survive |
| 5 | Harden icon cache/catalog validation | QUEUED | Pending | Validate disk bitmap dimensions and include density in catalog comparison |
| 6 | Repair CI lint issue and run full relevant checks | QUEUED | Existing lint failure is `fillMaxWidth(1.02f)` at `NekOSRoot.kt:1217`; fix separately without mixing it into image changes. | Confirm debug/release lint and available tests pass |
| 7 | Add build-time resource asset optimization pipeline (keep assets file-backed; no hardcoded bitmap arrays; safe format optimization) | QUEUED — added to Stage 1 | Scope recorded above; implementation not started. | Audit current drawable resources and Gradle setup; preserve originals and convert only assets that benefit from WebP/vector formats |

## Completed
- Initial source-level audit and prioritization: completed 2026-11-23.
- Tag icon decoding is implemented and its APK build/unit tests passed; the combined CI job remains red only because of the pre-existing lint error recorded above.

## References
- Android Developers, Optimize bitmap images in Jetpack Compose: https://developer.android.com/develop/ui/compose/graphics/images/optimization
- Android Developers, Loading Large Bitmaps Efficiently: https://developer.android.com/topic/performance/graphics/load-bitmap.html
- Android Developers, BitmapFactory.Options / inSampleSize: https://developer.android.com/reference/android/graphics/BitmapFactory.Options
- Android Developers, Modifier.pointerInput: https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/pointerInput.modifier
