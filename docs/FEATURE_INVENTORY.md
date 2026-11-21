# NekOS — Feature Inventory

> Snapshot: 2026-11-21  
> Scope: implementation currently present on `main`, compared with the product direction and architecture documents.

This inventory records what exists today, what is only partially implemented, and what remains planned or intentionally deferred. A feature is marked **implemented** only when there is corresponding source code in the repository; the list is not a claim that every edge case has been fully validated on every Android version.

## 1. Implemented

### Launcher foundation
- Android app exposes both the launcher entry point (`MAIN` + `HOME` + `DEFAULT`) and a normal launcher-app entry point.
- Single-Activity application built with Kotlin and Jetpack Compose.
- Installed launchable apps are discovered through Android's package manager, deduplicated by package name, and sorted alphabetically by label.
- App launch, App info, and uninstall requests are handed to Android's system APIs.
- App list refresh and stale favorite/tag reference cleanup are implemented.
- Startup splash remains visible until initial app discovery and reference cleanup complete; no fixed artificial startup delay is used.

### Home screen and favorites
- Minimal home screen with a top tag strip and a 5 × 3 (15-slot) favorite area.
- Empty favorite slots are preserved; adding an app does not automatically fill or compact unrelated slots.
- Favorite apps launch on tap.
- Long-press opens app actions; drag after long-press supports favorite reordering and slot placement.
- Favorite package names are persisted locally in private SharedPreferences.
- First-use App Drawer hint is shown and its dismissal is persisted.

### App Drawer
- Opens with an upward home gesture and closes with a downward gesture.
- Displays installed launchable apps in a five-column grid with native Android app icons.
- App list is alphabetically sorted.
- Prefix search/filter is implemented.
- Tap launches an app; long-press exposes App info, Add/Remove favorite, and Uninstall actions.
- A transparent close target covers the upper 40% of the screen while the App Drawer is fully open; the existing drawer handle icon remains visually unchanged.
- A cancelled swipe is distinguished from a completed drag so a cancelled gesture does not open the drawer.

### Tags and collections
- Create, edit, and delete user-defined tags.
- Tag names are trimmed and limited to 24 characters.
- Choose from six preset tag colors.
- Choose an optional icon from the bundled 100-icon catalog.
- Assign and unassign installed apps to a tag.
- Tap a tag to open its app collection; tap an app in the collection to launch it.
- Tap a tag body to open its app collection; the body has no long-press action.
- Tap the dedicated six-dot handle to open Edit/Delete options.
- Long-press and drag the same handle to reorder tags; the handle also exposes an accessible button action for opening options.
- Tag data is stored as versioned JSON in private SharedPreferences, with basic malformed-data and duplicate filtering.
- Uninstalled app references are cleaned from tags.
- Tags can be persistently reordered by dragging a dedicated handle; the handle is anchored at the right edge of each Tag pill.

### Settings and appearance
- Settings can be opened from the home screen's empty-area interaction.
- Three home background presets are available: Warm paper, Soft sage, and Cool mist.
- A custom image can be selected as the home wallpaper and removed later.
- Wallpaper data is copied into app-private storage, with file-size and decode-dimension limits.
- Background preset and wallpaper path are persisted locally.

### Accessibility and interaction
- Favorite slots and App Drawer items expose button semantics.
- App items expose a TalkBack custom action for the app actions menu.
- Gesture handling distinguishes tap, long-press, drag, drag completion, and cancellation.
- Back handling closes the settings/action UI or returns from the drawer.

### Branding and assets
- Android launcher icon and startup wordmark assets are included.
- Source icon catalogs include machine-readable JSON and human-readable Markdown indexes.
- Runtime tag icon assets are bundled with the application.
- App icons shown for installed apps remain the original Android-provided icons.

## 2. Partially implemented / needs product follow-through

- **Settings:** the agreed MVP scope is implemented: Appearance (background presets and custom wallpaper), Home behavior (show the App Drawer hint again), and About (version, MIT license information, and repository link). A broader settings system is intentionally out of scope.
- **Tag customization:** create/edit/color/icon/app assignment and persistent drag ordering are implemented. Tap the dedicated six-dot handle for options; long-press and drag it to reorder. Move up / Move down actions are not part of the Tag action dialog.
- **Onboarding:** a dismissible first-use App Drawer hint exists, with a Settings action to show it again; a broader first-run walkthrough is not implemented.
- **Persistence:** favorites and tags persist locally, but the project does not yet provide user-facing export/import or a documented migration/backup workflow.
- **Visual customization:** home background and wallpaper are supported; a broader theme engine, typography controls, and configurable accent system are not implemented.

## 3. Planned next, based on current product direction

These items should be prioritized only after confirming they still fit the intended NekOS experience:

1. Stabilize the completed MVP with focused physical-device checks for Tag drag ordering and handle alignment, the expanded App Drawer close target, favorite gestures, App Drawer behavior, Settings actions, and persistence across restart.
2. Review accessibility and gesture discoverability without adding persistent visual clutter.
3. Review persistence and recovery expectations before adding more user-owned customization; keep export/import and backup decisions explicit rather than adding them implicitly.

## 4. Explicitly deferred

The product direction currently defers:
- Home-screen widgets.
- AI-powered tagging.
- Alphabetical-rail and color-based search systems.
- Private/work profile features.
- A full theme engine.
- Advanced gesture customization.
- Extra multi-feature panels or widget hubs.

These should not be added implicitly during implementation. Revisit them only through an explicit product decision.

## 5. Existing automated test areas

The repository currently contains tests covering:
- Favorite slot operations and favorite persistence.
- Tag persistence.
- Icon catalog loading.
- App Drawer filtering.
- Launcher interaction controller and popup positioning.
- Gesture thresholds and gesture integration, including cancelled gestures.
- Stale launcher-reference cleanup.
- Launcher item accessibility semantics.

Test-file presence indicates existing automated coverage, not complete verification of every user journey. Continue to require CI and targeted device checks at phase checkpoints.

## 6. Recommended development order

1. Keep the agreed MVP scope explicit; Tag drag reordering and the minimal Settings scope are now implemented.
2. Implement one cohesive feature group at a time and avoid adding deferred systems implicitly.
3. At each checkpoint: run CI, review regressions in existing behavior, and perform a focused device test where UI/Android integration is involved.
4. Run a dedicated stabilization pass across persistence, accessibility, performance, and Android lifecycle behavior.

## 7. Inventory maintenance

Update this document whenever a feature is added, removed, or deliberately deferred. Keep implementation status separate from product intent so the roadmap does not mistake a planned feature for an existing one.
