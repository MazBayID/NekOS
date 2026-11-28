# NekOS Icon Pack · Essentials

Paket ikon aplikasi bawaan bergaya NekOS: simpel, solid, monochrome, dan bisa di-tint.

## Isi
- `icons/apps_full/` — ikon per aplikasi (lihat bagian di bawah).
- `icons/apps/` — **102 glyph** 96×96 px (WebP lossless, mask putih + alpha, live area 80×80). Total ±440 KB.
  36 glyph dan 17 ikon UI diambil dari template NekOS, 49 glyph baru digambar dengan gaya yang sama (termasuk `paw` sebagai ikon bawaan).
- `icons/apps/icons_index.json` / `.md` — katalog glyph (skema sama dengan template).
- `app_icon_map.json` — **699 aplikasi** (package name → glyph), mencakup aplikasi internasional dan Indonesia.
- `tools/import_appfilter.py` — impor `appfilter.xml` dari icon pack open-source (ribuan package asli + nama aplikasi) ke peta ini.
- `tools/map_packages.py` + `tools/keyword_rules.json` — memetakan daftar package biasa (teks) secara otomatis.
- `preview_glyphs.png` — pratinjau seluruh glyph.

## Ikon per aplikasi
`icons/apps_full/<package>.webp` — **satu file ikon untuk setiap aplikasi** di `app_icon_map.json` (kolom `icon`, `kind`), 96×96 px, mask putih, total ±780 KB.
- `kind: bespoke` (59 aplikasi): ikon khas hasil gambar sendiri bergaya NekOS (WhatsApp, X, TikTok, Facebook, Instagram, YouTube, Telegram, LinkedIn, Snapchat, Reddit, Threads, Messenger, Discord, Spotify, Netflix, Gmail, Chrome, Drive, ChatGPT, Claude, GitHub, Shopee, Tokopedia, Gojek, Grab, DANA, OVO, Play Store, Signal, Pinterest, Twitch, Word/Excel/PowerPoint/Outlook, Termux, Gemini, Maps).
- `kind: monogram` (sisanya): glyph kategori + lencana inisial nama aplikasi, jadi tiap aplikasi punya ikon berbeda.
Ikon khas adalah tanda sederhana bergaya sendiri, bukan salinan logo resmi.

## Cara pakai
- Satu glyph dipakai banyak aplikasi (hemat memori). Cari `package` di `app_icon_map.json`, lalu muat `icons/apps/icon_<glyph>.webp`.
- Aplikasi yang tidak terdaftar memakai `default_glyph` (`paw`).
- Warna tidak ada di file: tint glyph dengan warna "on badge" di atas badge yang di-tint warna Shared UI (komposisi sama seperti ikon tag).

## Memperluas ke ribuan aplikasi
```
# 1) unduh appfilter.xml dari icon pack open-source, mis. Lawnicons (Apache-2.0):
#    github.com/LawnchairLauncher/lawnicons -> app/assets/appfilter.xml
# 2) gabungkan ke peta:
python3 tools/import_appfilter.py appfilter.xml --merge
```
Hasil tebakan kata kunci sebaiknya ditinjau; jalankan tanpa `--merge` (`-o extra_map.json`) untuk melihat kolom `source` dulu.

### Dari daftar teks
```
python3 tools/map_packages.py packages.txt -o extra_map.json
```
`packages.txt` berisi satu package per baris (opsional `,Nama`). Hasil bertanda `"source": "exact"` (pasti), `"rule"` (tebakan kata kunci, perlu ditinjau), atau `"default"`.

## Catatan
Pemetaan berdasarkan package name; beberapa aplikasi memakai package berbeda per negara/versi, jadi tidak semua perangkat akan cocok. Format paket ini mengikuti struktur template NekOS yang diberikan; sesuaikan bila launcher memakai skema pemetaan lain.
