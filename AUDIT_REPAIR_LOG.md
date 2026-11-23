# NekOS Repair Log

Last updated: 2026-11-23

## Workflow
One repair at a time: inspect current source -> make the smallest targeted change -> build and run relevant tests -> record commit and result -> proceed only after verification.

## Repair queue

| # | Task | Status | Completion evidence | Next |
|---|---|---|---|---|
| 1 | Optimize Tag icon decoding (decode near rendered size; retain a larger size only where preview quality requires it) | IMPLEMENTED — CI pending | Commit `2a62f586`; `loadBitmap()` now reads bounds, downsamples to max 128 px, scales exactly, recolors the reduced bitmap, and keys cache by target size. Android CI run #499 is pending. | Confirm CI and relevant tests; then mark complete and move to UI artwork recoloring |
| 2 | Optimize UI artwork decoding/recoloring | QUEUED | Pending | Downsample before pixel recoloring; verify appearance and cache behavior |
| 3 | Right-size startup logo bitmap | QUEUED | Pending | Add a display-sized asset variant and compare visual quality |
| 4 | Harden background lifecycle cleanup for transient gestures/overlays | QUEUED | Pending | Reset only transient interaction state; preserve dialogs that should survive |
| 5 | Harden icon cache/catalog validation | QUEUED | Pending | Validate disk bitmap dimensions and include density in catalog comparison |
| 6 | Repair CI lint issue and run full relevant checks | QUEUED | Pending | Confirm debug/release lint and available tests pass |

## Completed
- Initial source-level audit and prioritization: completed 2026-11-23.
- No repair in this queue is marked complete yet; completion requires a verified code change and test result.

## References
- Android Developers, Optimize bitmap images in Jetpack Compose: https://developer.android.com/develop/ui/compose/graphics/images/optimization
- Android Developers, Loading Large Bitmaps Efficiently: https://developer.android.com/topic/performance/graphics/load-bitmap.html
- Android Developers, BitmapFactory.Options / inSampleSize: https://developer.android.com/reference/android/graphics/BitmapFactory.Options
- Android Developers, Modifier.pointerInput: https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/pointerInput.modifier
