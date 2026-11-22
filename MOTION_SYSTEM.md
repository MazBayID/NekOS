# NekOS Motion System

## Direction: Quiet Motion

NekOS uses motion to clarify hierarchy, continuity, and interaction. Animation should feel calm, precise, restrained, and warm. It must never compete with app icons, labels, wallpaper, or the user's task.

## Principles

1. **Purpose before decoration** — animate only when motion explains a state change, preserves spatial context, or confirms an interaction.
2. **Content over scenery** — wallpaper remains visually stable during Home content transitions unless the wallpaper itself is changing.
3. **Follow direct manipulation** — drawer and drag interactions track the user's finger; settling animation begins only after release.
4. **Short and restrained** — prefer small distances, subtle scale changes, and low overshoot.
5. **Respect system ownership** — Android controls cross-task and app-launch transitions where applicable. NekOS animates its own content and must not depend on privileged launcher APIs.
6. **Accessible and robust** — avoid unnecessary motion, preserve touch targets and semantics, and do not make essential information available only through animation.

## Motion tokens

These are starting values, not a mandate to animate every component.

| Interaction | Duration | Behavior |
| --- | ---: | --- |
| Tap feedback | 100–140 ms | Subtle state/color response |
| Small action menu | 140–180 ms | Fade + restrained scale |
| Dialog | 180–250 ms | Material 3 default unless a measured issue exists |
| Tag page change | 220–280 ms | Horizontal movement + fade |
| Drawer settle | 250–320 ms | Finger-driven; gentle spring after release |
| Return to Home content | 250–300 ms | Foreground fade + slight upward reveal |
| Wallpaper replacement | 250–350 ms | Crossfade between loaded images |
| Reorder placement | Spring | Low bounce; preserve finger tracking |

Use FastOutSlowInEasing or a comparable decelerating curve for entering content. Use a faster exit than entrance. Springs should settle without pronounced bounce.

## Component rules

### Home return
Animate foreground Home content only. Keep the wallpaper and its readability scrim fixed in the root layer. The current foreground treatment starts at alpha 0.96, scale 0.985, and 12 dp vertical offset, then settles to its resting state. Keep this subtle and avoid animating on cold start.

### App Drawer
Keep the existing finger-tracked drawer progress, scrim behavior, close gesture eligibility, and spring settling. Do not replace the gesture model with a timed entrance animation.

### Tags
When changing pages, animate only the tag-page content horizontally with a short fade. Keep the surrounding frame and page indicator stable. Reduce drag emphasis if it distracts from reordering; the dragged item should remain clear without an exaggerated bounce.

### Favorites and app icons
Keep reorder placement spring-based and icons readable. Avoid replaying icon entrance animations on ordinary resume or unrelated state updates.

### Wallpaper
When a new wallpaper has finished loading, crossfade it with the previous image. Do not clear a valid displayed image while the replacement is still loading. Keep the warm readability scrim aligned with the displayed wallpaper.

### Dialogs and menus
Use Material 3 defaults unless a concrete usability inconsistency is found. Do not add custom transitions just for visual novelty.

### App launch and system transitions
Use Android-supported launch options only where they are compatible with the device and existing source bounds. Do not attempt to reproduce privileged AOSP Quickstep remote transitions in a normal third-party launcher.

## Implementation order

- **P0 — Home return layering:** separate the foreground transform from the static wallpaper layer.
- **P1 — Tag page transition:** animate the content change without moving the frame or breaking swipe/reorder gestures.
- **P1 — Wallpaper replacement:** crossfade only after the new image is decoded.
- **P2 — Validate Drawer, drag, menus, and dialogs:** tune only after device observation.
- **P3 — Minor feedback polish:** only if it improves clarity.

Implement one area at a time. Preserve Drawer gesture semantics, app catalog behavior, icon cache behavior, and accessibility descriptions in every motion change.

## References

- [Compose animation overview](https://developer.android.com/develop/ui/compose/animation/introduction)
- [Compose animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Android ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions)
- [AOSP Launcher3 Quickstep transition manager](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/master/quickstep/src/com/android/quickstep/LauncherActivityInterface.java)

The AOSP reference describes system-launcher architecture; it is not a permission/API contract for third-party launchers.
