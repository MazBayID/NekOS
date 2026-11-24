# NekOS — Feature Inventory

> Snapshot: 2026-11-24 (after the package/profile lifecycle audit and UI file extraction)  
> Scope: what the source code on `main` does today, compared with `PRODUCT_DIRECTION.md`.

A feature is marked **implemented** only when matching source code exists. This is not a claim that every edge case has been verified on every Android version or on physical devices.

## 1. Implemented

### Launcher foundation
- Manifest exposes the launcher entry (`MAIN` + `HOME` + `DEFAULT`) and a normal app entry (`MAIN` + `LAUNCHER`).
- Single Activity, Kotlin + Jetpack Compose.
- Home layout orchestration lives in `HomeScreen.kt`; Favorite Slot, Tag Pill, and Tag Frame components live in `HomeComponents.kt`; App Drawer UI and app grid items live in `AppDrawer.kt`.
- Launchable apps come from `LauncherApps`, deduplicated per profile, sorted alphabetically by label using the device locale.
- App launch uses `LauncherApps`; App info and uninstall are handed to Android system screens.
- The app list refreshes automatically on package added, removed, changed, and available/unavailable events, plus profile changes. Bursts of events are coalesced into one refresh. On resume the list is re-checked; it is kept as-is (so decoded icons are reused and Favorite icons do not re-animate) when nothing changed, and fully reloaded after a pending package event or an absence longer than 30 seconds.
- Pressing Home while NekOS is open closes the drawer, Settings, and app/tag menus and returns to a clean Home. An in-progress tag edit is kept. The Activity uses `singleTask` so Home always returns to the same instance.
- Stale favorite and tag references are cleaned up after a refresh. References belonging to a stopped, locked, or quiet-mode profile are kept intact.
- Launch failures show a short message (app no longer available, not allowed, or profile unavailable) instead of crashing.
- The startup splash stays until the first app scan and cleanup finish; there is no fixed delay.

### Home Screen
- Favorite Grid: 15 slots (5 × 3) at the bottom. Empty slots are preserved and never auto-filled.
- Tag Rail: a framed vertical list on the right, about 38% of the available content width, showing up to five tags per page. Horizontal swipes change pages; centered page dots appear when needed, with the add control at the lower-right. Up to 10 tags can be created.
- Header (top left): small secondary wordmark and a Settings (gear) button.
- Wallpaper behind everything: three presets or a custom image.
- Favorites launch on tap; long-press opens app actions; drag after long-press reorders or places into a slot.
- A dismissible first-use App Drawer hint is shown once.

### App Drawer
- Swipe up opens a panel covering about 60% of the screen. The swipe can start in the main Home canvas or anywhere in the Favorite band at the bottom; only the scrolling Tag Rail above that band is excluded. While the drawer is fully open, the upper 40% is a transparent close target.
- Swipe down from the drawer handle closes it. Cancelled swipes do not open it.
- Shows all launchable apps with native icons in an adaptive grid (minimum cell width 68 dp, so the number of columns depends on screen width).
- Search matches any part of the app name, ignoring case. An empty result shows "No apps match your search". The keyboard offers a Search action that dismisses it, dragging the list also dismisses it, and the panel rises above the keyboard (keeping its height) so results stay visible while typing. Closing the drawer clears the search and the keyboard.
- Tap launches; long-press opens App info, Add/Remove favorite, and Uninstall.

### Tag System
- Create, edit, and delete tags; names are trimmed and limited to 24 characters.
- Nine preset tag colors and an optional icon from the bundled 100-icon catalog.
- Assign and unassign installed apps; tapping a tag opens its collection and tapping an app launches it.
- The tag body has no long-press action. The six-dot handle at the right edge of each tag opens Edit/Delete, and long-press plus drag on the handle reorders tags. Order is persisted.
- Deleting a tag, from either the options menu or the editor, asks for confirmation first.
- Tags are stored as versioned JSON in private SharedPreferences, with malformed-data and duplicate filtering.

### Settings
- Opened with the gear button in the Home header.
- **Appearance:** Warm paper, Soft sage, Cool mist.
- **Wallpaper:** choose a custom image or remove it. The image is copied to app-private storage with file-size and decode-dimension limits.
- **Home behavior:** show the App Drawer hint again.
- **About:** version, MIT license, copyright, and repository link.

### Accessibility and interaction
- Favorite slots and drawer items expose button semantics; app items offer a TalkBack custom action for the action menu.
- Gesture handling distinguishes tap, long-press, drag, completed drag, and cancellation.
- Back closes dialogs and menus, or returns from the drawer.

### Assets
- Launcher icon and startup wordmark are included. Tag icon catalog (JSON plus Markdown indexes) is bundled as runtime assets.
- App icons are always the original Android icons.

## 2. Gaps against the concept (to review)

These are places where the code and `PRODUCT_DIRECTION.md` do not fully line up. Each needs a deliberate decision rather than a silent change.

| Area | Current state | Concept expectation | Suggested action |
|---|---|---|---|
| Drawer columns | Adaptive grid, not fixed | Not mandated; older docs claimed five columns | Keep adaptive, or fix to five columns if a consistent look matters more |
| Home header | Small wordmark plus gear button | Product direction explicitly allows a small secondary wordmark and Settings access | No action; current implementation matches the direction |
| Settings entry | Gear button only | Settings reachable from Home | No action; keep the direct, visible entry point |
| Typography | System font; the Nunito Sans recommendation was removed from the docs | Warm, calm look | None |
| Disabled or suspended single apps | Not specially handled; relies on launch error messages | "Check handling of disabled or unavailable apps" | Verify on a device and add explicit handling if needed |
| Notification dots | No code found | Optional, only if needed | Leave out unless there is a clear need |
| Backup | `allowBackup` is enabled; there is no documented backup or export workflow | Personal data should survive | Document the behavior or add an explicit decision |

## 3. Next steps

Follow the concept's priorities: stability over new features.

1. Test on physical devices: drawer gesture, drag-and-drop for favorites and tags, the 40% close target, and persistence after restart.
2. Check behavior after installing and uninstalling apps, and with disabled or unavailable apps.
3. Verify Tag Rail paging, the 10-tag limit, and moving tags between pages on physical devices.
4. Confirm search feels comfortable with real app lists.
5. Resolve the remaining gaps in section 2.

## 4. Explicitly deferred

Widget engine, nested folders, multiple Home pages, feeds or recommendations, complex automation, a full theme system, many gestures, AI tagging, alphabet or color search, and advanced gesture customization. Add none of these implicitly.

## 5. Automated tests

Unit and instrumented tests exist for: favorite slot operations and persistence, tag persistence, tag ordering and normalization, icon catalog loading and asset paths, drawer filtering, label sorting, interaction controller, popup positioning, gesture thresholds and integration, reference cleanup, app identity, wallpaper validation, and item accessibility.

Presence of tests is not complete verification of every user journey. Keep CI green and do focused device checks at each checkpoint.

## 6. Maintenance

Update this file whenever a feature is added, removed, or deferred. Keep implementation status separate from product intent.
