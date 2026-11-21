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
- A cancelled swipe is distinguished from a completed drag so a cancelled gesture does not open the drawer.

### Tags and collections
- Create, edit, and delete user-defined tags.
- Tag names are trimmed and limited to 24 characters.
- Choose from six preset tag colors.
- Choose an optional icon from the bundled 100-icon catalog.
- Assign and unassign installed apps to a tag.
- Tap a tag to open its app collection; tap an app in the collection to launch it.
- Long-press a tag to access edit/delete actions.
- Tag data is stored as versioned JSON in private SharedPreferences, with basic malformed-data and duplicate filtering.
- Uninstalled app references are cleaned from tags.

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

- **Settings:** currently limited to background presets and wallpaper. The broader settings surface described in product planning does not exist yet.
- **Tag customization:** create/edit/color/icon/app assignment are implemented, but tag reordering is not present in the current home UI.
- **Onboarding:** a dismissible first-use App Drawer hint exists; a broader first-run walkthrough or discoverability system is not implemented.
- **Persistence:** favorites and tags persist locally, but the project does not yet provide user-facing export/import or a documented migration/backup workflow.
- **Visual customization:** home background and wallpaper are supported; a broader theme engine, typography controls, and configurable accent system are not implemented.

## 3. Planned next, based on current product direction

These items should be prioritized only after confirming they still fit the intended NekOS experience:

1. Tag ordering/reordering, since it is described in the product direction but is absent from the current implementation.
2. Expand Settings deliberately, beginning with only settings that support the core launcher experience.
3. Improve gesture discoverability without adding persistent visual clutter.
4. Review persistence and recovery expectations before adding more user-owned customization.

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

1. Confirm the product scope and decide whether tag reordering is still required for the MVP.
2. Define the minimum Settings scope and avoid turning it into a broad theme engine.
3. Implement one cohesive feature group at a time.
4. At each checkpoint: run CI, review regressions in existing behavior, and perform a focused device test where UI/Android integration is involved.
5. After the planned MVP scope is complete, run a dedicated stabilization pass across persistence, accessibility, performance, and Android lifecycle behavior.

## 7. Inventory maintenance

Update this document whenever a feature is added, removed, or deliberately deferred. Keep implementation status separate from product intent so the roadmap does not mistake a planned feature for an existing one.
