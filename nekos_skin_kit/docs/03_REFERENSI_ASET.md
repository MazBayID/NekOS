# 03 — Referensi Aset & Format Skin NekOS

Kontrak teknis aset dan paket skin. Dipakai bersama oleh pembuat skin (`02_PANDUAN_MEMBUAT_SKIN.md`) dan pengembang launcher (`01_PANDUAN_IMPLEMENTASI_LAUNCHER.md`). Semua angka diukur dari aset bawaan di `default_skin/`. Semua path di dokumen ini relatif terhadap **root skin** (root zip).

Cek aset kamu dengan:

```bash
python3 -I tools/validate_skin.py <folder_skin | skin.zip>
```

---

## 1. Prinsip dasar

1. **Semua aset bawaan adalah mask putih + alpha.** Piksel berisi harus `RGB = 255,255,255`; bentuknya hanya ada di kanal alpha. Warna diberikan saat runtime (tint `SRC_IN`). Jangan memanggang warna ke gambar.
2. **Satu master per aset, resolusi 4x (xxxhdpi): 1 dp = 4 px.** Densitas lain diturunkan dengan downscale. Jangan membuat 512 px untuk ikon yang tampil 24 dp.
3. **Ukuran kanvas dan posisi slot tidak boleh berubah.** Skin boleh mengganti isi gambar, bukan geometri kontrak (lihat tabel §2).
4. **Skin hanya boleh mengganti tampilan.** Tidak boleh mengubah perilaku, navigasi, izin, atau aksesibilitas. Skin tidak berisi kode.
5. **Aset yang tidak disediakan skin memakai aset bawaan** (fallback per file). Skin boleh parsial.

### Peran warna (tint role)

| Role | Dipakai oleh | Diisi dengan | Default |
|---|---|---|---|
| `shared_ui` | `badge_cat`, `top_frame`, `tag_frame` | pilihan **Shared UI Color** (Ocean, Blue, Purple, …) | Purple |
| `on_badge` | glyph ikon **tag** di atas badge | warna kontras di atas `shared_ui` | krem `#F5F2ED` |
| `foreground` | semua ikon **UI** | warna teks/ikon tema | dari palette (Porcelain, Sand, …) |

Pada pilihan **Icon Color** di dialog *Create tag*, yang berubah adalah tint badge (`shared_ui`/warna tag); glyph tetap `on_badge`.

> **Temuan kontras:** krem `#F5F2ED` di atas badge terang hampir tak terbaca. Dengan warna contoh Silver `#C9CED6` rasionya hanya **1,42 : 1** (WCAG minimum untuk ikon = 3 : 1); di atas Purple 5,41 : 1 dan Dark 13,59 : 1. Bukan kesalahan aset, tetapi keputusan runtime: pilih warna glyph otomatis (krem atau gelap) berdasarkan kontras terhadap warna badge. Lihat panduan implementasi §3.5.

Contoh render (Compose):

```kotlin
Image(bitmap, contentDescription = null, colorFilter = ColorFilter.tint(color, BlendMode.SrcIn))
```

---

## 2. Tabel kontrak aset

| Aset | File | Kanvas (px) | Tampil (dp) | Live area / isi | Tint |
|---|---|---|---|---|---|
| Glyph ikon tag | `icons/tag/icon_<bentuk>.webp` | 96 × 96 | 24 × 24 (di slot badge) | 80 × 80 di tengah (margin 8 px) | `on_badge` |
| Glyph ikon UI | `icons/ui/icon_<bentuk>.webp` | 96 × 96 | 24 × 24 | 80 × 80 di tengah (margin 8 px) | `foreground` |
| Badge tag | `components/tag_badge/badge_cat.webp` | 128 × 128 | 32 × 32 | siluet maks. 120 × 120 (margin ≥ 4 px) | `shared_ui` |
| TopFrame | `components/top_frame/topframe_spritesheet@4x.png` + `topframe_atlas.json` | 536 × 256 | tinggi 64, lebar mengikuti dialog (min 124) | lihat §5 | `shared_ui` |
| TagFrame | `components/tag_frame/frame_spritesheet.webp` + `.json` | 512 × 240 | lihat §6 | lihat §6 | `shared_ui` |
| Motif dialog | `motifs/motif_<nama>.png` (+ `@512`) | 1024 × 1024 (+ 512 × 512) | tile berulang, lihat §8 | seamless, seluruh kanvas | `shared_ui` (usulan, lihat §12) |

Format file: ikon & badge & TagFrame = **WebP lossless** (RGBA); sprite TopFrame & motif = PNG RGBA. Target ukuran ikon: < 20 KB (bawaan rata-rata ±1 KB).

---

## 3. Glyph (ikon tag dan UI)

```
 96 px
┌────────────────────────┐
│  8 px margin           │
│   ┌────────────────┐   │
│   │  live area 80  │   │   isi glyph harus di dalam kotak ini
│   │                │   │   pusat optik di tengah kanvas (48,48)
│   └────────────────┘   │
└────────────────────────┘
```

- **Satu warna, tanpa gradien/bayangan/tekstur.** Detail dibuat dari alpha: area terisi = alpha 255, lubang = alpha 0. Anti-aliasing = alpha parsial, bukan warna.
- **Ketebalan garis 8–12 px** (2–3 dp). Bawaan memakai ±10–12 px. Garis lebih tipis dari 6 px hilang pada ukuran 24 dp.
- **Ukuran optik:** glyph persegi/lingkaran mengisi ±64–72 px; glyph lebar/tinggi boleh sampai 80 px. Jangan mengisi seluruh 96 px.
- **Tidak ada lingkaran/kotak latar di dalam glyph UI** (latar adalah urusan komponen). Set lama memanggang lingkaran teal; set baru tidak.
- **Ikon UI arah** (`arrow_*`, `chevron_*`, `undo`, `redo`) ditandai `mirror_in_rtl` di index; runtime mencerminkannya pada layout RTL.
- **Celah terkecil** (lubang, jarak antar elemen) ≥ 4 px agar tidak menutup saat diperkecil.

### Penamaan

`icon_<bentuk>.webp`, huruf kecil, angka, underscore: regex `^icon_[a-z0-9]+(_[a-z0-9]+)*$`. Nama = **bentuk gambar** (`icon_brain`, `icon_terminal`, `icon_network`, `icon_cloud`), bukan nomor, bukan fungsi. Nomor urut hanya muncul di tabel index.

### Stabilitas identitas ID

Setiap ID numerik harus selalu menunjuk nama glyph yang sama seperti katalog bawaan. Skin boleh mengganti artwork/file mask, tetapi tidak boleh memakai ulang ID untuk bentuk lain. Index parsial diperbolehkan; setiap entri yang dicantumkan tetap harus memakai pasangan ID → nama kanonis dan file `icon_<nama>.webp` yang sesuai.

### Katalog wajib

Skin yang mengganti sebuah ikon harus memakai **nama yang sama persis** dengan ikon bawaan (lihat `default_skin/icons/tag/icons_index.md` dan `default_skin/icons/ui/ui_index.md`). Nama tidak ada di katalog = diabaikan. Skin boleh menyediakan sebagian ikon.

Setiap folder ikon memiliki index (`icons_index.json` / `ui_index.json`) + `.md`. Jika skin menambah/mengganti ikon, index harus ikut diperbarui (validator memeriksa konsistensinya).

---

## 4. Badge tag (`badge_cat.webp`)

- Kanvas **128 × 128**, siluet bawaan 116 × 120 px di tengah (x 6–122, y 4–124). Margin ke tepi **≥ 4 px**.
- Satu bentuk solid (alpha), tanpa lubang, karena glyph digambar di atasnya.
- **Slot glyph** (di `badge.json`): `x=16, y=22, w=96, h=96` — glyph 96 px digambar 1:1 di sini. Pusat slot (64, 70) sengaja lebih rendah daripada pusat kanvas karena telinga memakan bagian atas kepala.
- Skin yang mengganti bentuk badge (lingkaran, kotak, …) **wajib** menyertakan `badge.json` dengan `glyph_slot` baru supaya glyph tetap di tengah area isi badge. Aturan: slot harus berada penuh di dalam siluet, dengan margin ≥ 8 px ke tepi siluet.

---

## 5. TopFrame

Spritesheet 4x berukuran **536 × 256**, tersusun dari empat potongan dengan celah transparan 8 px (2 px @1x):

| Potongan | x | w × h (px) | Mode | Isi yang terukur |
|---|---|---|---|---|
| `left_cap` | 0 | 128 × 256 | fixed (32 dp) | isi y 128–224; sudut luar elips 128 × 96 px (32 × 24 dp) |
| `bar_tile` | 136 | 16 × 256 | stretch horizontal (dipakai 2×) | isi y 128–176 → bar setebal 48 px (12 dp) |
| `head` | 160 | 240 × 256 | fixed (60 dp) | isi x 10–230, y 14–236 (220 × 222 px ≈ 55 × 55,5 dp), terpusat horizontal |
| `right_cap` | 408 | 128 × 256 | fixed (32 dp) | cermin dari `left_cap` |

Rumus lebar: `bar_each = (W − 2·32 − 60) / 2` dp, `W ≥ 124 dp`. Susunan: `left_cap · bar · head · bar · right_cap`. Bar di-stretch (`FillBounds`), **tidak di-tile**, jadi `bar_tile` harus polos secara horizontal (tidak boleh bermotif).

Aturan skin:

1. Pertahankan koordinat atlas. Jika skin butuh ukuran potongan lain, `topframe_atlas.json` skin harus menyatakannya dan runtime harus mendukungnya (saat ini **tidak**: lebar 32/60/32 dp tetap).
2. Garis tengah bar (y = 152 px @4x = 38 dp) harus sejajar dengan sisi atas Surface dialog (anchor geometris). `bar_tile` berisi band y 128–176; pindahkan band bersama anchor, jangan sendiri-sendiri.
3. Kepala harus terpusat di frame `head` dan telinga tidak melewati y = 0.
4. **Ujung dalam bar dan kepala:** sisakan celah ≥ 8 px (bawaan 10 px) antara tepi bar dan isi kepala.
5. Sudut luar cap bawaan elips 32 × 24 dp. Jika dialog bawaan memakai sudut melingkar (mis. 28 dp), selaraskan radius cap skin dengan radius dialog.
6. Offset visual runtime −6 dp (Alert Dialog, Launcher Settings) sudah ditentukan di `topframe_formula.json` dan tidak ikut skin.

Sheet 1x dan 2x tidak perlu dibuat tangan: turunkan dari 4x (÷4, ÷2; koordinat atlas sudah dihitung).

---

## 6. TagFrame (3-slice vertikal)

Sheet **512 × 240**, tersusun vertikal:

| Potongan | y | h (px) | Mode |
|---|---|---|---|
| `top` | 0 | 144 | fixed — kepala + sudut atas, jangan di-scale |
| `middle` | 144 | 16 | `stretch_vertical` — sisi lurus, tinggi bebas |
| `bottom` | 160 | 80 | fixed — sudut bawah |

Terukur: tebal garis 19 px (x 18–36), margin luar kiri 18 / atas 15 / bawah 18 px, kepala 87 × 120 px terpusat horizontal (x ≈ 214–300). Tinggi total `H = 144 + h_middle + 80`, minimal 224. Content inset: kiri/kanan 35, atas 144, bawah 80.

Aturan skin: `middle` harus identik di seluruh barisnya (tanpa motif vertikal yang berubah), karena hanya potongan ini yang ditarik. Garis kiri/kanan di `middle` harus sama x-nya dengan di `top` dan `bottom` agar tidak ada patahan.

> **Belum terverifikasi:** skala tampil TagFrame (dp per px) tidak tercatat di spec lama (drawable `nodpi`). Hingga diukur dari runtime, asumsikan konvensi yang sama (4 px = 1 dp, sheet 128 dp lebar). Isi `display_dp` di manifest skin setelah diverifikasi.

---

## 7. Desain konsisten antar komponen

Ketiga kepala kucing bawaan **berbeda proporsi**: badge 116 × 120 (rasio 0,97), TopFrame 220 × 222 (0,99), TagFrame 87 × 120 (0,73, lebih langsing). Skin sebaiknya memakai **satu siluet kepala yang sama** di ketiganya (skala berbeda, bentuk sama). Ini tidak dipaksa validator, tetapi membuat skin terlihat utuh.

---

---

## 8. Motif dialog (`motifs/`)

Tile seamless untuk latar dialog. Ditumpuk: warna latar → **motif (tint + opacity rendah)** → TopFrame → konten.

| Properti | Nilai |
|---|---|
| File | `motifs/motif_<nama>.png` (1024 × 1024) dan `motif_<nama>@512.png` (512 × 512, perangkat ringan) |
| Nama | `^motif_[a-z0-9]+(_[a-z0-9]+)*(@512)?\.png$` |
| Format | PNG RGBA, **RGB = 255 di semua piksel berisi**, bentuk di alpha |
| Seamless | harus bisa diulang kiri/kanan/atas/bawah tanpa sambungan (uji tile 2×2) |
| Coverage (alpha rata-rata) | motif solid 15–30 %, motif garis 5–15 % (flame bawaan 23,8 %: garis tebal) |
| Garis | ≥ 3 px pada kanvas 1024; detail < 2 px hilang saat diperkecil |
| Warna akhir | `tint.rgb`, alpha = `motif.a × opacity` (opacity umumnya 0,15–0,45) |

Motif **tidak** mengubah perilaku dialog; wajib di-clip mengikuti bentuk dialog. Terdaftar di `skin.json` → `motifs[]`:

| Motif bawaan | type | coverage | tile tampil (dp, usulan) | opacity |
|---|---|---|---|---|
| paw | solid | 0,226 | 320 | 0,18 |
| sakura | line | 0,071 | 640 | 0,35 |
| butterfly | line | 0,137 | 640 | 0,30 |
| flame | line | 0,238 | 512 | 0,25 |

> **Asumsi satuan:** paket lama menulis `suggested_display_px` dan contoh Kotlin-nya mengalikan dengan density, jadi diperlakukan sebagai **dp** dan kuncinya diganti `suggested_display_dp`. Bila yang dimaksud ternyata px fisik, ubah angkanya. Verifikasi di perangkat (§12).

---

## 9. `skin.json`

Manifest deklaratif. **Tidak pernah dieksekusi**; hanya dibaca sebagai data. Skema: `specs/skin_manifest.schema.json` (JSON Schema 2020-12). Contoh parsial: `specs/skin_manifest.example.json`; contoh lengkap: `default_skin/skin.json`.

| Field | Wajib | Keterangan |
|---|---|---|
| `schema_version` | ya | selalu `1` |
| `skin.id` | ya | `^[a-z0-9][a-z0-9._-]*$`; identitas unik. Skin dengan id sama = pembaruan |
| `skin.name` | ya | nama tampil |
| `skin.version` | ya | semver `MAJOR.MINOR.PATCH` |
| `skin.author`, `description`, `license` | tidak | informasi |
| `skin.min_nekos_version` | tidak | launcher menolak bila versinya lebih rendah |
| `assets[]` | tidak | metadata komponen/override. **Ikon ditemukan lewat konvensi folder + nama**, tidak perlu didaftarkan |
| `motifs[]` | tidak | motif yang disediakan (lihat §8) |
| `compatibility.contains_executable_code` | tidak | harus `false` bila ada |
| `compatibility.requires_network` | tidak | harus `false` bila ada |

Aturan pembacaan: field tak dikenal **diabaikan** (kompatibel ke depan). `file` tidak boleh absolut, mengandung `..` atau `\`, dan harus ada di paket. Tidak ada field untuk warna/teks/layout di v1: skin v1 = **aset gambar saja**. `specs/design_tokens.template.json` adalah placeholder untuk versi berikutnya dan **belum dipakai**.

---

## 10. Paket skin `.zip`

Skin dibagikan sebagai satu file `.zip`. Nama file bebas; saran: `<id>-<versi>.zip` (dibuat otomatis oleh `tools/pack_skin.py`).

```
skin.json                 <- WAJIB, di root zip (bukan di dalam folder pembungkus)
icons/tag/icon_<bentuk>.webp      (+ icons_index.json/.md bila ikon ditambah)
icons/ui/icon_<bentuk>.webp       (+ ui_index.json/.md)
components/tag_badge/{badge_cat.webp, badge.json}
components/top_frame/{topframe_spritesheet@4x.png, topframe_atlas.json}
components/tag_frame/{frame_spritesheet.webp, frame_spritesheet.json}
motifs/motif_<nama>.png, motif_<nama>@512.png
README.md, LICENSE.txt    <- opsional
```

Semua bagian opsional kecuali `skin.json`. Aset yang tidak ada → memakai aset bawaan **per file**.

### Batas & aturan keamanan (usulan; samakan dengan konstanta di kode launcher)

| Aturan | Nilai |
|---|---|
| Jumlah file | ≤ 400 |
| Ukuran per file (tidak terkompresi) | ≤ 8 MB |
| Total tidak terkompresi | ≤ 32 MB |
| Sisi gambar maksimum | ≤ 4096 px (bom dekompresi gambar) |
| Ekstensi diizinkan | `.webp .png .json .md .txt` |
| Path | tanpa `/` di awal, tanpa `..`, tanpa `\`, tanpa drive (`C:`), tanpa nama ganda (case-insensitive) |
| Symlink | dilarang |
| Kode | tidak ada (`.dex .so .jar .js .sh .apk` ditolak karena ekstensi) |
| Jaringan | skin tidak boleh meminta akses jaringan |

Dasar pemikiran: zip dari pihak ketiga adalah **input tak tepercaya**. Yang paling berbahaya: zip-slip (menulis di luar folder tujuan), zip bomb, dan gambar yang di-decode menjadi ratusan MB. Batas dihitung dari **byte yang benar-benar terbaca**, bukan dari header zip (header bisa berbohong). Folder `tests/fixtures/` berisi zip uji untuk setiap kasus.

Pesan error validator dan arti-nya: lihat `02_PANDUAN_MEMBUAT_SKIN.md` §8.

---

## 11. Daftar periksa sebelum menyerahkan skin

- [ ] `validate_skin.py` tanpa ERROR (WARN dibaca satu per satu).
- [ ] Semua file mask putih; tidak ada warna di dalam gambar.
- [ ] Ukuran kanvas sesuai tabel §2; isi di dalam live area.
- [ ] Nama file persis sama dengan katalog bawaan; index diperbarui bila ada penambahan.
- [ ] Dites ke-12 warna Shared UI Color, termasuk **Silver** dan **Dark**.
- [ ] Dites pada ukuran asli (24 dp / 32 dp), bukan hanya diperbesar.
- [ ] TopFrame dites pada lebar 124 dp (minimum) dan 360 dp (maksimum dialog).
- [ ] TagFrame dites pada tinggi minimum (224 px) dan tinggi besar; tidak ada patahan di sambungan slice.
- [ ] Motif dites tile 2×2 (tanpa sambungan), pada latar gelap dan terang, pada opacity yang disarankan.
- [ ] Satu siluet kepala yang sama dipakai di badge, TopFrame, dan TagFrame (§7).
- [ ] `skin.version` dinaikkan bila ini pembaruan dari rilis sebelumnya.

---

## 12. Belum terverifikasi / keputusan terbuka

Hal-hal ini tidak dapat dipastikan dari isi kit (kode NekOS tidak ada di zip). Jangan dianggap fakta.

| # | Hal | Status | Siapa memutuskan |
|---|---|---|---|
| 1 | Skala tampil **TagFrame** (dp per px) dan apakah lebarnya tetap 128 dp | asumsi 4 px = 1 dp, belum diukur | pengembang, ukur dari `HomeComponents.kt` |
| 2 | **Tint role motif** | usulan `shared_ui`; bisa juga warna teks/permukaan | pemilik produk |
| 3 | **Satuan** ukuran tile motif (dp vs px) | diasumsikan dp | pemilik produk, cek di perangkat |
| 4 | Warna glyph `on_badge` | tetap krem ⇒ gagal kontras di warna terang (Silver 1,42 : 1) | disarankan otomatis berdasarkan kontras |
| 5 | Ikon tag pengganti untuk 60 ikon lama yang dihapus tanpa padanan | usulan `icon_star` (`migration/legacy_icon_map.json`) | pemilik produk |
| 6 | Batas ukuran paket zip (§10) | usulan | pengembang |
| 7 | Hubungan file sumber `frame_spritesheet.webp` dengan PNG runtime `tag_frame_spritesheet.png` | **belum terbukti setara secara visual** | bandingkan sebelum mengganti |
| 8 | Sheet TopFrame 1x/2x: diturunkan runtime dari 4x atau tetap dikirim | skin hanya wajib 4x; bawaan tetap menyertakan ketiganya | pengembang |
| 9 | `min_nekos_version` | `0.0.0` (placeholder) sampai ada versi nyata | pengembang |
