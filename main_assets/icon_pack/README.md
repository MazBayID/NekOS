# NekOS App Icon Pack

Kumpulan ikon merek dan glyph kategori untuk rancangan sistem ikon aplikasi NekOS. Paket ini berisi data, aturan pencocokan, serta alat pembuat dan validator mapping.

## Status integrasi saat ini

<ins>Icon pack ini belum digunakan oleh launcher NekOS saat runtime.</ins>

Audit kode aplikasi menunjukkan:

- `AppIconBitmapCache.kt` masih mengambil ikon melalui `LauncherActivityInfo.getBadgedIcon()` dan memakai ikon default Android jika gagal.
- `app/build.gradle.kts` kini mendaftarkan `main_assets/icon_pack/` sebagai Android `main` assets. File paket akan disertakan saat proses packaging APK.
- `IconCatalogRepository.kt` hanya memuat katalog ikon tag dari `icons/tag/`; repository tersebut bukan loader untuk app icon pack.

Jadi, perubahan pada JSON atau file gambar di folder ini **belum mengubah ikon aplikasi yang terlihat di launcher**. Tahap packaging assets sudah disiapkan, tetapi runtime masih memerlukan resolver aplikasi, cache, dan rendering. Build APK belum dijalankan untuk membuktikan seluruh aset benar-benar masuk ke APK.

Dokumentasi Android: [LauncherActivityInfo](https://developer.android.com/reference/android/content/pm/LauncherActivityInfo).

## Isi dan jumlah aset

| Komponen | Jumlah | Fungsi |
|---|---:|---|
| Ikon merek, `icons/brands/` | 3.369 | Identitas merek aplikasi |
| Glyph kategori, `icons/apps/` | 109 | Fallback kategori generik |
| Package exact, `app_icon_map.json` | 1.418 | Mapping package yang ditulis eksplisit |
| Package exact dengan ikon merek | 362 | Mapping exact yang punya brand |
| Glyph unik yang dipakai mapping exact | 88 | Bagian dari 109 glyph tersedia |

Jumlah ikon merek tidak harus sama dengan jumlah package Android. Direktori merek berasal dari katalog Simple Icons yang juga berisi layanan web, produk desktop, dan merek yang tidak mempunyai aplikasi Android. `brand_index.json` adalah katalog merek; `app_icon_map.json` adalah daftar package Android yang diketahui. Keduanya punya tujuan berbeda.

Package name di `app_icon_map.json` disusun dari pengetahuan umum dan **belum diverifikasi satu per satu terhadap Play Store atau perangkat pengguna**. Mapping exact berarti package tersebut sengaja dicantumkan di file proyek, bukan jaminan bahwa package terpasang pada semua perangkat.

## Struktur direktori

```
main_assets/icon_pack/
├── app_icon_map.json
├── app_icon_rules.json
├── brand_index.json
├── icons/
│   ├── apps/
│   │   ├── icon_<glyph>.webp
│   │   └── icons_index.json
│   └── brands/
│       └── <slug>.webp
├── tools/
│   ├── map_packages.py
│   └── validate_pack.py
├── LICENSES/
│   └── simple-icons-LICENSE.md
├── preview_brands.png
└── preview_glyphs.png
```

Semua path di dalam JSON bersifat relatif terhadap direktori `main_assets/icon_pack/`.

## Aturan resolusi yang dimaksud

Resolver aplikasi nantinya harus mengikuti aturan berikut. Urutan ini juga menjadi acuan `map_packages.py`.

### 1. Package yang ada di `app_icon_map.json`

- Jika entri exact memiliki `brand`, gunakan ikon merek sebagai ikon utama dan `glyph` sebagai fallback.
- Jika entri exact tidak memiliki `brand`, gunakan `glyph` exact.
- Entri exact tidak boleh ditimpa oleh tebakan dari label, prefix, atau keyword.

### 2. Package yang belum ada di mapping exact

- Normalisasi label aplikasi: huruf kecil, `+` menjadi `plus`, `&` menjadi `and`, lalu buang karakter selain `a-z0-9`.
- Cocokkan hasilnya secara exact dengan slug atau alias di `brand_index.json`. Jika cocok, gunakan ikon merek.
- Glyph kategori fallback dipilih secara terpisah: `prefix_rules` (prefix terpanjang), lalu `keyword_rules` (regex pertama yang cocok, case-insensitive), lalu `default_glyph`.

Pencocokan label tidak memakai fuzzy matching atau pencocokan sebagian. Ini disengaja untuk mengurangi risiko aplikasi yang namanya hanya mirip mendapat logo merek yang salah. Alias generik seperti `foto` tidak digunakan karena dapat menunjuk ke banyak aplikasi galeri yang berbeda.

### Arti field hasil mapper

- `source`: asal pemilihan glyph, yaitu `exact`, `prefix`, `rule`, atau `default`.
- `brand_source`: asal ikon merek, yaitu `exact` atau `label`.
- `brand`: slug ikon merek. Field ini opsional; `glyph` tetap disediakan sebagai fallback.

Dengan demikian, package yang cocok dengan brand melalui label tetap dapat mempunyai glyph kategori hasil prefix/keyword. Kedua field tersebut bukan dua keputusan yang saling menimpa.

## Namespace ikon dan nama `paw`

Jalur aset harus tetap terpisah:

- `icons/apps/`: glyph kategori app icon pack.
- `icons/brands/`: ikon merek app icon pack.
- `icons/tag/`: katalog ikon tag skin NekOS.
- `motifs/`: motif dekoratif skin, termasuk motif paw bila tersedia.

Nama glyph `paw` pada app icon pack tidak bentrok dengan motif atau tag bernama serupa selama resolver memakai path dan katalog yang benar. Jangan gabungkan app icon pack ke `IconCatalogRepository`, karena repository tersebut secara sengaja membatasi path ke `icons/tag/`.

## Format aset

- Glyph dan ikon merek: WebP, 96×96, RGBA.
- Foreground ikon adalah mask putih dengan alpha; warna diterapkan oleh renderer.
- Ikon merek dinormalisasi ke kanvas 96×96 agar ukuran optiknya relatif konsisten.
- Glyph kategori merupakan gambar generik proyek NekOS.
- Ikon merek bersumber dari Simple Icons (CC0 1.0). CC0 pada file sumber tidak menghapus hak merek dagang atas logo; ikuti pedoman pemilik merek sebelum distribusi komersial.

Lisensi: [LICENSES/simple-icons-LICENSE.md](LICENSES/simple-icons-LICENSE.md).

## Menghasilkan mapping tambahan

Ambil daftar package dari perangkat Android:

```bash
adb shell pm list packages | sed 's/package://' > packages.txt
python3 tools/map_packages.py packages.txt -o extra_map.json
```

Gunakan `pm list packages -3` sebagai ganti perintah di atas jika hanya ingin aplikasi pihak ketiga.

Hasil `extra_map.json` adalah mapping tambahan untuk ditinjau, bukan pengganti otomatis `app_icon_map.json`. Periksa kembali setiap hasil `prefix`, `rule`, dan `default`; hasil heuristic bukan identifikasi yang terverifikasi. Hasil brand dari label juga perlu ditinjau bila label aplikasi ambigu.

## Validasi

Validator memeriksa:

- struktur JSON dan konsistensi jumlah metadata;
- package duplikat, referensi glyph, brand, alias, dan default;
- konsistensi aturan prefix/keyword dengan file glyph yang tersedia;
- sinkronisasi `icons_index.json` dan `brand_index.json`;
- format WebP, ukuran 96×96 RGBA, mask putih, dan batas live area.

Jalankan dari direktori paket:

```bash
python3 -m pip install pillow numpy
python3 -I tools/validate_pack.py
```

Validator memeriksa file gambar sebenarnya, bukan hanya nama file atau JSON. Jalankan kembali setelah mengganti atau menambah aset.

## Batasan dan pekerjaan lanjutan

- Mapping package exact belum diverifikasi satu per satu pada Play Store.
- Brand index hanya mencakup merek yang tersedia di Simple Icons; banyak aplikasi lokal atau merek yang tidak tercakup akan memakai glyph kategori.
- Prefix dan keyword adalah heuristik, bukan bukti identitas aplikasi.
- Runtime resolver dan packaging Android belum dibuat. Integrasi itu harus dilakukan terpisah dan diuji tanpa mengubah katalog tag atau motif skin.
