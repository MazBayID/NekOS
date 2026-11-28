import { createClient } from "npm:@supabase/supabase-js@2.119.1";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const DEVICE_SECRET = Deno.env.get("SKIN_DEVICE_HMAC_SECRET") ?? "";
const MIDTRANS_ENV = (Deno.env.get("MIDTRANS_ENVIRONMENT") ?? "production").trim().toLowerCase();
const MIDTRANS_CORE_API_ENABLED = (Deno.env.get("MIDTRANS_CORE_API_ENABLED") ?? "false").trim().toLowerCase() === "true";
const MIDTRANS_KEY = MIDTRANS_ENV === "sandbox"
  ? Deno.env.get("MIDTRANS_SANDBOX_SERVER_KEY") ?? ""
  : Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";
const MIDTRANS_SIGNATURE_SECRET = MIDTRANS_ENV === "sandbox"
  ? Deno.env.get("MIDTRANS_SANDBOX_SIGNATURE_SECRET") ?? ""
  : Deno.env.get("MIDTRANS_SIGNATURE_SECRET") ?? "";
const PAYPAL_MODE = (Deno.env.get("PAYPAL_MODE") ?? "sandbox").trim().toLowerCase();
const PAYPAL_CLIENT_ID = Deno.env.get("PAYPAL_CLIENT_ID") ?? "";
const PAYPAL_CLIENT_SECRET = Deno.env.get("PAYPAL_CLIENT_SECRET") ?? "";
const PAYPAL_WEBHOOK_ID = Deno.env.get("PAYPAL_WEBHOOK_ID") ?? "";
const PAYPAL_BASE = PAYPAL_MODE === "live" ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
const ORIGIN = "https://mazbayid.github.io";
const PREVIEWS = "skin-previews";
// Snap sessions and their selected payment methods are configured for 15 minutes.
// Reconcile at 16 minutes to allow a small provider-side expiry processing buffer.
const SNAP_EXPIRY_RECONCILIATION_MS = 16 * 60 * 1000;
const db = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

function json(data: unknown, status = 200, origin = "") {
  const headers: Record<string,string> = { "Content-Type":"application/json; charset=utf-8", "Cache-Control":"no-store" };
  if (origin === ORIGIN) {
    headers["Access-Control-Allow-Origin"] = ORIGIN;
    headers["Access-Control-Allow-Headers"] = "content-type, apikey, authorization, x-client-info";
    headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS";
    headers["Vary"] = "Origin";
  }
  return new Response(JSON.stringify(data), { status, headers });
}
function fail(code:string,status:number,origin="",details?:string) {
  return json({error:code,...(details?{details}: {})},status,origin);
}
function validOrigin(req:Request) {
  const origin=req.headers.get("origin");
  return !origin || origin===ORIGIN;
}
function hex(bytes:Uint8Array) { return [...bytes].map(v=>v.toString(16).padStart(2,"0")).join(""); }
async function hmacDevice(androidId:string) {
  if (!DEVICE_SECRET || DEVICE_SECRET.length < 32) throw new Error("device_identity_not_configured");
  if (!/^[0-9a-fA-F]{16}$/.test(androidId)) throw new Error("invalid_android_id");
  const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(DEVICE_SECRET),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
  return hex(new Uint8Array(await crypto.subtle.sign("HMAC",key,new TextEncoder().encode("nekos-skin-store-v1:"+androidId.toLowerCase()))));
}
async function sha512(value:string) {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-512",new TextEncoder().encode(value))));
}
function constantEqual(a:string,b:string) {
  if(a.length!==b.length)return false;
  let diff=0;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);
  return diff===0;
}
function assertPaymentConfig(provider = "midtrans") {
  if (provider === "midtrans") {
    if (!MIDTRANS_KEY || !MIDTRANS_SIGNATURE_SECRET || !["production","sandbox"].includes(MIDTRANS_ENV)) {
      throw new Error("midtrans_not_configured");
    }
    return;
  }
  if (provider === "paypal") {
    if (!PAYPAL_CLIENT_ID || !PAYPAL_CLIENT_SECRET || !["sandbox","live"].includes(PAYPAL_MODE)) {
      throw new Error("paypal_not_configured");
    }
    return;
  }
  throw new Error("unsupported_payment_provider");
}
function midtransBase() {
  return MIDTRANS_ENV === "production" ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
}
function snapBase() {
  return MIDTRANS_ENV === "production" ? "https://app.midtrans.com" : "https://app.sandbox.midtrans.com";
}
function authHeader() { return "Basic " + btoa(MIDTRANS_KEY + ":"); }
async function rpc(name:string,args:Record<string,unknown>) {
  const {data,error}=await db.rpc(name,args);
  if(error) throw new Error(error.message);
  return data;
}
async function catalog(origin:string, deviceHash?:string, appVersion:string|null=null) {
  const {data,error}=await db.from("skins")
    .select("id,skin_key,name,author,description,category,schema_version,min_nekos_version,preview_path,price_amount,price_usd,currency,package_sha256,package_size_bytes")
    .eq("status","published").order("created_at",{ascending:false}).limit(100);
  if(error)throw new Error("catalog_unavailable");
  const ownedIds = deviceHash
    ? new Set<string>((await rpc("skin_owned_catalog", {
        p_device_hash:deviceHash, p_app_version:appVersion
      }) as unknown[]).map(String))
    : new Set<string>();
  return (data??[]).map((skin:Record<string,unknown>)=>({
    ...skin,
    owned:ownedIds.has(String(skin.id)),
    preview_url:db.storage.from(PREVIEWS).getPublicUrl(String(skin.preview_path)).data.publicUrl,
  }));
}
function midtransExpiryStartTime(date = new Date()) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Jakarta",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  }).formatToParts(date);
  const values = Object.fromEntries(
    parts.filter(part => part.type !== "literal").map(part => [part.type, part.value])
  );
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second} +0700`;
}

async function createSnapPayment(
  order:Record<string,unknown>, orderId:string, skin:Record<string,unknown>
) {
  const amount = Number(order.amount);
  if (!Number.isSafeInteger(amount) || amount < 1) throw new Error("invalid_order_amount");
  assertPaymentConfig("midtrans");
  await rpc("skin_attach_payment", {
    p_order_id:orderId, p_provider_order_id:orderId, p_checkout_url:null, p_provider:"midtrans"
  });
  const response = await fetch(snapBase()+"/snap/v1/transactions", {
    method:"POST",
    headers:{Authorization:authHeader(),"Content-Type":"application/json","Accept":"application/json","Idempotency-Key":orderId},
    body:JSON.stringify({
      transaction_details:{order_id:orderId,gross_amount:amount},
      item_details:[{id:String(skin.id),price:amount,quantity:1,name:String(skin.name).slice(0,50)}],
      callbacks:{
        finish:"https://mazbayid.github.io/skin-payment-return/",
        error:"https://mazbayid.github.io/skin-payment-return/",
        pending:"https://mazbayid.github.io/skin-payment-return/"
      },
      expiry:{start_time:midtransExpiryStartTime(),unit:"minutes",duration:15}
    })
  });
  const data=await response.json();
  if(!response.ok||!data.token||!data.redirect_url) {
    console.error("skin_store_midtrans_snap_failed",JSON.stringify({http_status:response.status,status_code:data.error_messages}));
    throw new Error("midtrans_checkout_creation_failed");
  }
  const checkoutUrl=String(data.redirect_url);
  const parsed=new URL(checkoutUrl);
  const allowedHosts=MIDTRANS_ENV==="production"?["app.midtrans.com"]:["app.sandbox.midtrans.com"];
  if(parsed.protocol!=="https:"||!allowedHosts.includes(parsed.hostname))throw new Error("invalid_midtrans_checkout_url");
  await rpc("skin_attach_payment",{
    p_order_id:orderId,p_provider_order_id:orderId,p_checkout_url:checkoutUrl,p_provider:"midtrans"
  });
  return {checkout_url:checkoutUrl,order_id:orderId,provider:"midtrans",payment_method:"snap"};
}

async function createCoreGoPayPayment(
  order:Record<string,unknown>, orderId:string, skin:Record<string,unknown>
) {
  const amount = Number(order.amount);
  if (!Number.isSafeInteger(amount) || amount < 1) throw new Error("invalid_order_amount");
  assertPaymentConfig("midtrans");
  await rpc("skin_attach_payment", {
    p_order_id:orderId, p_provider_order_id:orderId, p_checkout_url:null, p_provider:"midtrans"
  });
  const response = await fetch(midtransBase()+"/v2/charge", {
    method:"POST",
    headers:{Authorization:authHeader(),"Content-Type":"application/json","Accept":"application/json"},
    body:JSON.stringify({
      payment_type:"gopay",
      transaction_details:{order_id:orderId,gross_amount:amount},
      item_details:[{id:String(skin.id),price:amount,quantity:1,name:String(skin.name).slice(0,50)}],
      gopay:{enable_callback:true,callback_url:"nekos://gopay-finish"}
    })
  });
  const data=await response.json().catch(()=>({}));
  if(!response.ok || String(data.status_code??"")!=="201" ||
     String(data.order_id??"")!==orderId ||
     String(data.currency??"IDR").toUpperCase()!=="IDR" ||
     Number(data.gross_amount)!==amount ||
     String(data.transaction_status??"").toLowerCase()!=="pending") {
    console.error("skin_store_midtrans_core_charge_failed",JSON.stringify({
      http_status:response.status,status_code:String(data.status_code??""),
      status_message:String(data.status_message??"")
    }));
    throw new Error("midtrans_gopay_creation_failed");
  }
  const action=(Array.isArray(data.actions)?data.actions:[]).find(
    (item:Record<string,unknown>)=>String(item.name??"")==="deeplink-redirect"
  );
  if(!action?.url) throw new Error("midtrans_gopay_deeplink_missing");
  const checkoutUrl=String(action.url);
  const parsed=new URL(checkoutUrl);
  const host=parsed.hostname.toLowerCase();
  const allowed= MIDTRANS_ENV==="sandbox"
    ? host==="simulator.sandbox.midtrans.com"
    : host==="gopay.co.id" || host.endsWith(".gopay.co.id") ||
      host==="gojek.link" || host.endsWith(".gojek.link") ||
      host==="link.gojek.com";
  if(parsed.protocol!=="https:" || !allowed) throw new Error("invalid_midtrans_gopay_url");
  await rpc("skin_attach_payment",{
    p_order_id:orderId,p_provider_order_id:orderId,p_checkout_url:checkoutUrl,p_provider:"midtrans"
  });
  return {
    checkout_url:checkoutUrl,order_id:orderId,provider:"midtrans",
    payment_method:"gopay",provider_payment_id:String(data.transaction_id??"")
  };
}

async function createPayment(
  order:Record<string,unknown>, orderId:string, skin:Record<string,unknown>
) {
  return MIDTRANS_CORE_API_ENABLED
    ? await createCoreGoPayPayment(order,orderId,skin)
    : await createSnapPayment(order,orderId,skin);
}


async function paypalAccessToken() {
  assertPaymentConfig("paypal");
  const credentials = btoa(PAYPAL_CLIENT_ID + ":" + PAYPAL_CLIENT_SECRET);
  const response = await fetch(PAYPAL_BASE + "/v1/oauth2/token", {
    method: "POST",
    headers: { Authorization: "Basic " + credentials, "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: "grant_type=client_credentials"
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) {
    console.error("skin_store_paypal_oauth_failed", JSON.stringify({ http_status: response.status }));
    throw new Error("paypal_authentication_failed");
  }
  return String(data.access_token);
}
async function paypalRequest(path: string, init: RequestInit = {}) {
  const token = await paypalAccessToken();
  const headers = new Headers(init.headers ?? {});
  headers.set("Authorization", "Bearer " + token);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(PAYPAL_BASE + path, { ...init, headers });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    console.error("skin_store_paypal_api_failed", JSON.stringify({
      path, http_status: response.status,
      issue: String(data.name ?? data.error ?? "paypal_api_error")
    }));
    throw new Error("paypal_api_request_failed");
  }
  return data as Record<string, any>;
}
function paypalApprovalUrl(data: Record<string, any>) {
  const link = (data.links ?? []).find((item: Record<string, unknown>) =>
    ["approve", "payer-action"].includes(String(item.rel ?? "").toLowerCase()));
  if (!link?.href) throw new Error("paypal_approval_url_missing");
  const url = new URL(String(link.href));
  const allowed = PAYPAL_MODE === "live" ? ["www.paypal.com"] : ["www.sandbox.paypal.com"];
  if (url.protocol !== "https:" || !allowed.includes(url.hostname)) throw new Error("invalid_paypal_approval_url");
  return url.toString();
}
async function createPayPalPayment(order: Record<string, unknown>, localOrderId: string, skin: Record<string, unknown>) {
  const amount = Number(order.amount);
  if (!Number.isFinite(amount) || amount <= 0 || String(order.currency).toUpperCase() !== "USD") {
    throw new Error("invalid_paypal_order_amount");
  }
  assertPaymentConfig("paypal");
  const value = amount.toFixed(2);
  const data = await paypalRequest("/v2/checkout/orders", {
    method: "POST",
    headers: { "PayPal-Request-Id": localOrderId },
    body: JSON.stringify({
      intent: "CAPTURE",
      purchase_units: [{
        reference_id: localOrderId,
        custom_id: localOrderId,
        description: "NekOS skin: " + String(skin.name).slice(0, 120),
        amount: {
          currency_code: "USD",
          value,
          breakdown: { item_total: { currency_code: "USD", value } }
        },
        items: [{
          name: String(skin.name).slice(0, 127),
          quantity: "1",
          category: "DIGITAL_GOODS",
          unit_amount: { currency_code: "USD", value }
        }]
      }],
      application_context: {
        brand_name: "NekOS",
        user_action: "PAY_NOW",
        return_url: "https://mazbayid.github.io/skin-payment-return/?provider=paypal&order_id=" + encodeURIComponent(localOrderId),
        cancel_url: "https://mazbayid.github.io/skin-payment-return/?provider=paypal&cancel=1&order_id=" + encodeURIComponent(localOrderId)
      }
    })
  });
  const paypalOrderId = String(data.id ?? "");
  if (!paypalOrderId || !["CREATED", "PAYER_ACTION_REQUIRED"].includes(String(data.status ?? ""))) {
    throw new Error("paypal_order_creation_failed");
  }
  const checkoutUrl = paypalApprovalUrl(data);
  await rpc("skin_attach_payment", {
    p_order_id: localOrderId,
    p_provider_order_id: paypalOrderId,
    p_checkout_url: checkoutUrl,
    p_provider: "paypal"
  });
  return { checkout_url: checkoutUrl, order_id: localOrderId, provider: "paypal", provider_order_id: paypalOrderId };
}
async function verifyPayPalCompletedOrder(paypalOrderId: string, localOrderId: string, expectedAmount: number) {
  const order = await paypalRequest("/v2/checkout/orders/" + encodeURIComponent(paypalOrderId));
  if (String(order.id ?? "") !== paypalOrderId || String(order.status ?? "") !== "COMPLETED") {
    throw new Error("paypal_payment_not_completed");
  }
  const units = Array.isArray(order.purchase_units) ? order.purchase_units : [];
  if (units.length !== 1) throw new Error("paypal_purchase_unit_mismatch");
  const unit = units[0];
  if (String(unit.custom_id ?? "") !== localOrderId || String(unit.reference_id ?? "") !== localOrderId) {
    throw new Error("paypal_order_reference_mismatch");
  }
  const captures = unit.payments?.captures ?? [];
  const completed = captures.filter((capture: Record<string, any>) => String(capture.status ?? "") === "COMPLETED");
  if (completed.length !== 1) throw new Error("paypal_capture_not_completed");
  const captureId = String(completed[0].id ?? "");
  if (!captureId) throw new Error("paypal_capture_id_missing");
  const capture = await paypalRequest("/v2/payments/captures/" + encodeURIComponent(captureId));
  const amount = String(capture.amount?.value ?? "");
  const currency = String(capture.amount?.currency_code ?? "").toUpperCase();
  if (String(capture.status ?? "") !== "COMPLETED" || currency !== "USD" ||
      !/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount).toFixed(2) !== expectedAmount.toFixed(2)) {
    throw new Error("paypal_capture_amount_mismatch");
  }
  const relatedOrderId = String(capture.supplementary_data?.related_ids?.order_id ?? paypalOrderId);
  if (relatedOrderId !== paypalOrderId) throw new Error("paypal_capture_order_mismatch");
  return { capture, captureId, payload: {
    gross_amount: amount,
    currency,
    paypal_order_id: paypalOrderId,
    status: String(capture.status),
    capture
  }};
}
async function grantPayPalEntitlement(paypalOrderId: string, localOrderId: string, expectedAmount: number) {
  const verified = await verifyPayPalCompletedOrder(paypalOrderId, localOrderId, expectedAmount);
  const granted = await rpc("skin_mark_paid", {
    p_provider: "paypal",
    p_provider_order_id: paypalOrderId,
    p_provider_payment_id: verified.captureId,
    p_payload: verified.payload
  });
  if (!granted) throw new Error("paypal_entitlement_grant_failed");
  return verified;
}
async function handlePayPalCapture(body: Record<string, unknown>, deviceHash: string, origin: string) {
  const localOrderId = String(body.order_id ?? "").trim();
  if (!localOrderId) return fail("invalid_order_id", 400, origin);
  let status = await rpc("skin_order_status", { p_order_id: localOrderId, p_device_hash: deviceHash });
  if (!status) return fail("order_not_found", 404, origin);
  if (status.owned || status.status === "paid") return json({ success: true, paid: true, owned: true, order_id: localOrderId }, 200, origin);
  const active = status.active_payment ?? null;
  if (!active || String(active.provider) !== "paypal") return fail("paypal_payment_not_found", 404, origin);
  const paypalOrderId = String(active.provider_order_id ?? "");
  if (!paypalOrderId) return fail("paypal_order_id_missing", 409, origin);
  assertPaymentConfig("paypal");
  let remote = await paypalRequest("/v2/checkout/orders/" + encodeURIComponent(paypalOrderId));
  if (String(remote.status ?? "") === "APPROVED") {
    remote = await paypalRequest("/v2/checkout/orders/" + encodeURIComponent(paypalOrderId) + "/capture", {
      method: "POST",
      headers: { "PayPal-Request-Id": localOrderId + "-capture" },
      body: "{}"
    });
  }
  if (String(remote.status ?? "") !== "COMPLETED") return fail("paypal_payment_not_completed", 409, origin);
  await grantPayPalEntitlement(paypalOrderId, localOrderId, Number(status.amount));
  status = await rpc("skin_order_status", { p_order_id: localOrderId, p_device_hash: deviceHash });
  return json({ success: true, paid: Boolean(status?.owned), owned: Boolean(status?.owned), order: status }, 200, origin);
}
async function handlePayPalWebhook(req: Request) {
  assertPaymentConfig("paypal");
  if (!PAYPAL_WEBHOOK_ID) return fail("paypal_webhook_not_configured", 503);
  const rawBody = await req.text();
  let event: Record<string, any>;
  try { event = JSON.parse(rawBody); } catch { return fail("invalid_notification", 400); }

  const headers = req.headers;
  const verification = await paypalRequest("/v1/notifications/verify-webhook-signature", {
    method: "POST",
    body: JSON.stringify({
      auth_algo: headers.get("paypal-auth-algo"),
      cert_url: headers.get("paypal-cert-url"),
      transmission_id: headers.get("paypal-transmission-id"),
      transmission_sig: headers.get("paypal-transmission-sig"),
      transmission_time: headers.get("paypal-transmission-time"),
      webhook_id: PAYPAL_WEBHOOK_ID,
      webhook_event: event
    })
  });
  if (String(verification.verification_status ?? "") !== "SUCCESS") return fail("invalid_signature", 401);

  const eventType = String(event.event_type ?? "");
  const resource = event.resource ?? {};

  if (eventType === "CHECKOUT.ORDER.APPROVED") {
    const paypalOrderId = String(resource.id ?? "");
    if (!paypalOrderId) return fail("invalid_notification", 400);
    let remoteOrder = await paypalRequest("/v2/checkout/orders/" + encodeURIComponent(paypalOrderId));
    if (String(remoteOrder.id ?? "") !== paypalOrderId) return fail("paypal_order_id_mismatch", 400);

    const units = Array.isArray(remoteOrder.purchase_units) ? remoteOrder.purchase_units : [];
    if (units.length !== 1) return fail("paypal_purchase_unit_mismatch", 400);
    const unit = units[0];
    const localOrderId = String(unit.custom_id ?? "");
    const referenceId = String(unit.reference_id ?? "");
    const amountText = String(unit.amount?.value ?? "");
    const currency = String(unit.amount?.currency_code ?? "").toUpperCase();
    if (!localOrderId || referenceId !== localOrderId || currency !== "USD" ||
        !/^\\d+(\\.\\d{1,2})?$/.test(amountText)) {
      return fail("paypal_order_reference_or_amount_invalid", 400);
    }

    if (String(remoteOrder.status ?? "") === "APPROVED") {
      remoteOrder = await paypalRequest(
        "/v2/checkout/orders/" + encodeURIComponent(paypalOrderId) + "/capture",
        {
          method: "POST",
          headers: { "PayPal-Request-Id": localOrderId + "-capture" },
          body: "{}"
        }
      );
    }
    if (String(remoteOrder.status ?? "") === "COMPLETED") {
      await grantPayPalEntitlement(paypalOrderId, localOrderId, Number(amountText));
    }
    // APPROVED is captured above; PENDING or other non-completed states never grant entitlement.
  } else if (eventType === "PAYMENT.CAPTURE.COMPLETED") {
    const captureId = String(resource.id ?? "");
    const paypalOrderId = String(resource.supplementary_data?.related_ids?.order_id ?? "");
    if (!captureId || !paypalOrderId) return fail("invalid_notification", 400);
    const remoteOrder = await paypalRequest("/v2/checkout/orders/" + encodeURIComponent(paypalOrderId));
    const units = Array.isArray(remoteOrder.purchase_units) ? remoteOrder.purchase_units : [];
    if (units.length !== 1) return fail("paypal_purchase_unit_mismatch", 400);
    const localOrderId = String(units[0].custom_id ?? "");
    const amountText = String(units[0].amount?.value ?? "");
    const currency = String(units[0].amount?.currency_code ?? "").toUpperCase();
    if (!localOrderId || String(units[0].reference_id ?? "") !== localOrderId ||
        currency !== "USD" || !/^\\d+(\\.\\d{1,2})?$/.test(amountText)) {
      return fail("paypal_order_reference_or_amount_invalid", 400);
    }
    const verified = await grantPayPalEntitlement(paypalOrderId, localOrderId, Number(amountText));
    if (verified.captureId !== captureId) return fail("paypal_capture_id_mismatch", 400);
  } else if (eventType === "PAYMENT.CAPTURE.DENIED") {
    const paypalOrderId = String(resource.supplementary_data?.related_ids?.order_id ?? "");
    if (paypalOrderId) {
      await rpc("skin_update_payment_status", {
        p_provider: "paypal", p_provider_order_id: paypalOrderId,
        p_status: "deny", p_payload: event
      });
    }
  } else if (eventType === "CHECKOUT.ORDER.VOIDED" ||
             eventType === "CHECKOUT.PAYMENT-APPROVAL.REVERSED") {
    const paypalOrderId = String(
      eventType === "CHECKOUT.PAYMENT-APPROVAL.REVERSED"
        ? resource.order_id ?? ""
        : resource.id ?? ""
    );
    if (paypalOrderId) {
      await rpc("skin_update_payment_status", {
        p_provider: "paypal", p_provider_order_id: paypalOrderId,
        p_status: "cancel", p_payload: event
      });
    }
  } else if (eventType === "PAYMENT.CAPTURE.PENDING") {
    // Acknowledge the notification, but do not grant access while capture is pending.
  }

  return json({ success: true });
}
async function handleCancel(body:Record<string,unknown>, deviceHash:string, origin:string) {
  const orderId=String(body.order_id??"").trim();
  if(!orderId)return fail("invalid_order_id",400,origin);
  const status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
  if(!status)return fail("order_not_found",404,origin);
  if(status.owned||status.status==="paid") {
    return json({success:true,paid:true,cancelled:false,message:"Pembayaran sudah berhasil. NekOS akan menyelesaikan pemasangan skin."},200,origin);
  }
  const active=status.active_payment??null;
  if(active && !["midtrans","paypal"].includes(String(active.provider))) {
    return fail("unsupported_active_payment_provider",409,origin);
  }
  if(active && String(active.provider)==="paypal") {
    assertPaymentConfig("paypal");
    const paypalOrderId=String(active.provider_order_id??"");
    let remote=await paypalRequest("/v2/checkout/orders/"+encodeURIComponent(paypalOrderId));
    const units=Array.isArray(remote.purchase_units)?remote.purchase_units:[];
    const unit=units[0]??{};
    const remoteAmount=String(unit.amount?.value??"");
    if(String(remote.id??"")!==paypalOrderId || units.length!==1 ||
       String(unit.custom_id??"")!==orderId || String(unit.reference_id??"")!==orderId ||
       String(unit.amount?.currency_code??"").toUpperCase()!=="USD" ||
       !/^\d+(\.\d{1,2})?$/.test(remoteAmount) ||
       Number(remoteAmount).toFixed(2)!==Number(status.amount).toFixed(2)) {
      return fail("paypal_order_reference_or_amount_mismatch",409,origin);
    }
    if(String(remote.status??"")==="APPROVED") {
      remote=await paypalRequest("/v2/checkout/orders/"+encodeURIComponent(paypalOrderId)+"/capture",{
        method:"POST",headers:{"PayPal-Request-Id":orderId+"-capture"},body:"{}"
      });
    }
    if(String(remote.status??"")==="COMPLETED") {
      await grantPayPalEntitlement(paypalOrderId,orderId,Number(status.amount));
      return json({success:true,paid:true,cancelled:false,message:"Pembayaran PayPal sudah berhasil. Skin akan dipasang."},200,origin);
    }
    if(["VOIDED","EXPIRED"].includes(String(remote.status??""))) {
      await rpc("skin_update_payment_status",{p_provider:"paypal",p_provider_order_id:paypalOrderId,
        p_status:"cancel",p_payload:remote});
      return json({success:true,cancelled:true,checkout_active:false,
        message:"Order PayPal sudah tidak aktif. Kamu bisa memilih metode pembayaran lain."},200,origin);
    }
    return json({success:true,cancelled:false,checkout_active:true,can_close_checkout:true,
      message:"Order PayPal masih dapat disetujui dan tidak memiliki API pembatalan langsung. NekOS akan menunggu status terminal sebelum mengizinkan pergantian metode pembayaran."},200,origin);
  }
  if(!active) {
    const terminal=["cancelled","failed","expired","expire","deny","denied"].includes(String(status.status??"").toLowerCase());
    return json({success:true,cancelled:terminal,checkout_active:false,
      message:terminal?"Pembelian ini sudah tidak aktif.":"Tidak ada checkout aktif untuk pesanan ini."},200,origin);
  }
  assertPaymentConfig("midtrans");
  const providerOrderId=String(active.provider_order_id??orderId);
  const statusUrl=midtransBase()+"/v2/"+encodeURIComponent(providerOrderId)+"/status";
  const fetchStatus=()=>fetch(statusUrl,{headers:{Authorization:authHeader(),"Accept":"application/json"}});
  let response=await fetchStatus();
  let snapSessionConfirmedInactive=false;
  let snapSessionInProgress=false;
  if(response.status===404) {
    const checkoutUrl=String(active.checkout_url??"");
    let snapToken="";
    try {
      const checkout=new URL(checkoutUrl);
      const parts=checkout.pathname.split("/").filter(Boolean);
      const marker=parts.lastIndexOf("vtweb");
      if(checkout.protocol==="https:" &&
         checkout.hostname===(MIDTRANS_ENV==="production"?"app.midtrans.com":"app.sandbox.midtrans.com") &&
         marker>=0 && parts[marker+1]) {
        snapToken=parts[marker+1];
      }
    } catch { /* Invalid or absent Snap URL: never infer that the session is inactive. */ }

    if(!snapToken) {
      return fail("midtrans_snap_session_token_missing",409,origin,
        "Midtrans belum menyediakan status transaksi dan token sesi Snap tidak tersedia. NekOS belum dapat memastikan checkout lama sudah tertutup.");
    }

    const cancelSessionResponse=await fetch(
      snapBase()+"/snap/v1/transactions/"+encodeURIComponent(snapToken)+"/cancel",
      {method:"POST",headers:{Authorization:authHeader(),"Accept":"application/json","Content-Type":"application/json"}}
    );
    const cancelSessionResult=await cancelSessionResponse.json().catch(()=>({}));
    const sessionCancelled=cancelSessionResponse.ok && Boolean(cancelSessionResult.canceled_at);
    const sessionAlreadyInactive=Array.isArray(cancelSessionResult.error_messages) &&
      cancelSessionResult.error_messages.some((message:unknown)=>
        /token already canceled|token not found/i.test(String(message)));
    const sessionInProgress=Array.isArray(cancelSessionResult.error_messages) &&
      cancelSessionResult.error_messages.some((message:unknown)=>
        /transaction is on progress/i.test(String(message)));
    snapSessionConfirmedInactive=sessionCancelled || sessionAlreadyInactive;
    snapSessionInProgress=sessionInProgress;

    if(!sessionCancelled && !sessionAlreadyInactive && !sessionInProgress) {
      console.error("skin_store_midtrans_snap_cancel_not_confirmed",JSON.stringify({
        order_id:orderId,http_status:cancelSessionResponse.status,
        error_messages:cancelSessionResult.error_messages??[]
      }));
      return fail("midtrans_snap_cancel_not_confirmed",409,origin,
        "Midtrans belum mengonfirmasi pembatalan sesi checkout lama. Belum ada pembayaran baru yang dibuat.");
    }

    response=await fetchStatus();
    if(response.status===404) {
      if(sessionCancelled || sessionAlreadyInactive) {
        await rpc("skin_update_payment_status",{p_provider:"midtrans",p_provider_order_id:providerOrderId,
          p_status:"cancel",p_payload:{reason:"snap_session_cancelled",cancel_response:cancelSessionResult}});
        return json({success:true,cancelled:true,checkout_active:false,
          message:"Sesi checkout Snap lama sudah dibatalkan oleh Midtrans. Kamu bisa memilih metode pembayaran lain."},200,origin);
      }
      return fail("midtrans_snap_session_in_progress",409,origin,
        "Midtrans melaporkan transaksi sedang diproses. NekOS belum akan membuat pembayaran pengganti.");
    }
  }
  if(!response.ok)return fail("midtrans_status_check_failed",503,origin);
  let verified=await response.json();
  if(String(verified.status_code??"")==="404") {
    if(snapSessionConfirmedInactive) {
      await rpc("skin_update_payment_status",{p_provider:"midtrans",p_provider_order_id:providerOrderId,
        p_status:"cancel",p_payload:{reason:"snap_session_cancelled",provider_response:verified}});
      return json({success:true,cancelled:true,checkout_active:false,
        message:"Sesi checkout Snap lama sudah dibatalkan oleh Midtrans. Kamu bisa memilih metode pembayaran lain."},200,origin);
    }
    if(snapSessionInProgress) return fail("midtrans_snap_session_in_progress",409,origin,
      "Midtrans melaporkan transaksi sedang diproses. NekOS belum akan membuat pembayaran pengganti.");
    return json({success:true,cancelled:false,checkout_active:true,can_close_checkout:true,
      message:"Sesi Snap belum dapat dinyatakan tertutup oleh Midtrans. NekOS belum akan membuat pembayaran pengganti."},200,origin);
  }
  if(!["200","201"].includes(String(verified.status_code??""))||
     String(verified.order_id??"")!==providerOrderId||
     String(verified.currency??"").toUpperCase()!=="IDR"||
     Number(verified.gross_amount)!==Number(status.amount)) {
    return fail("midtrans_transaction_mismatch",502,origin);
  }
  let transactionStatus=String(verified.transaction_status??"").toLowerCase();
  const fraud=String(verified.fraud_status??"accept").toLowerCase();
  const markPaid=async(payload:Record<string,unknown>)=>{
    const granted=await rpc("skin_mark_paid",{
      p_provider:"midtrans",p_provider_order_id:providerOrderId,
      p_provider_payment_id:String(payload.transaction_id??""),p_payload:payload
    });
    if(!granted)throw new Error("entitlement_grant_failed");
  };
  if(transactionStatus==="settlement"||(transactionStatus==="capture"&&fraud==="accept")) {
    await markPaid(verified);
    return json({success:true,paid:true,cancelled:false,message:"Pembayaran sudah berhasil. Skin tidak dibatalkan dan akan dipasang."},200,origin);
  }
  if(["cancel","expire","deny"].includes(transactionStatus)) {
    await rpc("skin_update_payment_status",{p_provider:"midtrans",p_provider_order_id:providerOrderId,
      p_status:transactionStatus,p_payload:verified});
    return json({success:true,cancelled:true,checkout_active:false,
      message:"Transaksi Midtrans sudah tidak aktif. Kamu bisa membeli skin ini kembali."},200,origin);
  }
  if(!["pending","authorize","capture"].includes(transactionStatus)) {
    return fail("midtrans_transaction_not_cancellable",409,origin);
  }
  const cancelResponse=await fetch(midtransBase()+"/v2/"+encodeURIComponent(providerOrderId)+"/cancel",{
    method:"POST",
    headers:{Authorization:authHeader(),"Accept":"application/json","Content-Type":"application/json"}
  });
  let cancelResult:Record<string,unknown>={};
  if(cancelResponse.ok)cancelResult=await cancelResponse.json();
  response=await fetchStatus();
  if(!response.ok)return fail("midtrans_cancel_verification_failed",503,origin);
  verified=await response.json();
  if(!["200","201"].includes(String(verified.status_code??""))||
     String(verified.order_id??"")!==providerOrderId||
     Number(verified.gross_amount)!==Number(status.amount)||
     String(verified.currency??"").toUpperCase()!=="IDR") {
    return fail("midtrans_cancel_verification_failed",503,origin);
  }
  transactionStatus=String(verified.transaction_status??"").toLowerCase();
  const verifiedFraud=String(verified.fraud_status??"accept").toLowerCase();
  if(transactionStatus==="settlement"||(transactionStatus==="capture"&&verifiedFraud==="accept")) {
    await markPaid(verified);
    return json({success:true,paid:true,cancelled:false,message:"Pembayaran berhasil tepat saat pembatalan diproses. Skin akan tetap dipasang."},200,origin);
  }
  if(transactionStatus!=="cancel") {
    console.error("skin_store_midtrans_cancel_not_confirmed",JSON.stringify({
      order_id:orderId,http_status:cancelResponse.status,
      cancel_status_code:String(cancelResult.status_code??""),
      transaction_status:transactionStatus
    }));
    return fail("midtrans_cancel_not_confirmed",409,origin);
  }
  await rpc("skin_update_payment_status",{p_provider:"midtrans",p_provider_order_id:providerOrderId,
    p_status:"cancel",p_payload:verified});
  return json({success:true,cancelled:true,checkout_active:false,
    message:"Pembelian berhasil dibatalkan. Kamu bisa membeli skin ini kembali."},200,origin);
}


async function handlePaymentSwitch(body:Record<string,unknown>, deviceHash:string, origin:string) {
  const orderId=String(body.order_id??"").trim();
  const requestedProvider=String(body.provider??"").trim().toLowerCase();
  if(!orderId)return fail("invalid_order_id",400,origin);
  if(!["midtrans","paypal"].includes(requestedProvider))return fail("unsupported_payment_provider",400,origin);

  const status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
  if(!status)return fail("order_not_found",404,origin);
  if(status.owned||status.status==="paid") {
    return json({success:true,paid:true,owned:true,order_id:orderId,
      message:"Pembayaran sudah berhasil. Skin tetap menjadi milikmu."},200,origin);
  }

  const active=status.active_payment??null;
  if(active && String(active.provider)===requestedProvider && active.checkout_url) {
    return json({success:true,free:false,checkout_url:String(active.checkout_url),
      order_id:orderId,provider:requestedProvider,
      provider_order_id:String(active.provider_order_id??""),reused:true},200,origin);
  }
  if(active && !["midtrans","paypal"].includes(String(active.provider))) {
    return fail("unsupported_active_payment_provider",409,origin,
      "Gateway pada transaksi lama tidak dikenali. NekOS tidak akan membuat pembayaran baru sebelum statusnya direkonsiliasi.");
  }

  if(active) {
    const reconciliation=await handleCancel({order_id:orderId},deviceHash,origin);
    const outcome=await reconciliation.clone().json().catch(()=>({}));
    if(!reconciliation.ok)return reconciliation;
    if(outcome.paid||outcome.owned) {
      return json({success:true,paid:true,owned:true,order_id:orderId,
        message:"Pembayaran lama ternyata sudah berhasil. Skin tidak perlu dibayar ulang."},200,origin);
    }
    if(outcome.checkout_active) {
      return fail("payment_switch_blocked",409,origin,String(outcome.message??
        "Transaksi lama masih aktif. NekOS belum dapat membuat pembayaran pengganti."));
    }
    if(!outcome.cancelled && outcome.checkout_active!==false) {
      return fail("payment_switch_not_reconciled",409,origin,
        "Status transaksi lama belum dapat dipastikan. Belum ada pembayaran baru yang dibuat.");
    }
  }

  const skinId=String(status.skin_id??"");
  const {data:skin,error:skinError}=await db.from("skins")
    .select("id,skin_key,name,price_amount,price_usd,currency,status")
    .eq("id",skinId).eq("status","published").maybeSingle();
  if(skinError||!skin)return fail("skin_not_found",404,origin);
  if(requestedProvider==="paypal") {
    assertPaymentConfig("paypal");
    if(!Number.isFinite(Number(skin.price_usd))||Number(skin.price_usd)<=0)
      return fail("paypal_price_not_configured",400,origin);
    const order=await rpc("skin_create_paypal_order",{
      p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:String(body.app_version??"").slice(0,32)||null
    });
    const newOrderId=String(order.order_id);
    const newActive=await rpc("skin_active_payment_attempt",{p_order_id:newOrderId});
    if(newActive) {
      if(String(newActive.provider)!=="paypal")
        return fail("active_payment_provider_mismatch",409,origin,
          "Masih ada transaksi aktif dari gateway lain. Tidak ada checkout baru yang dibuat.");
      if(newActive.checkout_url)return json({success:true,free:false,checkout_url:String(newActive.checkout_url),
        order_id:newOrderId,provider:"paypal",provider_order_id:String(newActive.provider_order_id??""),reused:true},200,origin);
    }
    const payment=await createPayPalPayment(order,newOrderId,skin);
    return json({success:true,free:false,...payment,reused:Boolean(order.reused)},201,origin);
  }

  assertPaymentConfig("midtrans");
  const order=await rpc("skin_create_order",{
    p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:String(body.app_version??"").slice(0,32)||null
  });
  const newOrderId=String(order.order_id);
  const newActive=await rpc("skin_active_payment_attempt",{p_order_id:newOrderId});
  if(newActive) {
    if(String(newActive.provider)!=="midtrans")
      return fail("active_payment_provider_mismatch",409,origin,
        "Masih ada transaksi aktif dari gateway lain. Tidak ada checkout baru yang dibuat.");
    if(newActive.checkout_url)return json({success:true,free:false,checkout_url:String(newActive.checkout_url),
      order_id:newOrderId,provider:"midtrans",reused:true},200,origin);
  }
  const payment=await createPayment(order,newOrderId,skin);
  return json({success:true,free:false,...payment,reused:Boolean(order.reused)},201,origin);
}

async function handleWebhook(req:Request) {
  try { assertPaymentConfig("midtrans"); } catch { return fail("midtrans_not_configured",503); }
  const body=await req.json();
  const orderId=String(body.order_id??"");
  const statusCode=String(body.status_code??"");
  const gross=String(body.gross_amount??"");
  const signature=String(body.signature_key??"");
  if(!orderId||!statusCode||!gross||!signature)return fail("invalid_notification",400);
  const expected=await sha512(orderId+statusCode+gross+MIDTRANS_SIGNATURE_SECRET);
  if(!constantEqual(signature.toLowerCase(),expected))return fail("invalid_signature",401);
  const statusResponse=await fetch(midtransBase()+"/v2/"+encodeURIComponent(orderId)+"/status",{
    headers:{Authorization:authHeader(),"Accept":"application/json"}
  });
  if(!statusResponse.ok)return fail("status_verification_failed",503);
  const verified=await statusResponse.json();
  if(String(verified.order_id)!==orderId||String(verified.currency)!=="IDR"||String(verified.gross_amount)!==gross) {
    return fail("transaction_mismatch",400);
  }
  const orderStatus=String(verified.transaction_status??"");
  const fraud=String(verified.fraud_status??"accept").toLowerCase();
  if((orderStatus==="settlement"||(orderStatus==="capture"&&fraud==="accept"))&&fraud==="accept") {
    const granted=await rpc("skin_mark_paid",{
      p_provider:"midtrans",p_provider_order_id:orderId,
      p_provider_payment_id:String(verified.transaction_id??""),p_payload:verified
    });
    if(!granted)return fail("entitlement_grant_failed",500);
  } else if(["expire","cancel","deny"].includes(orderStatus)) {
    await rpc("skin_update_payment_status",{
      p_provider:"midtrans",p_provider_order_id:orderId,p_status:orderStatus,p_payload:verified
    });
  }
  return json({success:true});
}
Deno.serve(async(req:Request)=>{
  if(!validOrigin(req))return fail("origin_not_allowed",403);
  const origin=req.headers.get("origin")??"";
  const path=new URL(req.url).pathname.split("/").filter(Boolean).pop()??"";
  if(req.method==="OPTIONS")return new Response(null,{status:204,headers:{
    ...(origin===ORIGIN?{"Access-Control-Allow-Origin":ORIGIN}:{}),
    "Access-Control-Allow-Headers":"content-type, apikey, authorization, x-client-info",
    "Access-Control-Allow-Methods":"GET, POST, OPTIONS","Vary":"Origin"
  }});
  try {
    if(path==="webhook"&&req.method==="POST")return await handleWebhook(req);
    if(path==="paypal-webhook"&&req.method==="POST")return await handlePayPalWebhook(req);
    if(req.method==="GET")return json({skins:await catalog(origin)},200,origin);
    if(req.method!=="POST")return fail("method_not_allowed",405,origin);
    const body=await req.json();
    const deviceHash=await hmacDevice(String(body.android_id??""));
    const appVersion=String(body.app_version??"").slice(0,32)||null;
    if(path==="catalog")return json({skins:await catalog(origin,deviceHash,appVersion)},200,origin);
    if(path==="cancel")return await handleCancel(body,deviceHash,origin);
    if(path==="switch")return await handlePaymentSwitch(body,deviceHash,origin);
    if(path==="capture")return await handlePayPalCapture(body,deviceHash,origin);
    if(path==="purchase") {
      const skinId=String(body.skin_id??"");
      const {data:skin,error:skinError}=await db.from("skins").select("id,skin_key,name,price_amount,price_usd,currency,status").eq("id",skinId).eq("status","published").maybeSingle();
      if(skinError||!skin)return fail("skin_not_found",404,origin);
      const existingAccess=await rpc("skin_download_info",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
      if(existingAccess) return json({success:true,free:true,owned:true,skin_id:skinId},200,origin);
      if(Number(skin.price_amount)===0) return fail("package_not_available",503,origin);
      if(String(skin.currency)!=="IDR"||!Number.isSafeInteger(Number(skin.price_amount)))return fail("unsupported_price_currency",400,origin);
      const provider=String(body.provider??"midtrans").trim().toLowerCase();
      if(!["midtrans","paypal"].includes(provider))return fail("unsupported_payment_provider",400,origin);
      if(provider==="paypal") {
        assertPaymentConfig("paypal");
        if(!Number.isFinite(Number(skin.price_usd)) || Number(skin.price_usd)<=0) return fail("paypal_price_not_configured",400,origin);
        const order=await rpc("skin_create_paypal_order",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
        const orderId=String(order.order_id);
        const active=await rpc("skin_active_payment_attempt",{p_order_id:orderId});
        if(active) {
          if(String(active.provider)!=="paypal") return fail("active_payment_provider_mismatch",409,origin);
          if(active.checkout_url) return json({success:true,free:false,checkout_url:String(active.checkout_url),
            order_id:orderId,provider:"paypal",provider_order_id:String(active.provider_order_id),reused:true},200,origin);
        }
        const payment=await createPayPalPayment(order,orderId,skin);
        return json({success:true,free:false,...payment,reused:Boolean(order.reused)},201,origin);
      }
      const order=await rpc("skin_create_order",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
      const orderId=String(order.order_id);
      const active=await rpc("skin_active_payment_attempt",{p_order_id:orderId});
      if(active) {
        if(String(active.provider)!==provider) {
          return fail("active_payment_provider_mismatch",409,origin,
            "Masih ada checkout aktif melalui "+String(active.provider)+". Selesaikan atau tunggu transaksi tersebut berakhir sebelum memilih gateway lain.");
        }
        if(active.checkout_url) {
          return json({success:true,free:false,checkout_url:String(active.checkout_url),
            order_id:orderId,provider,reused:true},200,origin);
        }
      }
      const payment=await createPayment(order,orderId,skin);
      return json({success:true,free:false,...payment,reused:Boolean(order.reused)},201,origin);
    }
    if(path==="status") {
      const orderId=String(body.order_id??"");
      let status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
      if(!status)return fail("order_not_found",404,origin);
      const activePayment=status.active_payment??null;
      if(status.status==="pending" && activePayment?.provider==="paypal") {
        assertPaymentConfig("paypal");
        const paypalOrderId=String(activePayment.provider_order_id??"");
        let remote=await paypalRequest("/v2/checkout/orders/"+encodeURIComponent(paypalOrderId));
        let providerStatus=String(remote.status??"UNKNOWN").toUpperCase();
        if(providerStatus==="APPROVED") {
          remote=await paypalRequest("/v2/checkout/orders/"+encodeURIComponent(paypalOrderId)+"/capture",{
            method:"POST",headers:{"PayPal-Request-Id":orderId+"-capture"},body:"{}"
          });
          providerStatus=String(remote.status??"UNKNOWN").toUpperCase();
        }
        console.info("skin_store_paypal_status",JSON.stringify({
          order_id:orderId,provider_status:providerStatus
        }));
        if(providerStatus==="COMPLETED") {
          await grantPayPalEntitlement(paypalOrderId,orderId,Number(status.amount));
          status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
        } else {
          status={...status,provider_status:providerStatus};
        }
      }
      if(status.status==="pending" && activePayment?.provider==="midtrans") {
        assertPaymentConfig("midtrans");
        const createdAt=Date.parse(String(activePayment.created_at??status.created_at??""));
        const expiryReached=Number.isFinite(createdAt) && Date.now()-createdAt>=SNAP_EXPIRY_RECONCILIATION_MS;
        const statusUrl=midtransBase()+"/v2/"+encodeURIComponent(orderId)+"/status";
        const verifiedResponse=await fetch(statusUrl,{
          headers:{Authorization:authHeader(),"Accept":"application/json"}
        });
        console.info("skin_store_status_response", JSON.stringify({
          order_id: orderId,
          http_status: verifiedResponse.status,
          expiry_reached: expiryReached
        }));
        if(verifiedResponse.status===404) {
          // A Snap page can exist without a Core API transaction. Cancel its token
          // after the configured expiry before marking the local attempt terminal.
          if(expiryReached && activePayment.checkout_url) {
            const reconciliation=await handleCancel({order_id:orderId},deviceHash,origin);
            const outcome=await reconciliation.clone().json().catch(()=>({}));
            if(!reconciliation.ok)return reconciliation;
            if(outcome.paid || outcome.cancelled) {
              status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
            }
          }
        } else if(!verifiedResponse.ok) {
          return fail("midtrans_status_check_failed",503,origin);
        } else {
          let verified=await verifiedResponse.json();
          const providerStatusCode=String(verified.status_code??"");
          // Midtrans can return an API-level 404 inside a successful HTTP 200 response.
          // This is the expected response when a Snap page was abandoned before a method was selected.
          if(providerStatusCode==="404") {
            console.info("skin_store_snap_transaction_not_created", JSON.stringify({
              order_id:orderId,
              expiry_reached:expiryReached,
              status_message:String(verified.status_message??"")
            }));
            if(expiryReached && activePayment.checkout_url) {
              const reconciliation=await handleCancel({order_id:orderId},deviceHash,origin);
              const outcome=await reconciliation.clone().json().catch(()=>({}));
              if(!reconciliation.ok)return reconciliation;
              if(outcome.paid || outcome.cancelled) {
                status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
              }
            }
          } else if(!["200","201"].includes(providerStatusCode)) {
            console.error("skin_store_midtrans_status_error", JSON.stringify({
              order_id:orderId,
              http_status:verifiedResponse.status,
              provider_status_code:providerStatusCode,
              status_message:String(verified.status_message??"")
            }));
            return fail("midtrans_status_check_failed",503,origin);
          } else {
            const localAmount=Number(status.amount);
            const remoteAmount=Number(verified.gross_amount);
            const remoteOrderId=String(verified.order_id??"").trim();
            const orderIdMatches=remoteOrderId===orderId;
            const amountMatches=Number.isFinite(localAmount) && Number.isFinite(remoteAmount) &&
              remoteAmount===localAmount;
            if(!orderIdMatches || !amountMatches) {
              console.error("skin_store_transaction_mismatch", JSON.stringify({
                order_id: orderId,
                remote_order_id: remoteOrderId,
                local_amount: localAmount,
                remote_amount: remoteAmount,
                remote_status: String(verified.transaction_status??"")
              }));
              return fail("midtrans_transaction_mismatch",502,origin);
            }
            let transactionStatus=String(verified.transaction_status??"");
            if(transactionStatus==="pending" && expiryReached) {
              const expireResponse=await fetch(midtransBase()+"/v2/"+encodeURIComponent(orderId)+"/expire",{
                method:"POST",
                headers:{Authorization:authHeader(),"Accept":"application/json","Content-Type":"application/json"}
              });
              if(!expireResponse.ok) {
                return fail(expireResponse.status===401||expireResponse.status===403
                  ?"midtrans_expire_permission_required":"midtrans_expire_failed",503,origin);
              }
              const expired=await expireResponse.json();
              if(String(expired.order_id??orderId)!==orderId)return fail("midtrans_transaction_mismatch",502,origin);
              verified=expired;
              transactionStatus=String(verified.transaction_status??"expire");
            }
            const fraud=String(verified.fraud_status??"accept").toLowerCase();
            const paid=transactionStatus==="settlement" ||
              (transactionStatus==="capture" && fraud==="accept");
            if(paid && fraud==="accept") {
              const granted=await rpc("skin_mark_paid",{p_provider:"midtrans",p_provider_order_id:orderId,
                p_provider_payment_id:String(verified.transaction_id??""),p_payload:verified});
              if(!granted)return fail("entitlement_grant_failed",500,origin);
            } else if(["expire","cancel","deny"].includes(transactionStatus)) {
              await rpc("skin_update_payment_status",{p_provider:"midtrans",p_provider_order_id:orderId,p_status:transactionStatus,p_payload:verified});
            }
            status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
          }
        }
      }
      return json(status,200,origin);
    }
    if(path==="download") {
      const skinId=String(body.skin_id??"");
      const info=await rpc("skin_download_info",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
      if(!info)return fail("purchase_required",403,origin);
      const {data,error}=await db.storage.from(String(info.package_bucket)).createSignedUrl(String(info.package_path),90,{download:true});
      if(error||!data?.signedUrl)return fail("download_unavailable",503,origin);
      return json({success:true,...info,download_url:data.signedUrl},200,origin);
    }
    return fail("unknown_action",404,origin);
  } catch(error) {
    const message=error instanceof Error?error.message:"internal_error";
    console.error("skin_store_request_failed", JSON.stringify({
      path,
      message,
      stack: error instanceof Error ? error.stack : undefined
    }));
    const status=["midtrans_not_configured","paypal_not_configured","paypal_webhook_not_configured"].includes(message)?503:
      message==="already_owned"?409:
      message==="skin_not_found"?404:400;
    return fail(message,status,origin);
  }
});