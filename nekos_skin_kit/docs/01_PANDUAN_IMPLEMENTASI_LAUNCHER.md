# 01 — Panduan Implementasi Skin di Launcher NekOS

Untuk pengembang NekOS. Tujuan: mengubah launcher dari **aset lama yang dipanggang** menjadi launcher **reskinable**, yang memuat skin dari file `.zip`.

> **Status & batas dokumen ini**
> - Kode NekOS **tidak ada** di kit. Nama kelas/path (`IconCatalogRepository`, `HomeComponents.kt`, `tag_frame_spec.json`, `res/drawable-nodpi/…`) diambil dari catatan di dokumen lama, **belum dicocokkan dengan kode**. Cocokkan dulu sebelum mengubah.
> - Semua kode Kotlin di bawah adalah **draf arah**, belum dikompilasi atau dijalankan. Perlakukan sebagai titik awal.
> - Kontrak aset: `03_REFERENSI_ASET.md`. Bagian yang belum terverifikasi: §12 di dokumen itu dan §6 di bawah.

---

## 1. Target arsitektur

```
 ┌────────────────────┐      ┌──────────────────────────┐
 │ assets/skins/default/ │      │ filesDir/skins/<id>/ …   │   <- hasil instal dari .zip
 │ (dibundel di APK)     │      │ (skin pengguna)          │
 └─────────┬──────────┘      └────────────┬─────────────┘
           │  AssetSource                  │  AssetSource
           └──────────────┬───────────────┘
                          ▼
                 SkinPack (manifest + source)
                          ▼
   SkinRepository ──► activeSkin: StateFlow<SkinPack>   (DataStore: active_skin_id)
                          ▼
            SkinAssetResolver  (skin aktif → fallback default, per file, + cache)
                          ▼
   Composable: TagIcon · UiIcon · TopFrame · TagFrame · MotifBackground
```

Prinsip (harus dijaga di setiap fase):

1. **Satu jalur kode.** Skin bawaan dimuat lewat mekanisme yang sama dengan skin pengguna. Tidak ada `if (default)` di komposabel.
2. **Fallback per file.** Aset hilang atau rusak di skin → pakai aset bawaan. Skin yang rusak tidak boleh membuat launcher crash atau kosong.
3. **Skin hanya data gambar.** Tidak ada kode, tidak ada perubahan perilaku, gestur, navigasi, izin, penyimpanan, atau semantik aksesibilitas.
4. **Warna dari runtime.** Semua aset adalah mask putih; warna datang dari tema (`shared_ui`, `on_badge`, `foreground`).
5. **Input zip tidak tepercaya.** Validasi sebelum menyentuh storage permanen (§3.3).

Kenapa aset di `assets/` dan bukan `res/`: nama seperti `topframe_spritesheet@4x.png` tidak valid sebagai resource Android (`@` dan huruf besar dilarang), dan resolver butuh membuka file berdasarkan **nama dari katalog**, bukan ID resource.

---

## 2. Rencana kerja bertahap

Tiap fase bisa dirilis sendiri dan tidak merusak fase sebelumnya. **Mulai dari Fase 0–1**: itu sesuai targetmu (aset yang tepat dulu), dan tidak butuh fitur skin sama sekali.

### Fase 0 — Siapkan aset (tanpa mengubah perilaku)

- [ ] Salin `default_skin/` ke `app/src/main/assets/skins/default/` (isinya: `skin.json`, `icons/`, `components/`, `motifs/`).
- [ ] Jalankan `python3 -I tools/validate_skin.py default_skin --strict` → harus `0 error, 0 warning`.
- [ ] Salin `migration/legacy_icon_map.json` ke `app/src/main/assets/` (dipakai Fase 1).
- [ ] **Jangan** hapus aset lama dulu. Hapus setelah Fase 1 lulus uji.
- [ ] Pastikan `minSdk ≥ 18` (decode WebP lossless dengan alpha).

### Fase 1 — Tukar aset lama → baru (masih tanpa fitur skin)

Hasil: tampilan setara (bentuk sama; warna kini dari tint), ukuran aset ikon + komponen ±25 MB → ±0,15 MB (motif opsional menambah ±2,9 MB).

| Pekerjaan | Detail |
|---|---|
| Ikon **tag** | Gambar = `badge_cat` (tint warna tag/Shared UI) + glyph di `glyph_slot` (tint `on_badge`). Path baru `assets/skins/default/icons/tag/icon_<bentuk>.webp`. Lihat §3.6 |
| Ikon **UI** | Tint `foreground` (warna teks/ikon tema). Tidak ada lingkaran latar di dalam glyph; kalau komponen lama mengandalkannya, gambar latar di komponen |
| **TopFrame** | Sprite sekarang mask putih → tint `shared_ui`. Koordinat atlas **tidak berubah** (32/60/32 dp, tinggi 64 dp) |
| **TagFrame** | `frame_spritesheet.webp` mask putih → tint `shared_ui`. Geometri tidak berubah. **Jangan menimpa PNG runtime lama secara otomatis** (§6 #7) |
| **Migrasi data** | Tag yang tersimpan dengan nama lama (`icon_011` dst.) dipetakan lewat `legacy_icon_map.json` (§4) |
| **Ikon arah** | `arrow_*`, `chevron_*`, `undo`, `redo` bertanda `mirror_in_rtl` di `ui_index.json` → cerminkan di layout RTL |
| **Hapus aset lama** | `assets/ikon/` lama, drawable lama yang tak terpakai |

Kriteria lulus Fase 1: screenshot sebelum/sesudah per layar utama sama dalam bentuk; semua ikon tag tampil; tidak ada referensi ke nama lama (`grep -r "icon_0"`); tag lama tetap tampil ikon yang benar.

### Fase 2 — Abstraksi `SkinPack` + resolver (masih hanya skin bawaan)

Perkenalkan `SkinPack`, `AssetSource`, `SkinAssetResolver`, `LocalSkin` (§3.2–3.4). Semua komposabel mengambil gambar dari resolver, bukan dari path literal. Skin aktif selalu `default`. Tidak ada perubahan yang terlihat pengguna; ini fase refaktor.

### Fase 3 — Instal zip + pilih skin

`SkinInstaller` (§3.3), penyimpanan `filesDir/skins/<id>/`, `SkinRepository`, layar **Launcher theme → Skin**: daftar skin, impor `.zip` (picker SAF, tanpa izin storage), aktifkan, hapus. Skin aktif dihapus/rusak → kembali ke `default`.

### Fase 4 — Komponen yang benar-benar skinnable

- Slot glyph dibaca dari `badge.json` skin (bukan konstanta `16,22,96,96`).
- TopFrame membaca potongan dari atlas skin; batasan runtime saat ini tetap: lebar 32/60/32 dp (skin tidak boleh mengubahnya, §5 di spec).
- Latar motif dialog (§3.9).
- Fallback `on_badge` otomatis (§3.5).

### Fase 5 — Pengerasan & rilis

Uji fixture zip (§5), uji perangkat RAM kecil, pengukuran waktu muat skin, tinjauan keamanan installer, dokumentasi untuk pembuat skin (`02_PANDUAN_MEMBUAT_SKIN.md`).

---

## 3. Detail teknis

### 3.1 Model manifest

```kotlin
@Serializable
data class SkinManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    val skin: SkinInfo,
    val motifs: List<MotifEntry> = emptyList(),
    val compatibility: Compatibility = Compatibility(),
)

@Serializable
data class SkinInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    @SerialName("min_nekos_version") val minNekosVersion: String = "0.0.0",
)

@Serializable
data class MotifEntry(
    val id: String,
    val file: String,
    @SerialName("file_small") val fileSmall: String? = null,
    val type: String,                                   // "solid" | "line"
    val coverage: Float = 0f,
    @SerialName("suggested_display_dp") val suggestedDisplayDp: Float = 320f,
    @SerialName("suggested_opacity") val suggestedOpacity: Float = 0.25f,
)

@Serializable
data class Compatibility(
    @SerialName("contains_executable_code") val containsExecutableCode: Boolean = false,
    @SerialName("requires_network") val requiresNetwork: Boolean = false,
)

val SkinJson = Json { ignoreUnknownKeys = true }       // field tak dikenal diabaikan
```

Daftar `assets[]` di `skin.json` **tidak perlu di-parse** pada v1: ikon ditemukan lewat konvensi nama, komponen lewat path tetap.

### 3.2 `SkinPack` dan sumber aset

```kotlin
interface AssetSource {
    fun open(path: String): InputStream?          // null bila tidak ada
}

class BundledSource(private val am: AssetManager, private val base: String) : AssetSource {
    override fun open(path: String) = runCatching { am.open("$base/$path") }.getOrNull()
}

class DirSource(private val root: File) : AssetSource {
    override fun open(path: String): InputStream? {
        val f = File(root, path).canonicalFile
        if (!f.path.startsWith(root.canonicalPath + File.separator)) return null   // jaga-jaga
        return if (f.isFile) f.inputStream() else null
    }
}

class SkinPack(val manifest: SkinManifest, val source: AssetSource)
```

Tata letak penyimpanan:

```
filesDir/skins/
├── <id>/                 skin terpasang (hasil ekstrak, sudah tervalidasi)
│   ├── skin.json
│   └── …
└── .staging-<uuid>/      sementara saat instal; dibersihkan saat start & di finally
```

### 3.3 Installer aman

Aturan baku ada di `03_REFERENSI_ASET.md` §10. Poin yang sering terlewat: path dicek **kanonik**, byte dihitung dari **data yang dibaca**, gambar dicek **dimensi** sebelum di-decode, dan semuanya ditulis ke **staging** lalu dipindah atomik.

```kotlin
object SkinLimits {
    const val MAX_FILES = 400
    const val MAX_FILE_BYTES = 8L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
    const val MAX_IMAGE_SIDE = 4096
    val ALLOWED_EXT = setOf("webp", "png", "json", "md", "txt")
    val ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]*$")
}

sealed interface InstallResult {
    data class Ok(val manifest: SkinManifest) : InstallResult
    data class Rejected(val reason: String) : InstallResult
}

class SkinInstaller(private val ctx: Context, private val skinsDir: File) {

    suspend fun install(uri: Uri): InstallResult = withContext(Dispatchers.IO) {
        val staging = File(skinsDir, ".staging-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val root = staging.canonicalFile
            var files = 0
            var total = 0L
            val input = ctx.contentResolver.openInputStream(uri)
                ?: return@withContext InstallResult.Rejected("File tidak bisa dibuka")

            ZipInputStream(BufferedInputStream(input)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    val parts = name.split('/')
                    if (name.startsWith("/") || name.contains('\\') || ".." in parts ||
                        parts.dropLast(1).any { it.isEmpty() } || Regex("^[A-Za-z]:").containsMatchIn(name))
                        return@withContext InstallResult.Rejected("Path tidak aman: $name")
                    if (name.substringAfterLast('.', "").lowercase() !in SkinLimits.ALLOWED_EXT)
                        return@withContext InstallResult.Rejected("Jenis file tidak diizinkan: $name")
                    if (++files > SkinLimits.MAX_FILES)
                        return@withContext InstallResult.Rejected("Terlalu banyak file")

                    val dest = File(root, name).canonicalFile
                    if (!dest.path.startsWith(root.path + File.separator))
                        return@withContext InstallResult.Rejected("Path keluar dari folder tujuan: $name")
                    if (dest.exists())
                        return@withContext InstallResult.Rejected("Nama ganda: $name")
                    dest.parentFile!!.mkdirs()

                    var n = 0L
                    dest.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val r = zin.read(buf); if (r < 0) break
                            n += r; total += r
                            if (n > SkinLimits.MAX_FILE_BYTES || total > SkinLimits.MAX_TOTAL_BYTES)
                                return@withContext InstallResult.Rejected("Ukuran melebihi batas")
                            out.write(buf, 0, r)
                        }
                    }
                }
            }

            // symlink tidak bisa terbentuk: kita hanya menulis berkas biasa lewat stream.

            val manifestFile = File(root, "skin.json")
            if (!manifestFile.isFile)
                return@withContext InstallResult.Rejected("skin.json tidak ada di root zip")
            val manifest = runCatching { SkinJson.decodeFromString<SkinManifest>(manifestFile.readText()) }
                .getOrElse { return@withContext InstallResult.Rejected("skin.json tidak valid") }
            if (manifest.schemaVersion != 1) return@withContext InstallResult.Rejected("schema_version tidak didukung")
            if (!SkinLimits.ID_REGEX.matches(manifest.skin.id) || manifest.skin.id == "default")
                return@withContext InstallResult.Rejected("id skin tidak valid atau dicadangkan")
            if (manifest.compatibility.containsExecutableCode || manifest.compatibility.requiresNetwork)
                return@withContext InstallResult.Rejected("Skin meminta kemampuan yang tidak diizinkan")
            if (compareVersions(BuildConfig.VERSION_NAME, manifest.skin.minNekosVersion) < 0)
                return@withContext InstallResult.Rejected("Butuh NekOS ${manifest.skin.minNekosVersion} atau lebih baru")

            // validasi dimensi gambar TANPA decode penuh
            root.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("png", "webp") }.forEach { f ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > SkinLimits.MAX_IMAGE_SIDE || o.outHeight > SkinLimits.MAX_IMAGE_SIDE)
                    return@withContext InstallResult.Rejected("Gambar tidak valid atau terlalu besar: ${f.name}")
            }

            // pindah atomik: skin lama (id sama) diganti
            val target = File(skinsDir, manifest.skin.id)
            val old = File(skinsDir, ".old-${UUID.randomUUID()}")
            if (target.exists()) check(target.renameTo(old))
            check(staging.renameTo(target))
            old.deleteRecursively()
            InstallResult.Ok(manifest)
        } catch (t: Throwable) {
            InstallResult.Rejected("Gagal memasang skin")
        } finally {
            staging.deleteRecursively()      // no-op bila sudah dipindah
        }
    }
}
```

Catatan:
- `compareVersions` perlu ditulis (bandingkan semver per segmen). Selama belum ada versi nyata, `min_nekos_version = "0.0.0"`.
- Validasi detail (ukuran kanvas per aset, mask putih) **tidak wajib** di perangkat: itu tugas `validate_skin.py` untuk pembuat skin. Di perangkat cukup keamanan + toleransi: aset yang ukurannya salah → abaikan dan pakai fallback (§3.4), bukan menolak seluruh skin.
- Penggantian skin berid sama: tampilkan konfirmasi "perbarui skin X dari 1.0.0 ke 1.1.0?" bila UI-nya ada.

### 3.4 Resolver dengan fallback dan cache

```kotlin
class SkinAssetResolver(
    private val active: SkinPack,
    private val default: SkinPack,
    private val cache: LruCache<String, ImageBitmap?>,
) {
    /** Skin aktif dulu; kalau tidak ada / gagal decode / ukuran tak sesuai kontrak → bawaan. */
    fun image(path: String, expect: IntSize? = null): ImageBitmap? {
        val key = "${active.manifest.skin.id}:$path"
        cache.get(key)?.let { return it }
        val bmp = decode(active, path, expect) ?: decode(default, path, expect)
        cache.put(key, bmp)
        return bmp
    }

    private fun decode(pack: SkinPack, path: String, expect: IntSize?): ImageBitmap? =
        pack.source.open(path)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            ?.takeIf { expect == null || (it.width == expect.width && it.height == expect.height) }

    fun tagGlyph(name: String) = image("icons/tag/$name.webp", IntSize(96, 96))
    fun uiIcon(name: String)   = image("icons/ui/$name.webp",  IntSize(96, 96))
    fun badge()                = image("components/tag_badge/badge_cat.webp", IntSize(128, 128))
    fun topFrame4x()           = image("components/top_frame/topframe_spritesheet@4x.png", IntSize(536, 256))
    fun tagFrame()             = image("components/tag_frame/frame_spritesheet.webp", IntSize(512, 240))
}
```

Hal praktis:
- `name` dicari hanya di katalog (`icons_index.json` bawaan). Nama di luar katalog → diabaikan (aturan spec §3).
- Ukuran cache: ikon 96×96×4 B ≈ 36 KB → 81 ikon ≈ 3 MB. Motif 1024² ≈ 4 MB per berkas; di perangkat `ActivityManager.isLowRamDevice` pakai `@512` (1 MB). Batasi cache motif ke 1–2 entri.
- Muat di thread latar; tampilkan fallback sementara (jangan blok UI).
- Reset cache saat skin aktif berganti.
- Berikan lewat `CompositionLocal`:

```kotlin
val LocalSkin = staticCompositionLocalOf<SkinAssetResolver> { error("SkinAssetResolver belum disediakan") }
```

### 3.5 Warna: tiga peran dan kontras glyph

| Peran | Dipakai oleh | Sumber warna |
|---|---|---|
| `shared_ui` | badge, TopFrame, TagFrame, (motif, usulan) | pilihan **Shared UI Color** |
| `on_badge` | glyph ikon tag | krem `#F5F2ED` **atau** gelap, dipilih otomatis |
| `foreground` | semua ikon UI | warna teks/ikon tema |

Tint: `ColorFilter.tint(color, BlendMode.SrcIn)`.

**Masalah yang sudah terukur:** krem di atas badge Silver `#C9CED6` = 1,42 : 1 (hampir hilang). Pilih otomatis:

```kotlin
private val OnBadgeLight = Color(0xFFF5F2ED)
private val OnBadgeDark  = Color(0xFF1B1B1F)

fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

fun onBadgeColor(badge: Color): Color =
    if (contrast(OnBadgeLight, badge) >= 3f) OnBadgeLight else OnBadgeDark
```

Hasil contoh: Purple → krem (5,41), Ocean → krem (5,07), Silver → gelap (10,86), Dark → krem (13,59). Ini keputusan produk (§6 #4): bila ingin tetap krem di semua warna, minimal ganti warna Silver.

### 3.6 Ikon tag = badge + glyph

```kotlin
@Composable
fun TagIcon(
    glyph: String,                    // "icon_brain" (tanpa .webp)
    badgeTint: Color,
    size: Dp = 32.dp,
    slot: IntRect = IntRect(16, 22, 112, 118),   // dari badge.json skin: x=16 y=22 w=96 h=96
) {
    val skin = LocalSkin.current
    val badge = skin.badge() ?: return
    val g = skin.tagGlyph(glyph) ?: skin.tagGlyph(FALLBACK_TAG_GLYPH) ?: return
    val onBadge = remember(badgeTint) { onBadgeColor(badgeTint) }
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {      // dekoratif; label diberi oleh pemanggil
        val k = this.size.width / badge.width                   // px layar per px kanvas 128
        drawImage(
            badge, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            colorFilter = ColorFilter.tint(badgeTint, BlendMode.SrcIn), filterQuality = FilterQuality.High,
        )
        drawImage(
            g,
            dstOffset = IntOffset((slot.left * k).roundToInt(), (slot.top * k).roundToInt()),
            dstSize = IntSize((slot.width * k).roundToInt(), (slot.height * k).roundToInt()),
            colorFilter = ColorFilter.tint(onBadge, BlendMode.SrcIn), filterQuality = FilterQuality.High,
        )
    }
}
```

`slot` sebaiknya dibaca dari `components/tag_badge/badge.json` skin aktif (Fase 4). Bawaan: pusat slot (64, 70), sengaja lebih rendah dari pusat kanvas karena telinga.

> Pada `clearAndSetSemantics { }` di atas, jangan hapus semantik yang sebelumnya diberikan pemanggil; sesuaikan dengan cara komponen lama memberi `contentDescription`. Skin tidak boleh mengubah semantik aksesibilitas.

### 3.7 TopFrame

Rumus (`topframe_formula.json`): `bar_each = (W − 2·32 − 60) / 2` dp, `W ≥ 124 dp`, tinggi 64 dp. Offset visual −6 dp (Alert Dialog, Launcher Settings) tetap di kode, **bukan** bagian skin.

```kotlin
@Composable
fun TopFrame(width: Dp, tint: Color, modifier: Modifier = Modifier) {
    val sheet = LocalSkin.current.topFrame4x() ?: return
    Canvas(modifier.size(width.coerceAtLeast(124.dp), 64.dp)) {
        val d = density
        fun px(dp: Float) = (dp * d).roundToInt()
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        val cap = px(32f); val head = px(60f)
        val barL = (w - 2 * cap - head) / 2
        val barR = w - 2 * cap - head - barL                    // sisa 1 px ke kanan, agar tajam
        val f = ColorFilter.tint(tint, BlendMode.SrcIn)
        fun part(sx: Int, sw: Int, dx: Int, dw: Int) {
            if (dw <= 0) return
            drawImage(sheet, IntOffset(sx, 0), IntSize(sw, 256), IntOffset(dx, 0), IntSize(dw, h),
                colorFilter = f, filterQuality = FilterQuality.High)
        }
        var x = 0
        part(0, 128, x, cap);        x += cap          // left_cap  (tetap)
        part(136, 16, x, barL);      x += barL         // bar       (FillBounds)
        part(160, 240, x, head);     x += head         // head      (tetap)
        part(136, 16, x, barR);      x += barR         // bar       (FillBounds)
        part(408, 128, x, cap)                         // right_cap (tetap)
    }
}
```

Koordinat 4x (dari `topframe_atlas.json`): `left_cap` x0 w128 · `bar_tile` x136 w16 · `head` x160 w240 · `right_cap` x408 w128; celah transparan 8 px di antaranya. Kalau tampak garis tipis di tepi bar saat distretch, cek celah (sprite sudah menyisakannya) dan pembulatan lebar bar ke piksel utuh.

### 3.8 TagFrame (3-slice vertikal)

Sheet 512×240: `top` y0 h144 (tetap) · `middle` y144 h16 (stretch vertikal) · `bottom` y160 h80 (tetap). `H = 144 + h_middle + 80`, minimum 224 px sheet.

```kotlin
@Composable
fun TagFrame(heightDp: Dp, tint: Color, modifier: Modifier = Modifier) {
    val sheet = LocalSkin.current.tagFrame() ?: return
    // ASUMSI belum diverifikasi (§6 #1): 4 px sheet = 1 dp  ->  k = density / 4
    Canvas(modifier.size(128.dp, heightDp)) {
        val k = density / 4f
        val w = (512 * k).roundToInt()
        val topH = (144 * k).roundToInt(); val botH = (80 * k).roundToInt()
        val total = size.height.roundToInt()
        val midH = (total - topH - botH).coerceAtLeast(0)
        val f = ColorFilter.tint(tint, BlendMode.SrcIn)
        drawImage(sheet, IntOffset(0, 0),   IntSize(512, 144), IntOffset(0, 0),           IntSize(w, topH), colorFilter = f, filterQuality = FilterQuality.High)
        if (midH > 0)
            drawImage(sheet, IntOffset(0, 144), IntSize(512, 16),  IntOffset(0, topH),        IntSize(w, midH), colorFilter = f, filterQuality = FilterQuality.High)
        drawImage(sheet, IntOffset(0, 160), IntSize(512, 80),  IntOffset(0, total - botH), IntSize(w, botH), colorFilter = f, filterQuality = FilterQuality.High)
    }
}
```

Jangan pakai kode ini apa adanya: **ukur dulu skala tampil TagFrame di `HomeComponents.kt`** dan sesuaikan. Inset konten: kiri/kanan 35, atas 144, bawah 80 (px sheet).

### 3.9 Latar motif dialog

Urutan: warna latar → motif (tint, opacity) → TopFrame → konten. Motif harus di-clip bentuk dialog dan **diangkur ke pojok kiri-atas dialog** agar tidak bergeser saat di-resize.

```kotlin
@Composable
fun MotifBackground(
    motif: MotifEntry, tint: Color, shape: Shape, modifier: Modifier = Modifier,
    tileDp: Dp = motif.suggestedDisplayDp.dp, opacity: Float = motif.suggestedOpacity,
) {
    val skin = LocalSkin.current
    val d = LocalDensity.current.density
    val tilePx = (tileDp.value * d).roundToInt().coerceAtLeast(64)
    val brush = remember(motif.id, tilePx) {
        // pakai berkas @512 bila tile ≤ ~640 px: downscale 1024 → kecil dalam satu langkah membuat garis tipis bergerigi
        val path = if (tilePx <= 640 && motif.fileSmall != null) motif.fileSmall else motif.file
        val src = skin.image(path)?.asAndroidBitmap() ?: return@remember null
        val scaled = Bitmap.createScaledBitmap(src, tilePx, tilePx, true).asImageBitmap()
        ShaderBrush(ImageShader(scaled, TileMode.Repeated, TileMode.Repeated))
    }
    Box(modifier.clip(shape).drawBehind {
        brush?.let { drawRect(it, alpha = opacity, colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)) }
    })
}
```

Keputusan terbuka (§6 #2, #3): tint role motif dan satuan ukuran tile. Mulai dengan `shared_ui` dan dp, lalu cek di perangkat.

### 3.10 Penyimpanan pilihan & siklus hidup

- `DataStore`: `active_skin_id: String` (default `"default"`).
- Saat start: baca id → muat `SkinPack`; gagal (folder hilang, manifest rusak) → `default` dan hapus pilihan.
- Daftar skin = folder di `filesDir/skins/` yang punya `skin.json` valid, ditambah `default`.
- Hapus skin = hapus foldernya; bila itu skin aktif, pindah ke `default` **sebelum** menghapus.
- Bersihkan `.staging-*` dan `.old-*` saat aplikasi mulai.
- Ganti skin aktif di runtime = ganti nilai `StateFlow` + kosongkan cache; seluruh UI recompose.

### 3.11 Hal yang tidak boleh berubah karena skin

Peluncuran aplikasi, gestur, navigasi, izin, penyimpanan data, ukuran area sentuh (minimal 48 dp), `contentDescription`, urutan fokus. Kalau sebuah skin mengubah satu pun dari ini, itu bug launcher, bukan fitur skin.

---

## 4. Migrasi data tag lama

`migration/legacy_icon_map.json` memetakan 100 ikon tag lama dan 120 ikon UI lama:

| Status | Tag | UI | Arti |
|---|---|---|---|
| `kept` | 36 | 37 | ikon sama, nama/format baru |
| `merged` | 4 | 6 | duplikat → dipetakan ke ikon yang dipertahankan |
| `removed` | 60 | 77 | tidak ada padanan |

Pengguna yang sudah memilih salah satu ikon yang **dihapus** (mis. `key`, `wifi`, `bell`) akan kehilangan ikonnya. Jalankan migrasi sekali (flag di DataStore):

```kotlin
class LegacyIconMigrator(json: String) {
    private val tag: Map<String, String?> = parse(json, "tag")        // "icon_011.webp" -> "icon_mail.webp" | null
    fun migrateTag(stored: String): String {
        val key = stored.substringAfterLast('/')                      // toleran: path penuh atau nama saja
        return tag[key] ?: FALLBACK_TAG_ICON                          // usulan: icon_star.webp (keputusan produk)
    }
}
```

Format penyimpanan referensi ikon di NekOS (nomor, nama berkas, atau nama bentuk) **tidak diketahui dari kit**; setiap entri peta memuat `old_file`, `old_name`, dan `new_file` agar bisa dicari lewat salah satunya. Kalau ada ikon yang sering dipakai tetapi dihapus, ambil kembali dari zip asli (`MIGRATION.md` §Ikon dihapus) dan gambar ulang mengikuti spec.

---

## 5. Pengujian

### Otomatis

- **Parsing manifest:** `skin.json` default, contoh parsial, field tak dikenal, field hilang.
- **Installer** dengan zip di `tests/fixtures/`:

| Fixture | Hasil yang diharapkan |
|---|---|
| `ok_partial.zip` | diterima; ikon yang tidak ada jatuh ke bawaan |
| `bad_slip.zip` | ditolak: `../evil.json` |
| `bad_ext.zip` | ditolak: `.sh`, `.dex` |
| `bad_wrap.zip` | ditolak: `skin.json` di dalam folder pembungkus |
| `bad_link.zip` | ditolak/diabaikan: symlink (Java stream menulis sebagai file biasa; pastikan tidak pernah mengikuti link) |
| `bad_bomb.zip` | ditolak: > 32 MB setelah dibaca |
| `bad_icons.zip` | ikon berwarna/ukuran salah ditolak atau jatuh ke fallback; launcher tidak crash |

- **Resolver:** aset hilang → fallback; ukuran salah → fallback; gambar korup → fallback.
- **Migrator:** semua 100 entri tag memberi nama yang ada di katalog baru atau fallback.
- **Screenshot test** (Paparazzi/Roborazzi atau setara) untuk TagIcon, TopFrame, TagFrame, MotifBackground dengan skin default.

### Manual di perangkat

- [ ] Densitas: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi (ikon 24 dp tidak buram; TopFrame tanpa celah/garis).
- [ ] 12 warna Shared UI Color, terutama Silver dan Dark.
- [ ] Mode terang/gelap, skala font 1,3×, RTL (ikon arah mencerminkan).
- [ ] TopFrame lebar 124 dp dan 360 dp; TagFrame tinggi minimum dan tinggi besar.
- [ ] Dialog kecil + motif: teks tetap terbaca pada opacity yang disarankan.
- [ ] Impor skin, ganti, hapus skin aktif (kembali ke default), impor ulang id sama (pembaruan).
- [ ] RAM rendah: `adb shell am` batasi memori; tidak OOM saat motif 1024.
- [ ] Rotasi layar saat dialog bermotif terbuka.

---

### Definisi selesai per fase

- Fase 1: semua layar memakai aset baru; aset lama terhapus; APK mengecil; migrasi data lulus uji.
- Fase 3: zip valid terpasang & aktif tanpa restart; semua fixture buruk ditolak tanpa crash.
- Fase 4: skin parsial (hanya badge) tampil benar; skin tanpa motif tidak menampilkan latar kosong.

---

## 6. Keputusan terbuka / belum terverifikasi

Ringkasan dari `03_REFERENSI_ASET.md` §12 (tabel lengkap di sana). Yang menghalangi pekerjaan:

| # | Hal | Dampak bila salah | Tindakan |
|---|---|---|---|
| 1 | Skala tampil **TagFrame** | frame terlalu besar/kecil | ukur dari kode; isi `display_dp` di manifest |
| 2 | **Tint role motif** | warna motif tidak sesuai tema | putuskan; default usulan `shared_ui` |
| 3 | Satuan tile motif (dp vs px) | motif terlalu besar/kecil | coba di perangkat; ubah `suggested_display_dp` |
| 4 | `on_badge` otomatis vs krem tetap | glyph hilang di warna terang | implementasikan §3.5 |
| 5 | Fallback ikon tag terhapus | tag lama tampil ikon "acak" | putuskan ikon fallback |
| 6 | Batas ukuran zip | skin sah ditolak / skin besar lolos | sesuaikan `SkinLimits` |
| 7 | **PNG runtime TagFrame vs WebP sumber** | frame tampak beda | bandingkan visual sebelum mengganti |
| 8 | Sheet 1x/2x TopFrame | hanya soal ukuran/kualitas | turunkan dari 4x atau tetap dikirim |

Di luar cakupan v1 (sengaja): warna/tipografi/layout dalam skin (`design_tokens.template.json` baru placeholder), tanda tangan/verifikasi skin, toko skin online, font, wallpaper.
