# Changelog

## v3 — perapian struktur dan panduan

**Masalah yang ditemukan di zip v2**

- Dua kit tercampur: "Reskin Assets v2" (ikon + komponen NekOS) dan "Skin Dialog Kit" (motif). Ada dua README (`README.md`, `README.txt`), dua folder pratinjau (`preview/`, `previews/`), dan folder `motif/` + `motifs.json` yang tidak terhubung ke spesifikasi skin.
- `docs/01` dan `docs/02` (panduan motif lama) sudah usang: merujuk folder `frame/` dan `preview/frame_stretch_preview.png` yang tidak ada, menyebut warna frame `#1A4A58` (sekarang frame adalah mask putih), dan membahas Unity/Godot/Web padahal NekOS adalah Android.
- Tidak ada definisi **paket skin `.zip`**: struktur root, batas ukuran, aturan keamanan.
- `skin_manifest.example.json` hanya mencantumkan 3 aset berstatus `source_only`; motif tidak punya tempat di manifest.
- `motifs.json` memakai `suggested_display_px` yang ambigu (px atau dp).
- `MIGRATION.md`: judul "UI Icon — dihapus (82)" padahal tabelnya 83 baris (37 dipertahankan + 83 dihapus = 120).
- Metadata JSON komponen menunjuk path lama `reskin_assets/source/...`.
- Validator lama tidak memeriksa motif, `skin.json`, zip, atau konsistensi slice TopFrame/TagFrame.

**Perubahan**

- Struktur baru: `default_skin/` (skin bawaan sekaligus referensi, tata letak sama dengan isi zip), `docs/`, `specs/`, `templates/`, `previews/`, `migration/`, `tests/fixtures/`, `tools/`.
- Motif digabung ke skin (`motifs/`, terdaftar di `skin.json` → `motifs[]`); `suggested_display_px` → `suggested_display_dp` (asumsi dp, ditandai).
- Panduan baru: `01` implementasi launcher (fase, arsitektur, draf Kotlin, installer zip aman, migrasi, pengujian), `02` membuat skin (alur, aturan tiap aset, pesan validator, kemas). `03` = spesifikasi lama + motif + `skin.json` + paket zip + daftar keputusan terbuka.
- Tools baru: `validate_skin.py` (folder/zip, keamanan zip, motif, slice TopFrame/TagFrame, dimensi gambar), `pack_skin.py`, `new_skin.py`, `to_mask.py`, `preview_skin.py`. `make_motif.py` kini mencetak snippet `skin.json`.
- `migration/legacy_icon_map.json`: 220 entri hasil parse `MIGRATION.md` (kept/merged/removed).
- `tests/fixtures/`: zip uji untuk installer.
- Pratinjau dibuat ulang dari aset saat ini (`previews/default_*.png`); `tile_check_2x2.png` (2,3 MB) diganti `default_motifs.png`.
- Skema: `asset_manifest.schema.json` → `skin_manifest.schema.json` (tambah `motifs`, `compatibility`; `assets` opsional).

**Tidak diubah**: isi gambar ikon, badge, TopFrame, TagFrame, motif (byte-identik dengan v2), koordinat atlas, geometri.

**Temuan untuk diputuskan** (rincian: `docs/03_REFERENSI_ASET.md` §12)

- Glyph krem `#F5F2ED` di atas badge terang (Silver contoh) hanya 1,42 : 1.
- 60 ikon tag lama dihapus tanpa padanan: tag pengguna yang memakainya perlu ikon fallback.
- Skala tampil TagFrame dan hubungan WebP sumber vs PNG runtime belum terverifikasi.
- Motif `paw` punya bintik kecil di dalam telapak (noise sumber AI); kosmetik.
