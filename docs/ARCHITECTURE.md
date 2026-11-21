# NekOS — Application Architecture

## Current implementation boundary

NekOS uses a single Activity with Jetpack Compose. Keep the Activity responsible for Android window setup and lifecycle integration; keep composables focused on rendering and translating user input into events.

## Core layers

- **Presentation:** Compose screens, dialogs, gestures, and visual state. UI components should not directly own package-management or persistence rules.
- **Domain:** launcher operations such as loading launchable apps, adding/removing/reordering favorites, and resolving app actions.
- **Data:** local persistence for favorites and user-defined collections, plus Android PackageManager / LauncherApps access for installed applications.
- **Assets:** catalog metadata remains the source of truth for UI/tag icon names and paths. App icons in the drawer and favorites always come from Android.

## State and persistence rules

- Keep one source of truth for each user-owned collection.
- Persist package names, never Drawable or Bitmap instances.
- Treat package names as stable app identifiers; refresh the installed-app list after returning from system app info/uninstall screens.
- Remove stale package references after an app is uninstalled.
- Preserve the 15-slot favorite layout and its empty slots when moving or removing apps.
- Do not make a UI action appear successful until the underlying state change has completed.
- Tags are stored as versioned JSON in private SharedPreferences. Each tag has a stable UUID, trimmed name (max 24 characters), optional catalog icon ID, ARGB color, and a deduplicated list of assigned package names.
- Tag persistence tolerates malformed stored JSON by returning an empty collection; invalid/blank tags and duplicate IDs are filtered during load/save.

## Interaction rules

- A normal tap launches the app.
- A long-press followed by release without meaningful movement opens the app action menu.
- Movement beyond touch slop after a long-press begins a drag; the final target is calculated from the full drag distance, not the initial finger position inside the icon.
- The Android system owns the final uninstall confirmation and package removal.

## Asset integration

- `main_assets/ikon/icons_index.json` and `main_assets/ikon/ui_index.json` are the machine-readable catalogs.
- The matching Markdown files are human-readable references only.
- Tag icon filenames in `icons_index.json` are relative to the `icons/` directory; UI icon paths in `ui_index.json` are relative to `main_assets/ikon/` and begin with `ui/`.
- The runtime tag catalog is bundled under `app/src/main/assets/ikon/`; its copied JSON paths are normalized to begin with `icons/` so they resolve relative to the bundled index.
- Keep source PNGs untouched. The catalog index and icon files are bundled as runtime assets for the tag icon picker.
- Keep source icon IDs and filenames intact when mapping them into app resources.

## Implementation sequence

1. Stabilize app actions, favorite interactions, and app-list refresh.
2. Extract favorite state and package actions from the root composable into focused domain/data components.
3. Implement tag management UI with catalog icon selection, color palette, and app assignment.
4. Add tag collection view for launching assigned apps and refine the home-screen tag interaction.
5. Extract remaining root state and add settings after the home/drawer/favorites/tags vertical slice is reliable.
