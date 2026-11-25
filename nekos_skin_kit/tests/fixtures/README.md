# Fixture zip untuk menguji installer skin

Dipakai di unit test installer launcher (lihat `docs/01_PANDUAN_IMPLEMENTASI_LAUNCHER.md` §5) dan untuk mencoba `tools/validate_skin.py`.

| File | Isi | Hasil yang diharapkan |
|---|---|---|
| `ok_default.zip` | skin bawaan lengkap (`default-2.0.0`) | lolos validator. **id `default` dicadangkan** untuk skin bawaan APK, jadi installer sebaiknya menolak impor dengan id ini; berkas ini untuk uji validator/ukuran, bukan untuk diimpor |
| `ok_partial.zip` | skin parsial: badge + `icon_brain` + `icon_gear` | diterima; aset lain jatuh ke bawaan |
| `bad_slip.zip` | entri `../evil.json` | ditolak (zip-slip) |
| `bad_ext.zip` | `run.sh`, `classes.dex` | ditolak (ekstensi) |
| `bad_wrap.zip` | `skin.json` di dalam folder pembungkus | ditolak (tidak ada skin.json di root) |
| `bad_link.zip` | entri symlink | ditolak/diabaikan; tidak boleh pernah mengikuti link |
| `bad_bomb.zip` | satu berkas 40 MB nol (±40 KB terkompresi) | ditolak (melebihi batas setelah dibaca) |
| `bad_icons.zip` | ikon berwarna, ukuran salah, nama numerik, manifest menunjuk berkas yang tidak ada | ikon salah ditolak/fallback; tidak crash |

Cek dengan validator: `python3 -I tools/validate_skin.py tests/fixtures/<file>.zip`.
