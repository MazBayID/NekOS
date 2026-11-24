# NekOS — Application Architecture

## Current implementation boundary

NekOS uses a single Activity with Jetpack Compose. Keep the Activity responsible for Android window setup and lifecycle integration; keep composables focused on rendering and translating user input into events.

## Core layers

- **Presentation:** Compose screens, dialogs, gestures, and visual state. Keep screen-level orchestration in `NekOSRoot.kt`; Home layout orchestration lives in `HomeScreen.kt`; reusable Favorite Slot, Tag Pill, and Tag Frame composables live in `HomeComponents.kt`. App Drawer rendering and grid items live in `AppDrawer.kt`. Settings dialog orchestration lives in `NekOSSettingsDialog.kt`, with reusable preview and card composables in `SettingsComponents.kt`. Tag editor UI lives in `TagManagementDialog.kt`; tag viewing and action dialogs live in `TagDialogs.kt`. UI components should not directly own package-management or persistence rules.
- **Domain:** launcher operations such as loading launchable apps, adding/removing/reordering favorites, and resolving app actions.
- **Data:** local persistence for favorites and user-defined collections, plus Android PackageManager / LauncherApps access for installed applications.
- **Assets:** catalog metadata remains the source of truth for UI/tag icon names and paths. App icons in the drawer and favorites always come from Android.

## State and persistence rules

- Keep one source of truth for each user-owned collection.
- Persist app IDs and package names, never Drawable or Bitmap instances.
- Treat the app ID (profile serial number plus package name) as the stable identifier; the installed-app list refreshes from `LauncherApps` callbacks and on resume, including after returning from system app info/uninstall screens.
- Remove stale package references after an app is uninstalled.
- Preserve the 15-slot favorite layout and its empty slots when moving or removing apps.
- Do not make a UI action appear successful until the underlying state change has completed.
- Tags are stored as JSON in private SharedPreferences under `nekos_tags` / `tag_collection_v1`. Each tag has a stable UUID, trimmed name (max 24 characters), optional catalog icon ID, ARGB color, and a deduplicated list of assigned app IDs.
- Tag persistence tolerates malformed stored JSON by returning an empty collection; invalid/blank tags and duplicate IDs are filtered during load/save.

## Interaction rules

- Home layout: Favorite Grid (5 × 3) at the bottom, Tag Rail on the right, and App Drawer rising to about 60% of the screen height.
- Tag Rail shows up to five tags per page. Horizontal swipes change pages; page indicators appear when more than one page exists. The current implementation allows up to ten tags total.
- The add-tag control and page indicator sit below the framed tag list. Tag actions can move a tag to an adjacent page; dragging a tag handle reorders tags.
- A normal tap launches the app.
- A long-press followed by release without meaningful movement opens the app action menu.
- Movement beyond touch slop after a long-press begins a drag; the final target is calculated from the full drag distance, not the initial finger position inside the icon.
- The Android system owns the final uninstall confirmation and package removal.

## Asset integration

- `main_assets/icons/app/icons_index.json` and `main_assets/icons/ui/ui_index.json` are the machine-readable source catalogs.
- The matching Markdown files are human-readable references only.
- App icon filenames are relative to `main_assets/icons/app/`; UI icon paths are relative to `main_assets/icons/` and begin with `ui/`.
- The runtime Tag icon catalog is bundled under `app/src/main/assets/ikon/`; its JSON paths are normalized to begin with `icons/` so they resolve relative to the bundled index.
- The 100 source app-icon WebP files and their 100 runtime copies currently have identical Git blobs. Keep the runtime copies unless the asset pipeline is deliberately changed and verified.
- Keep source artwork and icon IDs intact when mapping them into runtime assets.

## Current priorities

1. Verify gestures, drag-and-drop, and app-list refresh on physical devices.
2. Handle disabled or unavailable apps gracefully.
3. Keep `NekOSRoot.kt` focused on screen orchestration. Home, Favorite, Tag Rail, and App Drawer composables are extracted into focused files; tag viewing/actions are separated from the tag editor. Continue moving code only when the responsibility boundary is clear and behavior can be preserved.
4. Keep new features out unless they pass the test in `PRODUCT_DIRECTION.md`.
