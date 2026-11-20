# NekOS — Product Direction & MVP

## Product identity
NekOS is a calm, personal Android launcher: fast to scan, easy to arrange, and intentionally quiet. It should have its own visual language and interaction model; Cyclauncher is a conceptual reference only, not a template.

## Core experience (MVP)
- **Home:** no visible buttons or search field by default. Keep Android status bar/time and notification shade accessible.
- **Tags:** a compact strip/area at the top of the home screen. Tags act as user-defined app collections (folder-like), each with a user-selected accent/background color and optional icon from NekOS assets. Tapping a tag opens its apps in a lightweight overlay/panel.
- **Favorites:** fixed lower home area, at most **5 columns × 3 rows (15 slots)**. Empty slots are allowed and should not be filled automatically. App icons launch apps. Long-press and drag an icon to reorder; long-press and release without moving opens App info, Remove from favorite, and Uninstall actions.
- **App drawer:** swipe up from the home screen to reveal the full installed-app list in a **5-column grid** (5 × as many rows as needed), using each app's original Android icon. The drawer scrolls vertically, with alphabetical ordering and prefix search. Long-press an app to open App info, Add to favorite, and Uninstall actions.
- **Settings:** long-press an empty area of home to enter launcher settings. Settings can initially be a single, simple screen.
- **Customization:** tags can be created, renamed, reordered, assigned apps, and given a color and optional NekOS catalog icon. Home favorites can be reordered and selected.
- **Persistency:** settings, tag definitions/members, tag order, favorites, and chosen colors survive process restart and device reboot. Favorite order is persisted when icons are rearranged.

## Icon policy
- NekOS `main_assets/ikon/` catalog icons are for **tag icons and launcher UI only** (settings, supporting UI, empty states, etc.).
- App drawer, home favorites, and app entries within tag views use the **original app icons** obtained from Android, never catalog icons as replacements.
- The icon catalog JSON/Markdown is the source of truth for names and metadata; avoid duplicating catalog labels in code.
- Icon tinting is technically possible for PNGs using alpha as a mask, but it replaces their existing colors and can flatten multicolor artwork. For the MVP, keep catalog icons in their original colors. Tag color customization applies to the tag's surface/accent, not the icon itself. Do not expose icon-color customization unless it can preserve the artwork well and is visually tested; it can be reconsidered later.

## Branding & startup
- Use `main_assets/app_logo.png` for the Android launcher icon.
- Use `main_assets/NekOS_text_logo.png` (wordmark + slogan) on a brief, understated startup splash. Keep the home screen free of persistent branding so it remains minimal.
- Splash should be short, avoid artificial loading delays, and transition into the launcher home as soon as initialization is complete.

## Visual direction
- Cozy-tech: warm, rounded, understated, with precise spacing and restrained accent colors.
- Avoid Cyclauncher's distinctive side alphabet rail, wheel, color-search system, widget hub, and multi-feature panels. NekOS should be recognizable through its **top tag strip + 5×3 favorite grid + gesture-only clean home**.
- Recommended primary font: **Nunito Sans** (friendly, legible, rounded without looking childish). Use system fallback for missing glyphs. Keep font weights limited (Regular / SemiBold).
- Suggested baseline: neutral warm background; tags use soft/tinted color surfaces; app icons stay native; UI icon assets should be selectively used rather than decorating every surface.

## MVP implementation outline
1. Android launcher shell: manifest MAIN + HOME + DEFAULT, app list retrieval, launch intents, status bar behavior.
2. Home composition and gestures: top tags, 5×3 favorites, swipe-up drawer, long-press empty-space settings.
3. App drawer: installed apps, native app icons, 5-column grid, alphabetical ordering and search.
4. Tag model + tag management + catalog icon picker and app assignment.
5. Favorites reorder/slot management.
6. Settings: theme/accent, tag color/icon picker, gesture/behavior options (only after core flow works).
7. Persist data with a small local store (DataStore for preferences; Room or a carefully versioned JSON store for collections). Keep UI state and persistence separate.

## Initial design principles
- Gesture discoverability should be subtle but not cryptic: first-run hints can be dismissed.
- Keep animations short and purposeful; avoid motion that slows app launching.
- Use stable app identifiers/package names for saved assignments, and gracefully handle uninstalled apps.
- The launcher should never block access to system status/notification shade or Android navigation.
- Build a working vertical slice before adding advanced customization.

## Explicitly deferred
Widgets, AI tagging, alphabet/color search, private/work profile features, theme engines, advanced gesture customization, and any extra features not required by the MVP.