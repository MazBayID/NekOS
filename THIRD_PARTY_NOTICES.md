# Third-Party Notices

NekOS is distributed under the MIT License. This file records third-party software included by the application.

## Reorderable

- **Project:** Reorderable
- **Description:** Drag-and-drop reordering for Jetpack Compose lists and grids.
- **Copyright:** Copyright (c) Calvin Liang and contributors.
- **Version used by NekOS:** 3.1.0
- **Maven coordinate:** `sh.calvin.reorderable:reorderable:3.1.0`
- **Source repository:** https://github.com/Calvin-LL/Reorderable
- **Version source:** https://github.com/Calvin-LL/Reorderable/tree/v3.1.0
- **License:** Apache License, Version 2.0 (Apache-2.0)
- **License text bundled in the APK:** `app/src/main/assets/licenses/reorderable-3.1.0-Apache-2.0.txt`

The Reorderable project’s `v3.1.0` source contains a `LICENSE` file and no `NOTICE` file at its repository root. The Apache-2.0 license text is included in the NekOS source tree and packaged as an Android application asset so recipients of the APK receive a copy.

Reorderable is used as an unmodified external dependency. NekOS does not claim ownership of that library.

## Lawnicons app-filter mapping data

- **Project:** Lawnicons
- **Description:** A subset of Android package-to-icon mapping metadata from Lawnicons' `app/assets/appfilter.xml`, used as the source for NekOS category-glyph mappings marked `source: rule` or `source: default`. NekOS does not bundle Lawnicons' icon artwork, application code, or logos.
- **Upstream repository:** https://github.com/LawnchairLauncher/lawnicons
- **Upstream file:** https://github.com/LawnchairLauncher/lawnicons/blob/develop/app/assets/appfilter.xml
- **License:** Apache License, Version 2.0 (Apache-2.0)
- **License text bundled in NekOS:** `app/src/main/assets/licenses/lawnicons-Apache-2.0.txt`

The imported catalog snapshot did not preserve the exact upstream Lawnicons commit SHA. The source file and license are identified here, but the historical revision cannot be verified from the inherited catalog metadata. Future imports must record the exact upstream commit SHA alongside the source path. NekOS converts the imported package mappings into its own glyph categories; the imported data is not represented as Lawnicons artwork.
