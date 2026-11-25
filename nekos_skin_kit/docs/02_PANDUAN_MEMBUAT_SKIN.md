# 02 — Panduan Membuat Skin Baru NekOS

Untuk desainer/pembuat skin. Hasil akhirnya satu file `.zip` yang bisa diimpor ke NekOS. Kamu tidak perlu menulis kode.

> Kontrak teknis lengkap (ukuran, margin, aturan zip): `03_REFERENSI_ASET.md`. Dokumen ini adalah alur kerjanya.
> Impor skin `.zip` di launcher baru berfungsi setelah Fase 3 di `01_PANDUAN_IMPLEMENTASI_LAUNCHER.md` selesai. Sebelum itu, validasi dan pratinjau lewat tools di kit tetap bisa dipakai.

---

## 1. Konsep dalam 1 menit

- Skin = kumpulan **gambar mask putih** + `skin.json`, dikemas sebagai `.zip`.
- **Mask putih** artinya: piksel berisi berwarna `RGB 255,255,255`; bentuknya hanya ada di **alpha** (transparansi). Warna akhir diberikan launcher (tint) mengikuti tema pengguna. Jangan memanggang warna ke gambar.
- Skin **boleh parsial**. Gambar yang tidak kamu sediakan memakai gambar bawaan.
- Skin hanya mengubah **tampilan**. Tidak ada kode, tidak ada perubahan perilaku.
- Ukuran kanvas dan posisi slot **tidak boleh diubah**; isi gambarnya bebas.

Yang bisa diganti:

| Aset | File | Kanvas | Catatan |
|---|---|---|---|
| Badge kepala tag | `components/tag_badge/badge_cat.webp` | 128×128 | bentuk dasar ikon tag |
| TopFrame | `components/top_frame/topframe_spritesheet@4x.png` | 536×256 | bingkai atas dialog |
| TagFrame | `components/tag_frame/frame_spritesheet.webp` | 512×240 | bingkai dialog tag |
| Ikon tag (36) | `icons/tag/icon_<bentuk>.webp` | 96×96 | digambar di atas badge |
| Ikon UI (45) | `icons/ui/icon_<bentuk>.webp` | 96×96 | ikon antarmuka |
| Motif dialog | `motifs/motif_<nama>.png` (+ `@512`) | 1024×1024 | pola latar tile |

---

## 2. Siapkan

1. Python 3.9+ dan `pip install pillow numpy` (untuk tools di kit).
2. Editor gambar yang mendukung transparansi dan lapisan: Inkscape, Figma, Krita, Affinity, Photopea, dsb.
3. Buka `templates/` sebagai lapisan acuan: `badge_128.png`, `glyph_96.png`, `topframe_spritesheet@4x.png`, `tag_frame_sheet.png` (garis margin, live area, slot, potongan; 1 kotak grid = 8 px = 2 dp).
4. Lihat `previews/` untuk gambaran hasil skin bawaan.

---

## 3. Pilih cakupan

Kerjakan bertahap; setiap tingkat sudah menghasilkan skin yang bisa dipakai.

| Tingkat | Isi | Kerja |
|---|---|---|
| 1 | badge saja | ±1 gambar. Mengubah tampilan semua ikon tag |
| 2 | + TopFrame + TagFrame | 3 gambar dengan **satu siluet kepala yang sama** |
| 3 | + sebagian/semua ikon tag | 36 glyph |
| 4 | + ikon UI | 45 glyph |
| 5 | + motif dialog | 1 gambar per motif |

Urutan yang disarankan: **badge → TopFrame → TagFrame → glyph → ikon UI → motif**. Tetapkan dulu gaya (tebal garis, sudut membulat/tajam) di badge, lalu pakai di semua yang lain.

---

## 4. Buat kerangka

```bash
python3 -I tools/new_skin.py my_skin --name "My Skin" --author "Nama Kamu"
```

Hasil: folder `my_skin/` berisi `skin.json` valid dan folder kosong. Untuk memulai dari aset bawaan (mengedit, bukan menggambar dari nol):

```bash
python3 -I tools/new_skin.py my_skin --name "My Skin" --copy badge_cat icon_brain ui:icon_gear
```

`--copy` menerima `badge_cat`, `top_frame`, `tag_frame`, dan nama ikon (`icon_<bentuk>`). Beberapa nama ada di ikon tag **dan** ikon UI (cloud, download, folder, home, image, palette, star): awali `tag:` atau `ui:` untuk memilih; tanpa awalan keduanya disalin.

Nama folder menjadi `skin.id` (huruf kecil, angka, `_`, `-`, `.`). Ganti di `skin.json` bila perlu. **id harus unik dan tidak boleh `default`.**

---

## 5. Menggambar tiap aset

### 5.1 Aturan umum untuk semua gambar

- **Satu warna, tanpa gradien/bayangan/tekstur.** Detail = alpha (isi 255, lubang 0). Tepi halus = alpha parsial, bukan warna.
- Ekspor **RGB putih** (`#FFFFFF`) di semua piksel berisi.
- **Ketebalan garis 8–12 px** pada kanvas 96 (2–3 dp). Di bawah 6 px garis hilang pada ukuran 24 dp.
- **Celah terkecil ≥ 4 px** (lubang, jarak antar elemen).
- Format: ikon/badge/TagFrame = **WebP lossless**; TopFrame/motif = **PNG RGBA**.
- Bekerja di ukuran kanvas aslinya (atau kelipatan bulat, lalu perkecil), jangan menggambar di 512 px lalu mengecilkan sembarangan.

### 5.2 Mengubah gambar jadi mask (`to_mask.py`)

Bila gambar sumbermu berwarna atau berlatar putih/hitam, ubah dengan:

```bash
# bentuk gelap di latar terang (atau PNG transparan)
python3 -I tools/to_mask.py brain.png --kind glyph --out my_skin/icons/tag/icon_brain.webp
# bentuk terang di latar gelap
python3 -I tools/to_mask.py brain_light.png --kind glyph --light-ink --out my_skin/icons/tag/icon_brain.webp
# badge
python3 -I tools/to_mask.py head.png --kind badge --out my_skin/components/tag_badge/badge_cat.webp
```

Skrip membuang warna, menghitung alpha, memotong ke isi, men-skala agar muat di live area, dan memusatkannya. Selalu periksa hasilnya; skrip tidak bisa memperbaiki gambar yang garisnya terlalu tipis.

### 5.3 Glyph ikon (96×96)

- Isi harus di dalam **live area 80×80** (margin 8 px di setiap sisi). Pusat optik di (48, 48).
- Bentuk persegi/lingkaran mengisi ±64–72 px; bentuk lebar/tinggi boleh sampai 80 px. Jangan mengisi seluruh 96.
- **Jangan menaruh lingkaran/kotak latar** di dalam glyph UI. Latar adalah urusan komponen.
- **Nama = bentuk gambar, harus sama persis dengan katalog bawaan**: `icon_brain.webp`, `icon_terminal.webp`, … Daftar di `default_skin/icons/tag/icons_index.md` dan `default_skin/icons/ui/ui_index.md`. Nama di luar katalog **diabaikan** launcher.
- Ikon arah (`arrow_*`, `chevron_*`, `undo`, `redo`) ditandai `mirror_in_rtl`; launcher mencerminkannya sendiri pada layout RTL. Jangan menggambar versi cermin.
- Glyph tag digambar di atas badge dengan warna krem/gelap (otomatis); desain agar terbaca di atas badge, bukan di atas latar putih.

### 5.4 Badge (128×128)

- Siluet maksimal 116×120 px, di tengah, **margin ≥ 4 px** ke tepi. Bawaan: x 6–122, y 4–124.
- Satu bentuk solid tanpa lubang (glyph digambar di atasnya).
- **Slot glyph** bawaan: `x=16, y=22, w=96, h=96` di `components/tag_badge/badge.json`. Pusat slot (64, 70) sengaja lebih rendah karena telinga.
- Kalau bentuk badgemu **berbeda** (lingkaran, kotak, kepala tanpa telinga), kamu **wajib** menyertakan `badge.json` dengan `glyph_slot` baru. Aturan: slot harus berada penuh di dalam siluet, margin ≥ 8 px ke tepi siluet. Salin `badge.json` bawaan lalu ubah `glyph_slot`.

### 5.5 TopFrame (536×256 @4x)

Satu sheet berisi empat potongan dengan celah transparan 8 px di antaranya:

```
x:   0        128 136   152 160           400 408         536
     ├─ left_cap ─┤ ├bar┤ ├──── head ────┤ ├─ right_cap ─┤
        128×256       16     240×256            128×256
        (tetap)    (stretch)  (tetap)           (tetap)
```

| Potongan | Aturan |
|---|---|
| `left_cap` | 32 dp tetap. Sudut luar bawaan elips 32×24 dp. Selaraskan dengan radius sudut dialog |
| `bar_tile` | **polos secara horizontal**. Di-stretch (bukan di-tile), jadi motif apa pun akan melar. Band bawaan y 128–176 (tebal 12 dp) |
| `head` | 60 dp tetap, kepala terpusat; **telinga tidak boleh melewati y = 0**. Sisakan celah ≥ 8 px antara kepala dan ujung bar |
| `right_cap` | cermin `left_cap` |

- Garis tengah bar (y = 152) sejajar dengan sisi atas permukaan dialog. Kalau menggeser band, geser **bersama** posisi anchor, jangan sendiri-sendiri.
- Lebar potongan (32/60/32 dp) **tidak boleh diubah**; launcher belum mendukungnya.
- Sheet 1x dan 2x tidak perlu dibuat; cukup `@4x`.
- Perilaku lebar: `bar_each = (W − 124) / 2` dp, `W` minimum 124 dp. Uji di 124 dp dan 360 dp.

### 5.6 TagFrame (512×240)

Tiga potongan vertikal:

| Potongan | y | tinggi | Mode |
|---|---|---|---|
| `top` | 0 | 144 | tetap (kepala + sudut atas) |
| `middle` | 144 | 16 | **di-stretch vertikal** |
| `bottom` | 160 | 80 | tetap (sudut bawah) |

- Baris-baris `middle` **harus identik** (tanpa motif yang berubah ke bawah). Validator memeriksa ini.
- Garis kiri/kanan di `middle` harus sama x-nya dengan di `top` dan `bottom`, agar tidak ada patahan.
- Garis bawaan tebal 19 px (x 18–36), kepala 87×120 px (lebih langsing daripada kepala badge).
- Tinggi minimum 224 px (`144 + 0 + 80`).

### 5.7 Siluet kepala yang konsisten

Kepala bawaan di ketiga komponen berbeda proporsi (badge 0,97 · TopFrame 0,99 · TagFrame 0,73). Skin yang rapi memakai **satu bentuk kepala** di ketiganya, hanya skalanya yang beda. Validator tidak memaksa ini.

### 5.8 Motif dialog

Hasil akhir: **PNG 1024×1024, seamless, objek putih, latar transparan**.

Syarat gambar sumber:

| Hal | Syarat |
|---|---|
| Ukuran | minimal **1152×1152** (1024 + overlap 128); disarankan ≥ 1400 |
| Latar | polos putih atau transparan; tanpa gradien/tekstur kertas/bayangan |
| Objek | satu warna solid atau garis satu warna; tanpa gradien/efek 3D |
| Sebaran | objek menyebar merata, ada celah kosong antar objek (jahitan seamless lewat celah) |
| Ukuran objek | solid ≥ 60–80 px; garis ≥ 3 px (pada kanvas 1024) |

Contoh prompt untuk AI generatif:

```
Seamless repeating pattern of [OBJEK], flat vector style, single solid color on a pure
white background, evenly spaced with gaps between elements, no gradients, no shadows,
no texture, no border, high resolution, top-down view, tileable wallpaper pattern
```

(Tambahkan "line art, uniform line weight" untuk motif garis atau "solid silhouette" untuk motif solid. Hasil AI biasanya tidak benar-benar seamless; skrip yang menjahitnya. Cek lisensi/hak pakai gambar AI atau gambar orang lain sebelum dirilis.)

Jalankan:

```bash
python3 -I tools/make_motif.py sumber.png --name bunga --out my_skin/motifs --type line
```

Hasil: `motif_bunga.png` (1024) dan `motif_bunga@512.png`, plus **snippet JSON** yang tinggal ditempel ke `"motifs"` di `skin.json`.

| Opsi | Fungsi |
|---|---|
| `--x`, `--y` | titik awal crop; geser bila tepi memotong objek penting |
| `--overlap` | zona jahitan (default 128); naikkan ke 192–256 bila sambungan terlihat |
| `--light-ink` | sumber berupa objek terang di latar gelap |
| `--no-seamless` | sumber sudah seamless dan persis 1024×1024 |
| `--type` | `solid` atau `line` (untuk snippet) |

Ukuran tampil dan opacity awal (nilai dp diasumsikan, uji di perangkat):

| Jenis | Tile tampil | Opacity |
|---|---|---|
| paw (solid) | 256–384 dp | 15–25 % |
| flame (garis tebal) | 400–600 dp | 20–30 % |
| sakura (garis tipis) | 560–800 dp | 30–45 % |
| butterfly (garis tipis, padat) | 560–800 dp | 25–40 % |

Motif garis tipis jangan diperkecil terlalu jauh. Bila dialog terasa ramai: perbesar tile, turunkan opacity, atau pilih motif yang lebih jarang. Motif yang aman: siluet sederhana (paw, bintang, hati, daun), garis geometris (gelombang, seigaiha, bintik).

---

## 6. `skin.json`

Edit field `skin` dan, bila ada motif, `motifs`:

```json
{
  "schema_version": 1,
  "skin": {
    "id": "sakura_pink",
    "name": "Sakura Pink",
    "version": "1.0.0",
    "author": "Nama Kamu",
    "description": "Skin musim semi.",
    "min_nekos_version": "0.0.0"
  },
  "assets": [],
  "motifs": [
    {
      "id": "bunga",
      "file": "motifs/motif_bunga.png",
      "file_small": "motifs/motif_bunga@512.png",
      "type": "line",
      "coverage": 0.09,
      "suggested_display_dp": 600,
      "suggested_opacity": 0.3
    }
  ],
  "compatibility": {
    "min_nekos_version": "0.0.0",
    "requires_network": false,
    "contains_executable_code": false
  }
}
```

- **Ikon tidak perlu didaftarkan.** Cukup taruh file di `icons/tag/` atau `icons/ui/` dengan nama yang benar.
- `assets[]` boleh kosong. Isi hanya bila ingin mencatat metadata komponen (contoh: `specs/skin_manifest.example.json`).
- Setiap rilis ulang: **naikkan `version`** (`1.0.0` → `1.0.1` untuk perbaikan, `1.1.0` untuk tambahan, `2.0.0` untuk perubahan besar). Launcher memperlakukan id yang sama sebagai pembaruan.
- Jangan ubah `contains_executable_code` dan `requires_network` dari `false`.

Jika kamu **menambah** ikon yang belum ada di katalog bawaan, ia tidak akan dipakai launcher (nama harus ada di katalog). Permintaan ikon baru → bahas dengan pengembang NekOS.

---

## 7. Validasi dan pratinjau

```bash
# validasi (folder atau zip)
python3 -I tools/validate_skin.py my_skin

# pratinjau: aset yang tidak ada diambil dari bawaan
python3 -I tools/preview_skin.py my_skin --out previews/my_skin
```

Pratinjau menulis `*_tags.png` (semua ikon tag di empat warna contoh: Purple, Ocean, Silver, Dark), `*_ui.png` (ikon UI di latar terang dan gelap), `*_frames.png` (TopFrame 360 dan 124 dp, TagFrame tiga tinggi), `*_motifs.png` (tile 2×2).

Yang harus kamu lihat sendiri (validator tidak bisa menilainya):

- [ ] Glyph terbaca di atas **semua** warna, terutama yang terang (Silver) dan gelap (Dark). Launcher memilih krem/gelap otomatis (bila fitur itu sudah ada), tapi bentuk yang terlalu tipis tetap hilang.
- [ ] Lihat pada **ukuran aslinya** (24 dp = 24 px di layar 1x; 32 dp untuk badge), bukan hanya diperbesar.
- [ ] TopFrame di 124 dp: kepala tidak tumpang tindih dengan cap.
- [ ] TagFrame: tidak ada patahan di garis pada tinggi besar.
- [ ] Motif: tanpa sambungan di garis tengah tile 2×2, tidak ada halo di latar gelap maupun terang, tidak berkesan "kotak-kotak".
- [ ] Gaya konsisten di seluruh ikon (tebal garis, sudut, ukuran optik).

---

## 8. Membaca pesan validator

| Pesan | Artinya | Perbaikan |
|---|---|---|
| `bukan mask putih` | ada piksel berisi yang RGB-nya bukan putih | ekspor ulang, atau jalankan `to_mask.py` |
| `ukuran (W, H), harus 96x96` | kanvas salah | ubah kanvas ke ukuran di tabel |
| `isi (...) keluar dari live area` | glyph menyentuh margin 8 px | kecilkan/pusatkan glyph |
| `nama harus icon_<bentuk>.webp` | nama tidak sesuai pola | huruf kecil, angka, underscore |
| `nama numerik dilarang` | mis. `icon_001.webp` | beri nama bentuk |
| `tidak ada di katalog bawaan -> akan DIABAIKAN` | nama ikon tidak dikenal launcher | samakan dengan katalog |
| `slice middle ... harus identik` | `middle` TagFrame punya detail yang berubah ke bawah | ratakan baris 144–160 |
| `bar_tile tidak polos horizontal` | bar TopFrame bermotif; akan melar | buat bar polos |
| `celah transparan ... harus kosong` | ada piksel di celah antar potongan TopFrame | bersihkan celah 8 px |
| `siluet menyentuh tepi` | badge kurang margin | kecilkan siluet ≥ 4 px dari tepi |
| `pusat glyph_slot jatuh di area transparan` | `badge.json` tidak cocok dengan bentuk badge | geser `glyph_slot` |
| `tepi tidak menyambung` (motif) | tidak seamless | naikkan `--overlap`, geser `--x/--y` |
| `file ... terdaftar tetapi tidak ada` | `skin.json` menyebut berkas yang tidak ada | koreksi path atau hapus entrinya |
| `skin.json tidak ada di root` | zip dibuat dari folder pembungkus | kemas ISI folder (pakai `pack_skin.py`) |
| `path tidak aman` / `ekstensi ... tidak diizinkan` / `symlink dilarang` | pelanggaran aturan keamanan zip | buang berkas itu |
| `melebihi batas` (ukuran, jumlah, piksel) | skin terlalu besar | kecilkan; ikon seharusnya ±1 KB |

Peringatan (`WARN`) tidak menghalangi kemas, tetapi baca setiap barisnya.

Masalah motif umum:

| Gejala | Penyebab | Solusi |
|---|---|---|
| Kabut abu-abu di seluruh motif | latar sumber tidak putih bersih | naikkan kontras sumber atau hapus latar dulu |
| Garis terputus | sumber JPG terkompresi / garis terlalu tipis | pakai PNG lebih besar, garis lebih tebal |
| Hasil kosong atau terlalu pekat | objek terang di latar gelap | tambahkan `--light-ink` |
| Banyak bercak kecil | noise sumber AI | bersihkan sumber, atau naikkan ambang `0.02` di `extract_alpha` |

---

## 9. Kemas

```bash
python3 -I tools/pack_skin.py my_skin --out dist
# -> dist/sakura_pink-1.0.0.zip
```

`pack_skin.py` memvalidasi dulu (error = tidak membuat zip), lalu membuat zip dengan isi folder di **root** (bukan di dalam folder pembungkus), tanpa berkas tersembunyi (`.DS_Store` dll.), lalu memvalidasi ulang zip hasilnya.

Jangan mengemas manual dengan klik-kanan "Compress folder": itu biasanya membuat folder pembungkus dan `skin.json` tidak ada di root. Bila terpaksa manual, zip **isi** folder, lalu cek:

```bash
python3 -I tools/validate_skin.py dist/sakura_pink-1.0.0.zip
```

Batas paket (usulan): ≤ 400 file, ≤ 8 MB per file, ≤ 32 MB total, gambar ≤ 4096 px per sisi, hanya `.webp .png .json .md .txt`. Skin lengkap bawaan ±3 MB (±0,15 MB tanpa motif).

---

## 10. Mencoba di NekOS

Setelah Fase 3 launcher selesai: **Launcher theme → Skin → Impor** → pilih `.zip` → aktifkan. Bila ditolak, pesan alasannya sama dengan kategori di §8. Lalu ulangi daftar uji di §7 di perangkat sungguhan dengan ke-12 warna Shared UI Color, mode terang/gelap, dan dialog kecil.

---

## 11. Rilis

- Cantumkan `author`, `description`; tambahkan `LICENSE.txt` di zip bila ada (boleh `.txt`/`.md`).
- Pastikan kamu berhak memakai semua gambar. Jangan menyertakan karakter/logo berhak cipta milik pihak lain.
- Simpan sumber editable (SVG/PSD/KRA) di luar zip.
- Tes ulang dari zip hasil kemas, bukan dari folder kerja.
