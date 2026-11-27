import { createClient } from "npm:@supabase/supabase-js@2.119.1";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const DEVICE_SECRET = Deno.env.get("SKIN_DEVICE_HMAC_SECRET") ?? "";
const MIDTRANS_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";
const MIDTRANS_SIGNATURE_SECRET = Deno.env.get("MIDTRANS_SIGNATURE_SECRET") ?? "";
const MIDTRANS_ENV = (Deno.env.get("MIDTRANS_ENVIRONMENT") ?? "").trim().toLowerCase();
const DUITKU_MERCHANT_CODE = Deno.env.get("DUITKU_MERCHANT_CODE") ?? "";
const DUITKU_API_KEY = Deno.env.get("DUITKU_API_KEY") ?? "";
const DUITKU_ENV = (Deno.env.get("DUITKU_ENVIRONMENT") ?? "sandbox").trim().toLowerCase();
const ORIGIN = "https://mazbayid.github.io";
const PREVIEWS = "skin-previews";
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
  if (provider === "duitku") {
    if (!DUITKU_MERCHANT_CODE || !DUITKU_API_KEY || !["production","sandbox"].includes(DUITKU_ENV)) {
      throw new Error("duitku_not_configured");
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
async function hmacSha256Hex(message:string, secret:string) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
    {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  return hex(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message))));
}
function duitkuBase() {
  return DUITKU_ENV === "production" ? "https://api-prod.duitku.com" : "https://api-sandbox.duitku.com";
}
async function rpc(name:string,args:Record<string,unknown>) {
  const {data,error}=await db.rpc(name,args);
  if(error) throw new Error(error.message);
  return data;
}
async function catalog(origin:string) {
  const {data,error}=await db.from("skins")
    .select("id,skin_key,name,author,description,schema_version,min_nekos_version,preview_path,price_amount,currency,package_sha256,package_size_bytes")
    .eq("status","published").order("created_at",{ascending:false}).limit(100);
  if(error)throw new Error("catalog_unavailable");
  return (data??[]).map((skin:Record<string,unknown>)=>({
    ...skin,
    preview_url:db.storage.from(PREVIEWS).getPublicUrl(String(skin.preview_path)).data.publicUrl,
  }));
}
async function createPayment(
  order:Record<string,unknown>, orderId:string, skin:Record<string,unknown>,
  provider:string, email:string
) {
  const amount = Number(order.amount);
  if (!Number.isSafeInteger(amount) || amount < 1) throw new Error("invalid_order_amount");
  assertPaymentConfig(provider);
  if (provider === "duitku" && (!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email) || email.length > 255)) {
    throw new Error("invalid_duitku_email");
  }
  await rpc("skin_attach_payment", {
    p_order_id:orderId, p_provider_order_id:orderId, p_checkout_url:null, p_provider:provider
  });

  if (provider === "midtrans") {
    assertPaymentConfig("midtrans");
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
        page_expiry:{unit:"minute",duration:15},
        expiry:{
          start_time:new Date(Date.now()+7*60*60*1000).toISOString().replace("T"," ").slice(0,19)+" +0700",
          unit:"minute",duration:15
        }
      })
    });
    const data=await response.json();
    if(!response.ok||!data.redirect_url)throw new Error("midtrans_checkout_creation_failed");
    await rpc("skin_attach_payment",{p_order_id:orderId,p_provider_order_id:orderId,p_checkout_url:data.redirect_url,p_provider:"midtrans"});
    return {checkout_url:data.redirect_url,token:data.token,order_id:orderId,provider:"midtrans"};
  }

  if (provider === "duitku") {
    assertPaymentConfig("duitku");
    if (!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email) || email.length > 255) {
      throw new Error("invalid_duitku_email");
    }
    const timestamp = String(Date.now());
    const signature = await hmacSha256Hex(DUITKU_MERCHANT_CODE + timestamp, DUITKU_API_KEY);
    const response = await fetch(duitkuBase()+"/api/merchant/createInvoice", {
      method:"POST",
      headers:{
        "Content-Type":"application/json",
        "x-duitku-merchantcode":DUITKU_MERCHANT_CODE,
        "x-duitku-timestamp":timestamp,
        "x-duitku-signature":signature
      },
      body:JSON.stringify({
        paymentAmount:amount,
        merchantOrderId:orderId,
        productDetails:"NekOS Skin - "+String(skin.name).slice(0,200),
        email,
        customerVaName:"NekOS User",
        callbackUrl:SUPABASE_URL+"/functions/v1/skin-store/duitku-callback",
        returnUrl:"https://mazbayid.github.io/skin-payment-return/",
        expiryPeriod:60,
        itemDetails:[{name:String(skin.name).slice(0,50),price:amount,quantity:1}]
      })
    });
    const data=await response.json();
    if(!response.ok||String(data.statusCode)!=="00"||!data.paymentUrl||!data.reference) {
      console.error("skin_store_duitku_invoice_failed",JSON.stringify({http_status:response.status,status_code:data.statusCode,status_message:data.statusMessage}));
      throw new Error("duitku_checkout_creation_failed");
    }
    const paymentUrl=String(data.paymentUrl);
    const parsed=new URL(paymentUrl);
    const allowedHosts=DUITKU_ENV==="production"?["app-prod.duitku.com"]:["app-sandbox.duitku.com"];
    if(parsed.protocol!=="https:"||!allowedHosts.includes(parsed.hostname))throw new Error("invalid_duitku_checkout_url");
    await rpc("skin_attach_payment",{
      p_order_id:orderId,p_provider_order_id:orderId,p_checkout_url:paymentUrl,p_provider:"duitku"
    });
    return {checkout_url:paymentUrl,order_id:orderId,provider:"duitku",provider_reference:String(data.reference)};
  }
  throw new Error("unsupported_payment_provider");
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
async function handleDuitkuCallback(req:Request) {
  try { assertPaymentConfig("duitku"); } catch { return fail("duitku_not_configured",503); }
  const form=await req.formData();
  const field=(name:string)=>String(form.get(name)??"");
  const merchantCode=field("merchantCode");
  const amount=field("amount");
  const orderId=field("merchantOrderId");
  const reference=field("reference");
  const resultCode=field("resultCode");
  const signature=field("signature");
  if(!merchantCode||!amount||!orderId||!resultCode||!signature)return fail("invalid_duitku_callback",400);
  if(merchantCode!==DUITKU_MERCHANT_CODE)return fail("invalid_duitku_merchant",401);
  if(!/^\\d+$/.test(amount))return fail("invalid_duitku_amount",400);
  const expected=await hmacSha256Hex(merchantCode+amount+orderId,DUITKU_API_KEY);
  if(!constantEqual(signature.toLowerCase(),expected.toLowerCase()))return fail("invalid_duitku_signature",401);
  // Persist only payment verification fields; never retain the customer's email.
  const payload={
    gross_amount:amount,
    currency:"IDR",
    merchantCode,
    merchantOrderId:orderId,
    reference,
    resultCode,
    paymentCode:field("paymentCode"),
    publisherOrderId:field("publisherOrderId"),
    settlementDate:field("settlementDate"),
    transactionState:field("transactionState"),
    transactionStateStatus:field("transactionStateStatus")
  };
  if(resultCode==="00") {
    const granted=await rpc("skin_mark_paid",{
      p_provider:"duitku",p_provider_order_id:orderId,
      p_provider_payment_id:reference||null,p_payload:payload
    });
    if(!granted)return fail("duitku_entitlement_grant_failed",500);
  } else if(resultCode==="02"||resultCode==="01") {
    await rpc("skin_update_payment_status",{
      p_provider:"duitku",p_provider_order_id:orderId,p_status:"02",p_payload:payload
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
    if(path==="duitku-callback"&&req.method==="POST")return await handleDuitkuCallback(req);
    if(req.method==="GET")return json({skins:await catalog(origin)},200,origin);
    if(req.method!=="POST")return fail("method_not_allowed",405,origin);
    const body=await req.json();
    const deviceHash=await hmacDevice(String(body.android_id??""));
    const appVersion=String(body.app_version??"").slice(0,32)||null;
    if(path==="purchase") {
      const skinId=String(body.skin_id??"");
      const {data:skin,error:skinError}=await db.from("skins").select("id,skin_key,name,price_amount,currency,status").eq("id",skinId).eq("status","published").maybeSingle();
      if(skinError||!skin)return fail("skin_not_found",404,origin);
      const existingAccess=await rpc("skin_download_info",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
      if(existingAccess) return json({success:true,free:true,owned:true,skin_id:skinId},200,origin);
      if(Number(skin.price_amount)===0) return fail("package_not_available",503,origin);
      if(String(skin.currency)!=="IDR"||!Number.isSafeInteger(Number(skin.price_amount)))return fail("unsupported_price_currency",400,origin);
      const provider=String(body.provider??"midtrans").trim().toLowerCase();
      if(!["midtrans","duitku"].includes(provider))return fail("unsupported_payment_provider",400,origin);
      const email=String(body.email??"").trim();
      if(provider==="duitku"&&(!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)||email.length>255))
        return fail("invalid_duitku_email",400,origin);
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
      const payment=await createPayment(order,orderId,skin,provider,email);
      return json({success:true,free:false,...payment,reused:Boolean(order.reused)},201,origin);
    }
    if(path==="status") {
      const orderId=String(body.order_id??"");
      let status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
      if(!status)return fail("order_not_found",404,origin);
      const activePayment=status.active_payment??null;
      if(status.status==="pending" && activePayment?.provider==="midtrans") {
        assertPaymentConfig("midtrans");
        const createdAt=Date.parse(String(activePayment.created_at??status.created_at??""));
        const expiryReached=Number.isFinite(createdAt) && Date.now()-createdAt>=15*60*1000;
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
          // Snap has no Core API transaction until the customer selects a payment method.
          // The Snap page itself expires after 15 minutes, and does not send a webhook.
          if(expiryReached) {
            await rpc("skin_update_payment_status",{
              p_provider:"midtrans",p_provider_order_id:orderId,p_status:"expire",
              p_payload:{reason:"snap_page_expired_without_payment_method",expired_at:new Date().toISOString()}
            });
            status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
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
            if(expiryReached) {
              await rpc("skin_update_payment_status",{
                p_provider:"midtrans",p_provider_order_id:orderId,p_status:"expire",
                p_payload:{reason:"snap_page_expired_without_payment_method",provider_response:verified,
                  expired_at:new Date().toISOString()}
              });
              status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
            }
          } else if(providerStatusCode!=="200") {
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
    const status=message==="midtrans_not_configured"||message==="duitku_not_configured"?503:
      message==="already_owned"?409:
      message==="skin_not_found"?404:400;
    return fail(message,status,origin);
  }
});