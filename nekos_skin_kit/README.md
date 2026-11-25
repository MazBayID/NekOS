# NekOS Skin Kit (v3)

Aset bawaan NekOS + alat + panduan untuk membuat launcher **reskinable**. Skin dibagikan sebagai file **`.zip`**.

Semua gambar adalah **mask putih + alpha**; warna diberikan launcher saat runtime (tint). Skin hanya mengubah tampilan, tidak boleh mengubah peluncuran aplikasi, gestur, navigasi, penyimpanan, izin, atau aksesibilitas, dan tidak berisi kode.

## Mulai dari mana

| Peranmu | Baca |
|---|---|
| Pengembang NekOS (merombak launcher) | `docs/01_PANDUAN_IMPLEMENTASI_LAUNCHER.md` — mulai Fase 0–1 |
| Pembuat skin | `docs/02_PANDUAN_MEMBUAT_SKIN.md` |
| Siapa pun yang butuh angka/kontrak | `docs/03_REFERENSI_ASET.md` |
| Menyambungkan data/kode lama ke aset baru | `docs/04_MIGRASI_ASET_LAMA.md` + `migration/legacy_icon_map.json` |

## Struktur

```text
nekos_skin_kit/
├── README.md
├── CHANGELOG.md
├── docs/
│   ├── 01_PANDUAN_IMPLEMENTASI_LAUNCHER.md
│   ├── 02_PANDUAN_MEMBUAT_SKIN.md
│   ├── 03_REFERENSI_ASET.md          kontrak aset, skin.json, paket zip, keputusan terbuka
│   └── 04_MIGRASI_ASET_LAMA.md       peta file lama → baru
├── default_skin/                     skin bawaan = referensi; struktur SAMA dengan isi zip skin
│   ├── skin.json
│   ├── icons/tag/   36 glyph (96×96) + index      <- nama = bentuk
│   ├── icons/ui/    45 glyph (96×96) + index
│   ├── components/  tag_badge/ top_frame/ tag_frame/
│   └── motifs/      4 motif seamless 1024 (+@512)
├── specs/                            skin_manifest.schema.json, contoh manifest, template blueprint/token
├── templates/                        kanvas panduan PNG (badge, glyph, TopFrame, TagFrame)
├── previews/                         render skin bawaan (tag, UI, frame, motif)
├── migration/legacy_icon_map.json    100 ikon tag + 120 ikon UI lama → baru
├── tests/fixtures/                   zip uji: 2 valid, 6 sengaja rusak
└── tools/
    ├── validate_skin.py              validasi folder atau .zip
    ├── new_skin.py                   buat kerangka skin
    ├── to_mask.py                    gambar apa pun → mask putih sesuai spesifikasi
    ├── make_motif.py                 gambar apa pun → motif seamless 1024
    ├── preview_skin.py               lembar pratinjau skin
    ├── pack_skin.py                  validasi + kemas jadi <id>-<versi>.zip
    └── legacy_build/                 catatan asal-usul (tidak untuk dipakai; lihat README di dalamnya)
```

## Perintah cepat

Butuh Python 3.9+ dan `pip install pillow numpy`.

```bash
python3 -I tools/validate_skin.py default_skin --strict           # skin bawaan: harus 0 error, 0 warning
python3 -I tools/new_skin.py my_skin --name "My Skin"             # kerangka skin baru
python3 -I tools/validate_skin.py my_skin                         # validasi (folder atau .zip)
python3 -I tools/preview_skin.py my_skin --out previews/my_skin   # pratinjau
python3 -I tools/pack_skin.py my_skin --out dist                  # -> dist/my_skin-1.0.0.zip
```

## Ringkas

| | Lama | Sekarang |
|---|---|---|
| Ikon tag | 100 × 512 px berwarna | 36 glyph 96 px + 1 badge |
| Ikon UI | 120 × 512 px berwarna | 45 glyph 96 px |
| Penamaan | `icon_001` | `icon_brain`, `icon_terminal`, … |
| Warna | teal/krem dipanggang | mask putih, tint runtime |
| Ukuran aset ikon + komponen | ±25 MB | ±0,15 MB (+ ±2,9 MB bila memakai 4 motif) |
| Format skin | — | `.zip` dengan `skin.json` di root |

## Aturan

- px untuk kanvas/atlas, dp untuk layout, sp untuk teks. Master 4×: 1 dp = 4 px.
- `skin.json` adalah data deklaratif; jangan pernah mengeksekusi apa pun dari skin.
- Skin dari zip adalah input tidak tepercaya: ikuti batas di `docs/03_REFERENSI_ASET.md` §10.
- Hal yang belum terverifikasi dari kode NekOS dicatat di `docs/03_REFERENSI_ASET.md` §12; jangan dianggap fakta.
