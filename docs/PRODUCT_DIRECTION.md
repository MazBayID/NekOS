# NekOS — Product Direction & MVP

## Product identity
NekOS is a calm, personal Android launcher: fast to scan, easy to arrange, and intentionally quiet. It should have its own visual language and interaction model; Cyclauncher is a conceptual reference only, not a template.

## Core experience (MVP)
- **Home:** no visible buttons or search field by default. Keep Android status bar/time and notification shade accessible.
- **Tags:** a compact strip/area at the top of the home screen. Tags act as user-defined app collections (folder-like), each with a user-selected accent color and optional icon from NekOS assets. Tapping a tag opens its apps in a lightweight overlay/panel.
- **Favorites:** fixed lower home area, at most **5 columns × 3 rows (15 slots)**. Empty slots are allowed and should not be filled automatically. App icons launch apps; long-press opens edit/reorder/remove actions.
- **App drawer:** swipe up from the home screen to reveal the full installed-app list. Start with a clean alphabetical list/grid and fast text search; avoid extra navigation chrome in the first version.
- **Settings:** long-press an empty area of home to enter launcher settings. Settings can initially be a single, simple screen.
- **Customization:** tags can be created, renamed, reordered, assigned apps, and given a color. Home favorites can be reordered and selected.
- **Persistency:** settings, tag definitions/members, tag order, favorites, and chosen colors survive process restart and device reboot.

## Visual direction
- Cozy-tech: warm, rounded, understated, with precise spacing and restrained accent colors.
- Avoid Cyclauncher's distinctive side alphabet rail, wheel, color-search system, widget hub, and multi-feature panels. NekOS should be recognizable through its **top tag strip + 5×3 favorite grid + gesture-only clean home**.
- Recommended primary font: **Nunito Sans** (friendly, legible, rounded without looking childish). Use system fallback for missing glyphs. Keep font weights limited (Regular / SemiBold).
- Suggested baseline: neutral warm background; tags use soft/tinted color surfaces; app icons stay native; UI icon assets should be selectively used rather than decorating every surface.

## Asset use
The curated main_assets/ikon/icons/ collection contains 100 PNG icons at 512×512, with icons_index.json and Indonesian icons_index.md as its catalog.
- Use these icons for tag icon selection, empty states, settings categories, and small supporting UI where relevant.
- Keep the catalog as the source of truth; map asset IDs/names from JSON rather than hardcoding duplicated labels.
- Do not use these custom icons as substitutes for installed app icons.
- Build an icon picker that can browse/search the catalog and preview the selected icon. A subset may be used in MVP; asset use should remain purposeful.

## MVP implementation outline
1. Android launcher shell: manifest MAIN + HOME + DEFAULT, app list retrieval, launch intents, status bar behavior.
2. Home composition and gestures: top tags, 5×3 favorites, swipe-up drawer, long-press empty-space settings.
3. App drawer: installed apps, alphabetical ordering, search/filter.
4. Tag model + tag management and app assignment.
5. Favorites reorder/slot management.
6. Settings: theme/accent, icon picker, gesture/behavior options (only after core flow works).
7. Persist data with a small local store (DataStore for preferences; Room or a carefully versioned JSON store for collections). Keep UI state and persistence separate.

## Initial design principles
- Gesture discoverability should be subtle but not cryptic: first-run hints can be dismissed.
- Keep animations short and purposeful; avoid motion that slows app launching.
- Use stable app identifiers/package names for saved assignments, and gracefully handle uninstalled apps.
- The launcher should never block access to system status/notification shade or Android navigation.
- Build a working vertical slice before adding advanced customization.

## Explicitly deferred
Widgets, AI tagging, alphabet/color search, private/work profile features, theme engines, advanced gesture customization, and any extra features not required by the MVP.