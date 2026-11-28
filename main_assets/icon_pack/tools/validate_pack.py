#!/usr/bin/env python3
"""Validasi paket ikon aplikasi NekOS.  python3 -I tools/validate_pack.py [root]   (butuh pillow, numpy)"""
import json, os, re, sys
import numpy as np
from PIL import Image
root = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
E, W = [], []
d = os.path.join(root, "icons/apps")
files = sorted(f for f in os.listdir(d) if f.endswith(".webp"))
for f in files:
    if not re.fullmatch(r"icon_[a-z0-9]+(_[a-z0-9]+)*\.webp", f): E.append(f"{f}: nama tidak valid")
    im = Image.open(os.path.join(d, f))
    if im.size != (96, 96) or im.mode != "RGBA": E.append(f"{f}: {im.size} {im.mode}, harus 96x96 RGBA"); continue
    a = np.array(im); op = a[..., 3] > 0
    if not op.any(): E.append(f"{f}: kosong"); continue
    if (a[..., :3][op] < 250).any(): E.append(f"{f}: bukan mask putih")
    ys, xs = np.where(a[..., 3] > 8)
    if xs.min() < 7 or ys.min() < 7 or xs.max() > 88 or ys.max() > 88: E.append(f"{f}: keluar live area 80x80")
    if os.path.getsize(os.path.join(d, f)) > 20000: W.append(f"{f}: > 20 KB")
glyphs = {f[5:-5] for f in files}
m = json.load(open(os.path.join(root, "app_icon_map.json")))
pk = [a["package"] for a in m["apps"]]
if len(pk) != len(set(pk)): E.append("package ganda di app_icon_map.json")
for a in m["apps"]:
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+", a["package"]): E.append(f"package tidak valid: {a['package']}")
    if a["glyph"] not in glyphs: E.append(f"{a['package']}: glyph '{a['glyph']}' tidak ada")
    if not a.get("name"): E.append(f"{a['package']}: tanpa nama")
bd = os.path.join(root, "icons/brands")
bfiles = {f[:-5] for f in os.listdir(bd) if f.endswith(".webp")}
bi = json.load(open(os.path.join(root, "brand_index.json")))
if set(bi["slugs"]) != bfiles: E.append("brand_index.json tidak sinkron dengan icons/brands")
for f in sorted(bfiles):
    im = Image.open(os.path.join(bd, f + ".webp"))
    if im.size != (96, 96) or im.mode != "RGBA": E.append(f"brands/{f}: {im.size} {im.mode}"); continue
    a = np.array(im); op = a[..., 3] > 0
    if not op.any(): E.append(f"brands/{f}: kosong"); continue
    if (a[..., :3][op] < 250).any(): E.append(f"brands/{f}: bukan mask putih")
    ys, xs = np.where(a[..., 3] > 32)
    if xs.min() < 8 or ys.min() < 8 or xs.max() > 88 or ys.max() > 88: E.append(f"brands/{f}: keluar live area")
for a in m["apps"]:
    if a.get("brand") and a["brand"] not in bfiles: E.append(f"{a['package']}: brand '{a['brand']}' tidak ada")
for k, v in bi["aliases"].items():
    if v not in bfiles: E.append(f"alias {k}: '{v}' tidak ada")
if m.get("apps_with_brand") != sum(1 for a in m["apps"] if a.get("brand")): E.append("apps_with_brand salah")
if m["total_apps"] != len(pk): E.append(f"total_apps {m['total_apps']} != {len(pk)}")
if m["total_glyphs"] != len(glyphs): E.append(f"total_glyphs {m['total_glyphs']} != {len(glyphs)}")
if m["glyphs_used"] != len({a['glyph'] for a in m['apps']}): E.append("glyphs_used salah")
if m["default_glyph"] not in glyphs: E.append("default_glyph tidak ada")
r = json.load(open(os.path.join(root, "app_icon_rules.json")))
for pre, g in r["prefix_rules"]:
    if g not in glyphs: E.append(f"prefix {pre}: glyph '{g}' tidak ada")
for rx, g in r["keyword_rules"]:
    try: re.compile(rx)
    except re.error as e: E.append(f"regex salah '{rx}': {e}")
    if g not in glyphs: E.append(f"keyword '{rx[:20]}': glyph '{g}' tidak ada")
ix = json.load(open(os.path.join(d, "icons_index.json")))
if {i["file"] for i in ix["items"]} != set(files): E.append("icons_index.json tidak sinkron dengan file")
if ix["total"] != len(ix["items"]): E.append("icons_index total salah")
for w in W: print("WARN ", w)
for e in E: print("ERROR", e)
print(f"{len(glyphs)} glyph, {len(bfiles)} ikon merek, {len(pk)} aplikasi: {len(E)} error, {len(W)} warning")
sys.exit(1 if E else 0)
