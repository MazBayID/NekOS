#!/usr/bin/env python3
"""Petakan daftar package name ke glyph NekOS.

  python3 tools/map_packages.py packages.txt -o extra_map.json

packages.txt: satu baris per aplikasi, "package.name" atau "package.name,Nama Aplikasi".
Daftar dari perangkat:  adb shell pm list packages -3 | sed 's/package://' > packages.txt
Urutan: 0) ikon merek (brand) bila ada / label cocok brand_index.json  1) app_icon_map.json (exact)  2) prefix_rules (awalan terpanjang)  3) keyword_rules  4) default (paw).
Hasil diberi "source": exact | prefix | rule | default agar mudah ditinjau. Hanya "exact" yang dianggap pasti.
"""
import argparse, json, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def load(root=ROOT):
    exact = {x["package"]: x for x in json.load(open(os.path.join(root, "app_icon_map.json")))["apps"]}
    r = json.load(open(os.path.join(root, "app_icon_rules.json")))
    prefixes = sorted(r["prefix_rules"], key=lambda t: -len(t[0]))
    comp = [(re.compile(p, re.I), g) for p, g in r["keyword_rules"]]
    bi = json.load(open(os.path.join(root, "brand_index.json")))
    return exact, prefixes, comp, r["default"], (set(bi["slugs"]), bi["aliases"])

def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("+", "plus").replace("&", "and"))

def brand_for(name, tables):
    slugs, aliases = tables[4]
    n = norm(re.sub(r"\(.*?\)", "", name))
    n = aliases.get(n, n)
    return n if n in slugs else None

def resolve(pkg, name, tables):
    exact, prefixes, comp, default = tables[:4]
    if pkg in exact: return exact[pkg]["glyph"], "exact"
    for pre, g in prefixes:
        if pkg.startswith(pre): return g, "prefix"
    hay = (pkg + " " + name).lower()
    for rx, g in comp:
        if rx.search(hay): return g, "rule"
    return default, "default"

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("input"); ap.add_argument("-o", "--output", default="extra_map.json")
    a = ap.parse_args(); t = load(); out, stat, seen = [], {"exact": 0, "prefix": 0, "rule": 0, "default": 0, "brand": 0}, set()
    for line in open(a.input, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"): continue
        pkg, _, name = line.partition(","); pkg, name = pkg.strip(), name.strip()
        if pkg in seen: continue
        seen.add(pkg); g, src = resolve(pkg, name, t); stat[src] += 1
        e = {"package": pkg, "name": name or pkg.split(".")[-1], "glyph": g, "source": src}
        b = t[0][pkg].get("brand") if pkg in t[0] else (brand_for(name, t) if name else None)
        if b: e["brand"] = b; stat["brand"] += 1
        out.append(e)
    json.dump({"schema_version": 3, "default_glyph": t[3], "apps": out}, open(a.output, "w"), indent=1, ensure_ascii=False)
    print(f"{len(out)} aplikasi -> {a.output}  {stat}")

if __name__ == "__main__":
    sys.exit(main())
