# NekOS Payment Recovery

## Tujuan
Pulihkan alur pembayaran sampai skin terunduh dan terpasang, dengan transaksi nyata tetap aman.

## Urutan kerja
1. Audit fondasi: repository, database, RPC, Edge Functions, secrets/config, dan log.
2. Midtrans: audit → perbaiki → uji sandbox → verifikasi.
3. PayPal: audit → perbaiki → uji sandbox → verifikasi.
4. Aplikasi: checkout → status → download → instalasi.
5. Finalisasi: regresi, security/performance advisors, deploy, verifikasi produksi.

## Aturan
- Satu tahap pada satu waktu; audit sebelum edit.
- Jangan membuat pembayaran nyata untuk pengujian.
- Jangan memaksa status paid atau mengubah/membatalkan transaksi pelanggan.
- Jangan menerapkan migrasi lama sebelum memeriksa dampaknya.
- Setelah setiap tahap, catat commit, deployment, tes, dan masalah tersisa.
- Provider yang didukung: Midtrans dan PayPal. Duitku tidak digunakan.

## Checkpoint 1 — Fondasi (28 Nov 2026)
- Repository: `MazBayID/NekOS`, branch utama `main`.
- Supabase project: `vizmufbfaghqucsoukne`.
- Edge Functions produksi: `skin-store` v48, `skin-manager` v34.
- Tabel privat pembayaran tersedia: `device_installations`, `skin_orders`, `skin_payments`, `skin_entitlements`, `skin_payment_events`; RLS aktif.
- Migrasi produksi terakhir: `20261128075520`.
- Kode `supabase/functions/skin-store/index.ts` identik byte-per-byte dengan source produksi v48 (53,350 karakter).
- RPC pembayaran ditemukan; hak EXECUTE ditolak untuk anon/authenticated dan diberikan kepada service_role.
- Advisor keamanan: `public.skin_manager_admins` memiliki RLS tanpa policy (INFO); perlindungan password bocor Auth nonaktif (WARN).
- Advisor performa: FK `skin_entitlements.skin_id` belum berindeks (INFO).
- `private.skin_payment_events` masih kosong; implementasi webhook belum menulis event ke tabel tersebut.
- Log 28 Nov: 7 request purchase (terbaru 07:30 UTC), 8 download historis (terbaru 07:17 UTC), tidak ada POST ke `paypal-webhook`; hanya satu GET ke route tersebut (bukan bukti webhook diterima).
- Status API terakhir yang terlihat pada 09:49 UTC tetap HTTP 200; HTTP 200 hanya menunjukkan endpoint merespons, bukan bukti pembayaran selesai.
- Transaksi Aurum PayPal USD 1.49 masih `pending`; jangan ubah atau bayar ulang tanpa rekonsiliasi provider.

## Checkpoint 1 — temuan yang perlu ditelusuri
- Kode backend `skin-store` sama dengan source produksi v48; RPC payment sudah dibatasi ke service_role.
- Android `NekOSPayPalCheckout.kt` mengunci `CoreEnvironment.SANDBOX`, sedangkan backend `PAYPAL_MODE` dapat `sandbox` atau `live`. Mode dan client ID SDK belum disinkronkan dari konfigurasi backend; ini berisiko mismatch saat backend live.
- PayPal return URL web diarahkan ke halaman instruksi statis; pemasangan tetap harus dilanjutkan dari aplikasi.
- Android mengirim order ID ke `/status`; backend akan capture bila PayPal order `APPROVED`, dan entitlement hanya diberikan setelah capture `COMPLETED` serta validasi amount/currency/reference.
- Log 28 Nov: 53 request `/status` HTTP 200, 7 `/purchase` HTTP 201, 8 `/download` HTTP 200; tidak ada request POST ke `/webhook` maupun `/paypal-webhook` pada rentang log yang diperiksa.
- `skin_payment_events` kosong dan webhook tidak menyimpan event. Status polling tetap tersedia sebagai jalur rekonsiliasi.
- Midtrans Snap membuat payment attempt sebelum memanggil API provider; retry dapat memakai order yang sama dan attach payment di-upsert. Midtrans status/webhook memverifikasi status provider, order ID, nominal, mata uang, dan signature.
- Koneksi Supabase saat ini tidak menyediakan pembacaan daftar secret Edge Function; nilai/keberadaan secret dan mode aktif belum dapat diverifikasi tanpa endpoint diagnostik aman atau akses dashboard.
- Migrasi lama `20261127160000_skin_store_reject_late_cancelled_payment.sql` tidak boleh diterapkan langsung: provider allowlist-nya masih `midtrans,duitku` dan dapat menimpa fungsi terbaru.
- Belum ditemukan test integrasi otomatis untuk alur transaksi provider; test Android yang ada hanya mencakup pemilihan provider dan validasi installer.

## Status
Tahap 1 audit kode dan database selesai; verifikasi secret aktif masih terbatas oleh akses connector. Belum ada kode aplikasi, konfigurasi, database, deployment, atau transaksi produksi yang diubah. Berikutnya: Tahap 2 — audit dan uji Midtrans sandbox.
