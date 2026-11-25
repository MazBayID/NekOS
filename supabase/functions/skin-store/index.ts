import { createClient } from "npm:@supabase/supabase-js@2.119.1";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const DEVICE_SECRET = Deno.env.get("SKIN_DEVICE_HMAC_SECRET") ?? "";
const MIDTRANS_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";
const MIDTRANS_ENV = (Deno.env.get("MIDTRANS_ENVIRONMENT") ?? "sandbox").toLowerCase();
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
function midtransBase() {
  return MIDTRANS_ENV==="production" ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
}
function snapBase() {
  return MIDTRANS_ENV==="production" ? "https://app.midtrans.com" : "https://app.sandbox.midtrans.com";
}
function authHeader() { return "Basic "+btoa(MIDTRANS_KEY+":"); }
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
async function createPayment(order:Record<string,unknown>,orderId:string,skin:Record<string,unknown>) {
  if(!MIDTRANS_KEY)throw new Error("payment_provider_not_configured");
  const amount=Number(order.amount);
  if(!Number.isSafeInteger(amount)||amount<1)throw new Error("invalid_order_amount");
  const response=await fetch(snapBase()+"/snap/v1/transactions",{
    method:"POST",
    headers:{Authorization:authHeader(),"Content-Type":"application/json","Accept":"application/json"},
    body:JSON.stringify({
      transaction_details:{order_id:orderId,gross_amount:amount},
      item_details:[{id:String(skin.skin_key).slice(0,50),price:amount,quantity:1,name:String(skin.name).slice(0,50)}],
      callbacks:{
        finish:"https://mazbayid.github.io/skin-payment-return/",
        error:"https://mazbayid.github.io/skin-payment-return/",
        pending:"https://mazbayid.github.io/skin-payment-return/"
      },
      expiry:{unit:"minute",duration:30}
    })
  });
  const data=await response.json();
  if(!response.ok||!data.redirect_url)throw new Error("midtrans_checkout_creation_failed");
  await rpc("skin_attach_payment",{p_order_id:orderId,p_provider_order_id:orderId,p_checkout_url:data.redirect_url,p_provider:"midtrans"});
  return {checkout_url:data.redirect_url,token:data.token,order_id:orderId};
}
async function handleWebhook(req:Request) {
  if(!MIDTRANS_KEY)return fail("payment_provider_not_configured",503);
  const body=await req.json();
  const orderId=String(body.order_id??"");
  const statusCode=String(body.status_code??"");
  const gross=String(body.gross_amount??"");
  const signature=String(body.signature_key??"");
  if(!orderId||!statusCode||!gross||!signature)return fail("invalid_notification",400);
  const expected=await sha512(orderId+statusCode+gross+MIDTRANS_KEY);
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
    const paymentId=String(verified.transaction_id??"");
    const orderUuid=orderId;
    const granted=await rpc("skin_mark_paid",{
      p_order_id:orderUuid,p_provider_order_id:orderId,
      p_provider_payment_id:paymentId,p_payload:verified
    });
    if(!granted)return fail("entitlement_grant_failed",500);
  } else if(["expire","cancel","deny"].includes(orderStatus)) {
    await rpc("skin_update_payment_status",{p_order_id:orderId,p_status:orderStatus,p_payload:verified});
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
    if(req.method==="GET")return json({skins:await catalog(origin)},200,origin);
    if(req.method!=="POST")return fail("method_not_allowed",405,origin);
    const body=await req.json();
    const deviceHash=await hmacDevice(String(body.android_id??""));
    const appVersion=String(body.app_version??"").slice(0,32)||null;
    if(path==="purchase") {
      const skinId=String(body.skin_id??"");
      const {data:skin,error:skinError}=await db.from("skins").select("id,skin_key,name,price_amount,currency,status").eq("id",skinId).eq("status","published").maybeSingle();
      if(skinError||!skin)return fail("skin_not_found",404,origin);
      if(Number(skin.price_amount)===0) {
        await rpc("skin_download_info",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
        return json({success:true,free:true,skin_id:skinId},200,origin);
      }
      if(String(skin.currency)!=="IDR"||!Number.isSafeInteger(Number(skin.price_amount)))return fail("unsupported_price_currency",400,origin);
      const order=await rpc("skin_create_order",{p_device_hash:deviceHash,p_skin_id:skinId,p_app_version:appVersion});
      const payment=await createPayment(order,String(order.order_id),skin);
      return json({success:true,free:false,...payment},201,origin);
    }
    if(path==="status") {
      const orderId=String(body.order_id??"");
      let status=await rpc("skin_order_status",{p_order_id:orderId,p_device_hash:deviceHash});
      if(!status)return fail("order_not_found",404,origin);
      if(status.status==="pending" && MIDTRANS_KEY) {
        const verifiedResponse=await fetch(midtransBase()+"/v2/"+encodeURIComponent(orderId)+"/status",{
          headers:{Authorization:authHeader(),"Accept":"application/json"}
        });
        if(verifiedResponse.ok) {
          const verified=await verifiedResponse.json();
          if(String(verified.order_id)===orderId && String(verified.currency)==="IDR") {
            const paid=verified.transaction_status==="settlement" ||
              (verified.transaction_status==="capture" && String(verified.fraud_status??"accept").toLowerCase()==="accept");
            if(paid && String(verified.fraud_status??"accept").toLowerCase()==="accept") {
              await rpc("skin_mark_paid",{p_order_id:orderId,p_provider_order_id:orderId,
                p_provider_payment_id:String(verified.transaction_id??""),p_payload:verified});
            } else if(["expire","cancel","deny"].includes(String(verified.transaction_status))) {
              await rpc("skin_update_payment_status",{p_order_id:orderId,p_status:String(verified.transaction_status),p_payload:verified});
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
    const status=message==="payment_provider_not_configured"?503:
      message==="already_owned"?409:
      message==="skin_not_found"?404:400;
    return fail(message,status,origin);
  }
});