#!/usr/bin/env python3
"""Petakan daftar package name ke glyph NekOS.

Pemakaian:
  python3 tools/map_packages.py packages.txt -o extra_map.json

packages.txt: satu baris per aplikasi, "package.name" atau "package.name,Nama Aplikasi".
Urutan pencocokan: 1) app_icon_map.json (pasti)  2) keyword_rules.json (tebakan)  3) default (paw).
Entri hasil tebakan diberi "source": "rule" agar mudah ditinjau ulang.
"""
import argparse, json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input"); ap.add_argument("-o", "--output", default="extra_map.json")
    a = ap.parse_args()
    exact = {x["package"]: x for x in json.load(open(os.path.join(ROOT, "app_icon_map.json")))["apps"]}
    rules = json.load(open(os.path.join(HERE, "keyword_rules.json")))
    comp = [(re.compile(p, re.I), g) for p, g in rules["rules"]]
    out, stat = [], {"exact": 0, "rule": 0, "default": 0}
    seen = set()
    for line in open(a.input, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"): continue
        pkg, _, name = line.partition(",")
        pkg, name = pkg.strip(), name.strip()
        if pkg in seen: continue
        seen.add(pkg)
        if pkg in exact:
            e = dict(exact[pkg]); e["source"] = "exact"; stat["exact"] += 1
        else:
            hay = (pkg + " " + name).lower(); glyph, src = rules["default"], "default"
            for rx, g in comp:
                if rx.search(hay): glyph, src = g, "rule"; break
            e = {"package": pkg, "name": name or pkg.split(".")[-1], "glyph": glyph, "source": src}; stat[src] += 1
        out.append(e)
    json.dump({"schema_version": 1, "default_glyph": rules["default"], "apps": out}, open(a.output, "w"), indent=1, ensure_ascii=False)
    print(f"{len(out)} aplikasi -> {a.output}  {stat}")

if __name__ == "__main__":
    sys.exit(main())
