# NekOS — Product Direction

> *A cozy space for your everyday apps.*

NekOS is a simple Android launcher for everyday use with a cozy-tech, cat-themed look. Its job is to make app access comfortable, tidy, and easy. It is not meant to be a launcher with many complex features.

This document is the product intent. For what is actually implemented, see `FEATURE_INVENTORY.md`.

## 1. Philosophy

- **Simple:** only features that are genuinely useful for general use.
- **Comfortable:** warm, calm, and not tiring to look at.
- **Easy to reach:** favorites and apps grouped by tag are quick to get to.
- **Personal:** users arrange apps, tags, and the Home appearance.
- **Stable:** everyday reliability comes before adding features.

**Usage flow:** Home → Favorite / Tag → App Drawer → Search → Settings

## 2. Main structure

| Part | Role |
|---|---|
| **Home Screen** | Central access point with a Favorite Grid and a Tag Rail on the right. |
| **App Drawer** | Panel rising from the bottom, about 60% of the screen, with app search. |
| **Tag System** | Groups apps with tags that can be created, renamed, reordered, and deleted. |
| **Settings** | Appearance, wallpaper, gesture hint, version, and app information. |

## 3. Home Screen design

- **Favorite Grid:** 15 slots in 5 columns × 3 rows at the bottom. Empty slots are allowed and are never filled automatically.
- **Tag Rail:** a vertical list of tags on the right side. Tapping a tag opens its app collection in a lightweight panel.
- **Wallpaper:** background presets and a custom image.
- **Gestures:** swipe up from the Home canvas or the Favorite area to open the App Drawer; long-press and drag to arrange apps and tags. Pressing Home while NekOS is open returns to a clean Home.
- Keep the Android status bar and notification shade accessible, and never block system navigation.

## 4. App behavior

- **Tap** an app to launch it.
- **Long-press and release** without moving opens the app action menu: App info, Add/Remove favorite, Uninstall.
- **Long-press and move** beyond touch slop starts a drag to reorder.
- **Uninstall** is always confirmed by Android's system UI.
- **Search** matches any part of an app name, ignoring case.
- App icons in the drawer, favorites, and tag collections are always the **original Android icons**.

## 5. Tag System

- Create, rename, reorder, and delete tags.
- Each tag has a name (max 24 characters), a color from a small palette, and an optional icon from the bundled catalog.
- Deleting a tag always asks for confirmation.
- Assign and unassign installed apps.
- Tag color applies to the tag surface only; catalog icons keep their original colors.

## 6. Icon policy

- Catalog icons in `main_assets/ikon/` are for **tag icons and launcher UI only**.
- The catalog JSON is the source of truth for icon names and metadata; the Markdown indexes are human-readable references.
- Do not expose icon-color customization unless it preserves the artwork and has been visually tested.

## 7. Visual direction

- Cozy-tech: warm, rounded, calm, with precise spacing and restrained accent colors.
- Warm neutral backgrounds; tags use soft tinted surfaces; app icons stay native.
- Animations are short and purposeful and must never slow down launching an app.
- Use UI icon assets selectively rather than decorating every surface.
- Branding: the launcher icon is `app_logo.png`; a short, calm startup splash uses the wordmark; the Home header shows a small secondary wordmark.

## 8. Settings scope

Settings stays small and simple:

1. **Appearance:** Home background presets.
2. **Wallpaper:** choose or remove a custom image.
3. **Home behavior:** show the App Drawer gesture hint again.
4. **About:** version, license, and repository link.

## 9. Features NekOS deliberately leaves out

To protect its focus, NekOS does not add:

- A widget engine.
- Nested folders.
- Multiple Home pages.
- News or recommendation feeds.
- Complex automation.
- An elaborate theme system.
- Too many gestures.

Also deferred: AI tagging, alphabet or color search, and advanced gesture customization. Revisit these only through an explicit product decision.

## 10. Development priorities

Do not add features first. Make the basics work well:

1. Keep the app list up to date after apps are installed or removed.
2. Make app search comfortable to use.
3. Handle disabled or unavailable apps gracefully.
4. Keep gestures and drag-and-drop stable on physical devices.
5. Consider small refinements, such as notification dots, only if they are really needed.

## 11. Design principles

- Gesture discovery is subtle but not cryptic; hints are dismissible.
- Use stable identifiers (package name plus profile) for saved assignments and handle uninstalled apps gracefully.
- Build and verify the core flow before any customization.
- Add something only if it truly improves everyday comfort. If a feature does not help users reach and manage their apps more easily, leave it out.
