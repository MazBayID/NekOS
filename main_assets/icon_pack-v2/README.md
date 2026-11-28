# NekOS Icon Pack · Essentials

Paket ikon aplikasi bergaya NekOS: sederhana, solid, monokrom, dan dapat diwarnai saat dirender.

## Isi

- `icons/apps_full/` — ikon khusus per aplikasi yang sudah memiliki artwork.
- `icons/apps/` — **102 glyph** 96×96 px (WebP lossless, mask putih + alpha, live area 80×80), sekitar **96 KB**.
- `icons/apps/icons_index.json` / `.md` — katalog glyph.
- `app_icon_map.json` — peta package aplikasi ke ikon atau glyph.
- `tools/import_appfilter.py` — impor package dari format `appfilter.xml`.
- `tools/map_packages.py` + `tools/keyword_rules.json` — pemetaan daftar package melalui aturan keyword.
- `preview_glyphs.png` dan `preview_apps.png` — pratinjau aset.

## Format app icon map

Schema versi 2 mendukung dua jenis entri:

- `kind: "bespoke"` atau `kind: "monogram"`: `icon` menunjuk ke file di `icons/apps_full/`.
- `kind: "glyph"`: `icon: null`; runtime mengambil `glyph` dari `icons/apps/` dan dapat merendernya tanpa membuat file per aplikasi.

Setiap entri menyimpan `package`, `name`, dan `glyph`. Resolver memprioritaskan ikon khusus jika tersedia. Entri `source: exact` dan `source: rule` mempertahankan glyph katalog; entri `source: default` menjalankan ulang `keyword_rules.json` memakai package name dan label aplikasi yang terpasang sebelum memakai `default_glyph`. Package yang tidak terdaftar juga dicocokkan dengan `keyword_rules.json`.

## Aset ikon per aplikasi

`icons/apps_full/<package>.webp` berisi artwork khusus atau monogram statis untuk entri yang sudah memiliki file. Ukuran target 96×96 px; total aset saat ini sekitar 795 KB.

- `kind: bespoke` (59 aplikasi): artwork khas bergaya NekOS.
- `kind: monogram` (640 aplikasi): glyph kategori dan inisial aplikasi yang sudah dirender menjadi file.
- `kind: glyph`: entri baru hasil impor yang memakai glyph katalog secara langsung; tidak membutuhkan file `apps_full`.

Ikon khas merupakan interpretasi visual bergaya NekOS, bukan salinan logo resmi.

## Impor appfilter.xml

Skrip mendukung format ADW/Nova yang umum digunakan oleh launcher Android.

```bash
python3 tools/import_appfilter.py appfilter.xml -o extra_map.json
```

Untuk menambahkan package baru ke peta utama:

```bash
python3 tools/import_appfilter.py appfilter.xml --merge
```

Mode `--merge`:
- Tidak menimpa entri package yang sudah ada.
- Menambahkan entri baru sebagai `kind: "glyph"` dan `icon: null`.
- Memvalidasi glyph yang dirujuk oleh aturan.
- Memperbarui `total_apps`, `glyphs_used`, `icons_bespoke`, dan `icons_monogram`.
- Menulis file secara atomik agar peta lama tidak rusak jika proses penulisan gagal.

Hasil tebakan keyword sebaiknya ditinjau sebelum dipakai. Gunakan output `extra_map.json` terlebih dahulu jika ingin memeriksa hasil tanpa mengubah peta utama.

## Pemetaan dari daftar teks

```bash
python3 tools/map_packages.py packages.txt -o extra_map.json
```

Format input: satu package per baris atau CSV `package.name,Nama Aplikasi`. Nama aplikasi yang mengandung koma dapat ditulis dengan tanda kutip CSV. Baris kosong dan komentar yang diawali `#` diabaikan.

Setiap hasil mencantumkan `source`:
- `exact`: package sudah ada di peta.
- `rule`: glyph dipilih oleh aturan keyword.
- `default`: tidak ada aturan yang cocok pada saat pembuatan katalog; runtime akan mencoba aturan lagi memakai label aplikasi yang terpasang sebelum memakai `default_glyph`.

## Catatan

- Pemetaan menggunakan package name Android; package dapat berbeda antarnegara atau versi aplikasi.
- Urutan aturan keyword bersifat penting: aturan pertama yang cocok akan digunakan.
- `paw` adalah glyph biasa sekaligus nilai default glyph saat ini. Runtime icon pack mengelola namespace-nya secara terpisah dari Skin, Tag Icons, dan Motifs.

The `9BIT` WebAPK may expose an opaque generated package ID, so its label rule (`\b9bit\b`) is intentionally kept first in `keyword_rules.json`. Keep this rule ahead of broader browser rules so the installed label can resolve to the `globe` glyph.
