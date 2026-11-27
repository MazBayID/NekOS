# Duitku Sandbox setup

NekOS keeps Midtrans and Duitku as separate payment choices under the same Skin Store order system.

## Supabase Edge Function secrets

In the Supabase Dashboard, open **Project Settings → Edge Functions → Secrets** and add:

- `DUITKU_MERCHANT_CODE` — Merchant Code from the Duitku merchant project.
- `DUITKU_API_KEY` — API Key / Merchant Key for that project.
- `DUITKU_ENVIRONMENT` — set to `sandbox` while testing.

Do not commit these values to GitHub or place them in the Android app.

## Duitku callback and return URLs

Configure the Duitku project callback URL as:

`https://vizmufbfaghqucsoukne.supabase.co/functions/v1/skin-store/duitku-callback`

The checkout return URL is:

`https://mazbayid.github.io/skin-payment-return/`

The callback is verified with Duitku's HMAC-SHA256 signature and the local order amount before the Skin Store grants an entitlement.

## Customer email

Duitku requires an email when NekOS creates an invoice. The app asks for it only after the customer selects Duitku. NekOS sends it to Duitku for invoice creation and does not save it in local preferences, the database, or callback payloads.

## Sandbox verification

1. Set the three secrets above with the Sandbox Merchant Code and API Key.
2. Use a NekOS build that includes the provider selector.
3. Buy a paid skin and select **Duitku**.
4. Complete a test payment in the Duitku Sandbox checkout page.
5. Return to NekOS and use **CHECK & INSTALL** to reconcile the confirmed order.
6. Confirm the order is paid and the skin entitlement is granted.

Midtrans remains available and uses its existing credentials and webhook.
