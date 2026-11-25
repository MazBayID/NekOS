# 04 — Migrasi aset: lama → baru

> Catatan v3: path baru ada di `default_skin/` (dulu `source/`). Pemetaan mesin-terbaca: `migration/legacy_icon_map.json`.

Dokumen ini memetakan setiap file lama ke file baru, dan mencatat apa yang dihapus beserta alasannya. File lama tetap ada di zip asli bila ingin dikembalikan.

## Perubahan format (berlaku untuk semua ikon)

| | Lama | Baru |
|---|---|---|
| Kanvas | 512 × 512 px | **96 × 96 px** (glyph) / **128 × 128 px** (badge) |
| Isi | kepala kucing teal + glyph krem, warna dipanggang | **mask putih + alpha**, warna oleh runtime/skin |
| Nama | `icon_001.webp` … | `icon_<bentuk>.webp` |
| Bobot | ±120 KB per ikon (25 MB total) | ±1 KB per ikon (±0,15 MB total) |
| Jumlah | 100 tag + 120 UI | 36 tag + 45 UI |

Ikon tag lama = badge + glyph menjadi satu gambar. Sekarang dipisah: satu `badge_cat.webp` bersama + glyph per ikon. Dengan begitu warna badge (Icon Color) dan warna glyph bisa diatur terpisah, dan skin bisa mengganti bentuk badge tanpa menggambar ulang 36 ikon.

## Ikon Tag — dipertahankan

| Lama | Nama lama | Baru |
|---|---|---|
| `tag/icon_011.webp` | `mail` | `tag/icon_mail.webp` |
| `tag/icon_012.webp` | `chat_bubble` | `tag/icon_chat.webp` |
| `tag/icon_013.webp` | `contacts` | `tag/icon_contacts.webp` |
| `tag/icon_008.webp` | `music_note` | `tag/icon_music_note.webp` |
| `tag/icon_035.webp` | `video_film` | `tag/icon_film.webp` |
| `tag/icon_010.webp` | `camera_a` | `tag/icon_camera.webp` |
| `tag/icon_020.webp` | `gallery` | `tag/icon_image.webp` |
| `tag/icon_031.webp` | `gamepad` | `tag/icon_gamepad.webp` |
| `tag/icon_033.webp` | `palette_a` | `tag/icon_palette.webp` |
| `tag/icon_007.webp` | `globe` | `tag/icon_globe.webp` |
| `tag/icon_001.webp` | `cloud` | `tag/icon_cloud.webp` |
| `tag/icon_002.webp` | `google` | `tag/icon_google.webp` |
| `tag/icon_044.webp` | `network` | `tag/icon_network.webp` |
| `tag/icon_018.webp` | `download_tray` | `tag/icon_download.webp` |
| `tag/icon_024.webp` | `briefcase` | `tag/icon_briefcase.webp` |
| `tag/icon_014.webp` | `calendar` | `tag/icon_calendar.webp` |
| `tag/icon_015.webp` | `alarm_clock` | `tag/icon_alarm_clock.webp` |
| `tag/icon_017.webp` | `folder` | `tag/icon_folder.webp` |
| `tag/icon_022.webp` | `news` | `tag/icon_newspaper.webp` |
| `tag/icon_029.webp` | `book_open` | `tag/icon_book.webp` |
| `tag/icon_030.webp` | `graduation_cap` | `tag/icon_graduation_cap.webp` |
| `tag/icon_061.webp` | `calculator` | `tag/icon_calculator.webp` |
| `tag/icon_027.webp` | `bar_chart` | `tag/icon_bar_chart.webp` |
| `tag/icon_028.webp` | `shopping_cart` | `tag/icon_cart.webp` |
| `tag/icon_016.webp` | `weather` | `tag/icon_weather.webp` |
| `tag/icon_023.webp` | `location_pin` | `tag/icon_location_pin.webp` |
| `tag/icon_026.webp` | `medical_plus` | `tag/icon_medical_cross.webp` |
| `tag/icon_025.webp` | `fitness_run` | `tag/icon_running.webp` |
| `tag/icon_041.webp` | `home` | `tag/icon_home.webp` |
| `tag/icon_090.webp` | `star` | `tag/icon_star.webp` |
| `tag/icon_003.webp` | `settings_tools` | `tag/icon_gear_wrench.webp` |
| `tag/icon_042.webp` | `shield` | `tag/icon_shield.webp` |
| `tag/icon_004.webp` | `brain` | `tag/icon_brain.webp` |
| `tag/icon_078.webp` | `terminal` | `tag/icon_terminal.webp` |
| `tag/icon_077.webp` | `code` | `tag/icon_code.webp` |
| `tag/icon_079.webp` | `database` | `tag/icon_database.webp` |

## Ikon Tag — dihapus (64)

| Lama | Nama lama | Alasan |
|---|---|---|
| `tag/icon_005.webp` | `hammer` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_006.webp` | `sliders` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_009.webp` | `play` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_019.webp` | `camera_b` | duplikat/varian dari `icon_010` (camera_a) |
| `tag/icon_021.webp` | `share_a` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_032.webp` | `messages_dots` | duplikat/varian dari `icon_012` (chat_bubble) |
| `tag/icon_034.webp` | `disc` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_036.webp` | `microphone` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_037.webp` | `broadcast` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_038.webp` | `monitor` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_039.webp` | `play_b` | duplikat/varian dari `icon_009` (play) |
| `tag/icon_040.webp` | `ticket` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_043.webp` | `key` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_045.webp` | `storage_stack` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_046.webp` | `bluetooth` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_047.webp` | `wifi` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_048.webp` | `battery` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_049.webp` | `brightness` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_050.webp` | `volume` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_051.webp` | `accessibility` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_052.webp` | `waveform` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_053.webp` | `search` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_054.webp` | `scan_ocr` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_055.webp` | `translate` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_056.webp` | `notes` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_057.webp` | `markup` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_058.webp` | `widgets` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_059.webp` | `document` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_060.webp` | `screencast` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_062.webp` | `stopwatch` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_063.webp` | `timer` | duplikat/varian dari `icon_062` (stopwatch) |
| `tag/icon_064.webp` | `compass` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_065.webp` | `ruler_a` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_066.webp` | `ruler_level` | duplikat/varian dari `icon_065` (ruler_a) |
| `tag/icon_067.webp` | `barcode` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_068.webp` | `qr_code` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_069.webp` | `fingerprint` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_070.webp` | `password` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_071.webp` | `login` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_072.webp` | `logout` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_073.webp` | `user` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_074.webp` | `group` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_075.webp` | `tie` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_076.webp` | `server_list` | duplikat/varian dari `icon_045` (storage_stack) |
| `tag/icon_080.webp` | `folder_open` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_081.webp` | `trash` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_082.webp` | `archive` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_083.webp` | `sync` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_084.webp` | `google_drive` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_085.webp` | `arrow_up` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_086.webp` | `download` | duplikat/varian dari `icon_018` (download_tray) |
| `tag/icon_087.webp` | `upload` | duplikat/varian dari `icon_085` (arrow_up) |
| `tag/icon_088.webp` | `share_b` | duplikat/varian dari `icon_021` (share_a) |
| `tag/icon_089.webp` | `pin` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_091.webp` | `bookmark` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_092.webp` | `flag` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_093.webp` | `tag` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_094.webp` | `bell` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_095.webp` | `gift` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_096.webp` | `palette_b` | duplikat/varian dari `icon_033` (palette_a) |
| `tag/icon_097.webp` | `brush` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_098.webp` | `pencil` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_099.webp` | `eraser` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |
| `tag/icon_100.webp` | `link` | tidak relevan sebagai kategori tag aplikasi (aksi/UI atau terlalu spesifik) |

## UI Icon — dipertahankan

| Lama | Nama lama | Baru |
|---|---|---|
| `ui/icon_001.webp` | `pencil` | `ui/icon_pencil.webp` |
| `ui/icon_004.webp` | `search_a` | `ui/icon_search.webp` |
| `ui/icon_003.webp` | `settings_a` | `ui/icon_gear.webp` |
| `ui/icon_012.webp` | `more_horizontal` | `ui/icon_dots_horizontal.webp` |
| `ui/icon_085.webp` | `add` | `ui/icon_plus.webp` |
| `ui/icon_086.webp` | `remove` | `ui/icon_minus.webp` |
| `ui/icon_087.webp` | `trash` | `ui/icon_trash.webp` |
| `ui/icon_110.webp` | `close` | `ui/icon_close.webp` |
| `ui/icon_109.webp` | `check` | `ui/icon_check.webp` |
| `ui/icon_092.webp` | `clipboard_copy` | `ui/icon_copy.webp` |
| `ui/icon_094.webp` | `undo_a` | `ui/icon_undo.webp` |
| `ui/icon_096.webp` | `redo` | `ui/icon_redo.webp` |
| `ui/icon_098.webp` | `reload` | `ui/icon_refresh.webp` |
| `ui/icon_030.webp` | `arrow_right` | `ui/icon_arrow_right.webp` |
| `ui/icon_115.webp` | `menu` | `ui/icon_menu.webp` |
| `ui/icon_116.webp` | `apps_grid` | `ui/icon_grid.webp` |
| `ui/icon_117.webp` | `list_a` | `ui/icon_list.webp` |
| `ui/icon_102.webp` | `filter` | `ui/icon_filter.webp` |
| `ui/icon_111.webp` | `info` | `ui/icon_info.webp` |
| `ui/icon_112.webp` | `warning` | `ui/icon_warning.webp` |
| `ui/icon_113.webp` | `help_a` | `ui/icon_help.webp` |
| `ui/icon_061.webp` | `lock` | `ui/icon_lock.webp` |
| `ui/icon_062.webp` | `unlock` | `ui/icon_lock_open.webp` |
| `ui/icon_013.webp` | `star` | `ui/icon_star.webp` |
| `ui/icon_002.webp` | `heart` | `ui/icon_heart.webp` |
| `ui/icon_014.webp` | `bookmark` | `ui/icon_bookmark.webp` |
| `ui/icon_015.webp` | `tag` | `ui/icon_tag.webp` |
| `ui/icon_009.webp` | `folder_a` | `ui/icon_folder.webp` |
| `ui/icon_006.webp` | `home` | `ui/icon_home.webp` |
| `ui/icon_007.webp` | `user` | `ui/icon_user.webp` |
| `ui/icon_031.webp` | `share` | `ui/icon_share.webp` |
| `ui/icon_016.webp` | `bell_a` | `ui/icon_bell.webp` |
| `ui/icon_082.webp` | `palette` | `ui/icon_palette.webp` |
| `ui/icon_035.webp` | `image` | `ui/icon_image.webp` |
| `ui/icon_054.webp` | `cloud` | `ui/icon_cloud.webp` |
| `ui/icon_052.webp` | `download` | `ui/icon_download.webp` |
| `ui/icon_053.webp` | `upload` | `ui/icon_upload.webp` |
| `tag/icon_089.webp` | (set tag) | `ui/icon_pin.webp` |

## UI Icon — baru (tidak ada di set lama)

| Baru | Cara dibuat |
|---|---|
| `ui/icon_dots_vertical.webp` | generated — menu lainnya (overflow) |
| `ui/icon_arrow_left.webp` | mirrored — kembali (cermin dari arrow_right) |
| `ui/icon_chevron_left.webp` | generated — kembali / buka sub-halaman |
| `ui/icon_chevron_right.webp` | generated — masuk / lanjut |
| `ui/icon_chevron_up.webp` | generated — ciutkan |
| `ui/icon_chevron_down.webp` | generated — bentangkan |
| `ui/icon_drag_handle.webp` | generated — pegangan seret / urutkan |

Set lama tidak punya chevron, panah kiri, titik tiga vertikal, maupun pegangan seret, padahal screenshot memakai `chevron_left` (header "Launcher theme") dan `drag_handle` (daftar tag).

## UI Icon — dihapus (83)

Dihapus karena (a) duplikat/varian gaya (mis. `search_b`, `bell` kedua, `folder_b`, `settings` kedua), atau (b) fungsi yang tidak dipakai launcher: status perangkat (wifi, bluetooth, NFC, baterai, senter), media player, perangkat keras (printer, tablet, VR), editor teks/gambar (crop, gunting, zoom), dsb. Bila fitur baru membutuhkannya, ambil kembali dari zip asli dan konversi dengan `tools/legacy_build/build_assets.py` (catatan asal-usul; lihat README di folder itu) atau gambar ulang mengikuti `docs/03_REFERENSI_ASET.md`.

| Lama | Nama lama | Alasan |
|---|---|---|
| `ui/icon_005.webp` | `search_b` | duplikat/varian dari `icon_004` (search_a) |
| `ui/icon_008.webp` | `group` | tidak dipakai launcher |
| `ui/icon_010.webp` | `folder_b` | duplikat/varian dari `icon_009` (folder_a) |
| `ui/icon_011.webp` | `document` | tidak dipakai launcher |
| `ui/icon_017.webp` | `bell_b` | duplikat/varian dari `icon_016` (bell_a) |
| `ui/icon_018.webp` | `calendar_a` | tidak dipakai launcher |
| `ui/icon_019.webp` | `calendar_day` | tidak dipakai launcher |
| `ui/icon_020.webp` | `alarm_a` | tidak dipakai launcher |
| `ui/icon_021.webp` | `alarm_b` | duplikat/varian dari `icon_020` (alarm_a) |
| `ui/icon_022.webp` | `brightness` | tidak dipakai launcher |
| `ui/icon_023.webp` | `location_a` | tidak dipakai launcher |
| `ui/icon_024.webp` | `location_b` | duplikat/varian dari `icon_023` (location_a) |
| `ui/icon_025.webp` | `mail` | tidak dipakai launcher |
| `ui/icon_026.webp` | `chat_dots` | tidak dipakai launcher |
| `ui/icon_027.webp` | `chat_bubble` | tidak dipakai launcher |
| `ui/icon_028.webp` | `reply` | tidak dipakai launcher |
| `ui/icon_029.webp` | `forward` | tidak dipakai launcher |
| `ui/icon_032.webp` | `attachment` | tidak dipakai launcher |
| `ui/icon_033.webp` | `link_a` | tidak dipakai launcher |
| `ui/icon_034.webp` | `link_b` | duplikat/varian dari `icon_033` (link_a) |
| `ui/icon_036.webp` | `camera` | tidak dipakai launcher |
| `ui/icon_037.webp` | `play` | tidak dipakai launcher |
| `ui/icon_038.webp` | `music` | tidak dipakai launcher |
| `ui/icon_039.webp` | `microphone` | tidak dipakai launcher |
| `ui/icon_040.webp` | `volume` | tidak dipakai launcher |
| `ui/icon_041.webp` | `volume_mute` | tidak dipakai launcher |
| `ui/icon_042.webp` | `battery_a` | tidak dipakai launcher |
| `ui/icon_043.webp` | `battery_b` | duplikat/varian dari `icon_042` (battery_a) |
| `ui/icon_044.webp` | `wifi` | tidak dipakai launcher |
| `ui/icon_045.webp` | `bluetooth` | tidak dipakai launcher |
| `ui/icon_046.webp` | `nfc` | tidak dipakai launcher |
| `ui/icon_047.webp` | `flashlight_a` | tidak dipakai launcher |
| `ui/icon_048.webp` | `flashlight_b` | duplikat/varian dari `icon_047` (flashlight_a) |
| `ui/icon_049.webp` | `refresh` | tidak dipakai launcher |
| `ui/icon_050.webp` | `sync_a` | tidak dipakai launcher |
| `ui/icon_051.webp` | `backup_restore` | tidak dipakai launcher |
| `ui/icon_055.webp` | `cloud_upload_a` | tidak dipakai launcher |
| `ui/icon_056.webp` | `cloud_upload_b` | duplikat/varian dari `icon_055` (cloud_upload_a) |
| `ui/icon_057.webp` | `database_a` | tidak dipakai launcher |
| `ui/icon_058.webp` | `database_b` | duplikat/varian dari `icon_057` (database_a) |
| `ui/icon_059.webp` | `network` | tidak dipakai launcher |
| `ui/icon_060.webp` | `shield_check` | tidak dipakai launcher |
| `ui/icon_063.webp` | `key` | tidak dipakai launcher |
| `ui/icon_064.webp` | `dialpad` | tidak dipakai launcher |
| `ui/icon_065.webp` | `calculator` | tidak dipakai launcher |
| `ui/icon_066.webp` | `ruler` | tidak dipakai launcher |
| `ui/icon_067.webp` | `ruler_pencil` | tidak dipakai launcher |
| `ui/icon_068.webp` | `compass` | tidak dipakai launcher |
| `ui/icon_069.webp` | `barcode` | tidak dipakai launcher |
| `ui/icon_070.webp` | `qr_code` | tidak dipakai launcher |
| `ui/icon_071.webp` | `fingerprint_a` | tidak dipakai launcher |
| `ui/icon_072.webp` | `fingerprint_b` | duplikat/varian dari `icon_071` (fingerprint_a) |
| `ui/icon_073.webp` | `printer` | tidak dipakai launcher |
| `ui/icon_074.webp` | `scan_frame` | tidak dipakai launcher |
| `ui/icon_075.webp` | `cast` | tidak dipakai launcher |
| `ui/icon_076.webp` | `monitor` | tidak dipakai launcher |
| `ui/icon_077.webp` | `tv` | tidak dipakai launcher |
| `ui/icon_078.webp` | `tablet` | tidak dipakai launcher |
| `ui/icon_079.webp` | `phone` | tidak dipakai launcher |
| `ui/icon_080.webp` | `vr_headset` | tidak dipakai launcher |
| `ui/icon_081.webp` | `gamepad` | tidak dipakai launcher |
| `ui/icon_083.webp` | `brush_a` | tidak dipakai launcher |
| `ui/icon_084.webp` | `brush_b` | tidak dipakai launcher |
| `ui/icon_088.webp` | `archive` | tidak dipakai launcher |
| `ui/icon_089.webp` | `translate` | tidak dipakai launcher |
| `ui/icon_090.webp` | `edit_square` | tidak dipakai launcher |
| `ui/icon_091.webp` | `clipboard` | tidak dipakai launcher |
| `ui/icon_093.webp` | `cut` | tidak dipakai launcher |
| `ui/icon_095.webp` | `undo_b` | tidak dipakai launcher |
| `ui/icon_097.webp` | `power` | tidak dipakai launcher |
| `ui/icon_099.webp` | `eject` | tidak dipakai launcher |
| `ui/icon_100.webp` | `sort_updown` | tidak dipakai launcher |
| `ui/icon_101.webp` | `swap_vertical` | tidak dipakai launcher |
| `ui/icon_103.webp` | `zoom_in` | tidak dipakai launcher |
| `ui/icon_104.webp` | `zoom_out` | tidak dipakai launcher |
| `ui/icon_105.webp` | `fullscreen` | tidak dipakai launcher |
| `ui/icon_106.webp` | `crop_a` | tidak dipakai launcher |
| `ui/icon_107.webp` | `crop_b` | duplikat/varian dari `icon_106` (crop_a) |
| `ui/icon_108.webp` | `sync_b` | tidak dipakai launcher |
| `ui/icon_114.webp` | `help_b` | duplikat/varian dari `icon_113` (help_a) |
| `ui/icon_118.webp` | `list_b` | duplikat/varian dari `icon_117` (list_a) |
| `ui/icon_119.webp` | `settings_b` | duplikat/varian dari `icon_003` (settings_a) |
| `ui/icon_120.webp` | `shield` | duplikat/varian dari `icon_060` (shield_check) |

## Komponen

| Lama | Baru | Catatan |
|---|---|---|
| `top_frame/topframe_spritesheet{,@2x,@4x}.png` | sama (nama tetap) | jadi mask putih, tepi dipertajam; 1x/2x diturunkan dari 4x. Koordinat atlas tidak berubah. |
| `top_frame/topframe_full_512.png`, `…@4x.png` | dihapus | turunan/preview, tidak dipakai atlas |
| `top_frame/preview_stretch.png` | `previews/default_frames.png` | preview dibuat ulang |
| `tag_frame/frame_spritesheet_smooth.webp` | `tag_frame/frame_spritesheet.webp` | mask putih; kata "smooth" dibuang dari nama |
| `tag_frame/frame_spritesheet_smooth.json` | `tag_frame/frame_spritesheet.json` | `image` diperbarui + data ukur |
| `tag_frame/frame_preview_heights_smooth.webp` | `previews/default_frames.png` | preview dibuat ulang |
| (baru) | `tag_badge/badge_cat.webp` + `badge.json` | badge kepala kucing kanonik untuk ikon tag |

**Catatan runtime:** perubahan nama file ikon dan format mask memerlukan penyesuaian `IconCatalogRepository` (path `assets/ikon/`, nama, dan komposisi badge+glyph). Atlas TopFrame dan spec TagFrame tidak berubah geometrinya.
