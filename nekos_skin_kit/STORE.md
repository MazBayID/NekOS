# NekOS Official Skin Store

## Architecture

- Public contribution page: https://mazbayid.github.io/skin-manager/
- Launcher catalog and purchase API: `https://vizmufbfaghqucsoukne.supabase.co/functions/v1/skin-store`
- Supabase project: `vizmufbfaghqucsoukne` (Singapore)
- Payment gateway: Midtrans Snap (IDR)
- Buyer accounts: none; entitlements are bound to a server-HMAC of Android `ANDROID_ID`.

## Purchase and delivery

1. The launcher requests the public catalog.
2. It submits the device Android ID and selected skin UUID to the Edge Function over TLS.
3. The server HMACs the Android ID; raw Android ID is not stored.
4. Free skins are eligible for immediate package delivery. Paid skins create a pending order and a Midtrans Snap checkout.
5. The Midtrans webhook is signature-checked and the transaction is re-queried from Midtrans before granting an entitlement.
6. The launcher checks the order after returning from checkout.
7. The Edge Function authorizes the device and creates a 90-second signed URL for the private ZIP.
8. The launcher downloads to cache, checks SHA-256, installs through the existing SkinInstaller, and deletes the temporary ZIP.

The package ZIP remains private. Do not publish package URLs or add privileged keys to the Android app or GitHub Pages.

## Required Supabase Edge Function secrets

Set these in Supabase Dashboard → Project Settings → Edge Functions → Secrets:

- `SKIN_DEVICE_HMAC_SECRET`: a private random secret of at least 32 characters.
- `MIDTRANS_SERVER_KEY`: Midtrans Server Key from the selected environment.
- `MIDTRANS_ENVIRONMENT`: `sandbox` for test mode, `production` only after merchant activation.

Never commit these values. Never place the Midtrans Server Key, Supabase service-role key, or HMAC secret in the launcher APK.

## Midtrans activation

Configure the Midtrans Payment Notification URL (in both Sandbox and Production when applicable) to:

\`https://vizmufbfaghqucsoukne.supabase.co/functions/v1/skin-store/webhook\`

The payment return page is https://mazbayid.github.io/skin-payment-return/ . It asks the buyer to return to NekOS and check payment status; entitlement is never granted from the redirect alone.

Midtrans currently supports Indonesian individual merchants. The current documented individual requirements are KTP and NPWP. Register at https://dashboard.midtrans.com/register, finish merchant verification, and add a bank account in the same personal name as the individual merchant. Enable Snap and payment methods in the merchant dashboard.

Official docs:
- https://docs.midtrans.com/docs/apa-saja-dokumen-legalitas-yang-diperlukan-untuk-registrasi-akun-midtrans
- https://docs.midtrans.com/docs/snap-snap-integration-guide
- https://docs.midtrans.com/docs/https-notification-webhooks

## Known security trade-off

The device ID is an Android identifier and can be spoofed by a modified client. HMAC protects database privacy and prevents raw identifier disclosure, but it does not make a client-submitted identifier cryptographic proof. For the initial direct-APK distribution this is a documented trade-off; add Play Integrity or a device key challenge later if stronger anti-abuse is required.
