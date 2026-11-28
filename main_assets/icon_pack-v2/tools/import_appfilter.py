#!/usr/bin/env python3
"""Impor appfilter.xml (standar ADW/Nova, dipakai hampir semua icon pack) ke peta NekOS.

appfilter.xml dari icon pack open-source (mis. Lawnicons, Apache-2.0) memuat ribuan package name
asli beserta nama aplikasinya. Skrip ini mengambil package + nama, lalu menentukan glyph NekOS:
  1) app_icon_map.json (pasti)   2) keyword_rules.json (tebakan, bertanda "rule")   3) default (paw)

Pemakaian:
  python3 tools/import_appfilter.py appfilter.xml -o extra_map.json
  python3 tools/import_appfilter.py appfilter.xml --merge      # gabung langsung ke app_icon_map.json
"""
import argparse, json, os, re, sys
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
CI = re.compile(r"ComponentInfo\{([^/}]+)/")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("appfilter"); ap.add_argument("-o", "--output", default="extra_map.json")
    ap.add_argument("--merge", action="store_true", help="gabungkan ke app_icon_map.json (entri lama tidak ditimpa)")
    a = ap.parse_args()
    mp = os.path.join(ROOT, "app_icon_map.json"); base = json.load(open(mp, encoding="utf-8"))
    exact = {x["package"]: x for x in base["apps"]}
    rules = json.load(open(os.path.join(HERE, "keyword_rules.json"), encoding="utf-8"))
    comp = [(re.compile(p, re.I), g) for p, g in rules["rules"]]
    out, seen, stat = [], set(), {"exact": 0, "rule": 0, "default": 0}
    for it in ET.parse(a.appfilter).getroot().iter("item"):
        m = CI.search(it.get("component", ""))
        if not m: continue
        pkg = m.group(1)
        if pkg in seen: continue
        seen.add(pkg)
        name = it.get("name") or it.get("drawable") or pkg.split(".")[-1]
        if pkg in exact:
            e = dict(exact[pkg]); e["source"] = "exact"
        else:
            hay = " ".join([pkg, name, it.get("drawable", "")]).lower(); g, src = rules["default"], "default"
            for rx, gl in comp:
                if rx.search(hay): g, src = gl, "rule"; break
            e = {"package": pkg, "name": name, "glyph": g, "source": src}
        stat[e["source"]] += 1; out.append(e)
    if a.merge:
        have = set(exact)
        new = [e for e in out if e["package"] not in have]
        for e in new: e.pop("source", None); base["apps"].append(e)
        base["total_apps"] = len(base["apps"]); json.dump(base, open(mp, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        print(f"{len(new)} aplikasi baru digabung -> total {base['total_apps']}  {stat}")
    else:
        json.dump({"schema_version": 1, "default_glyph": rules["default"], "apps": out}, open(a.output, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        print(f"{len(out)} aplikasi -> {a.output}  {stat}")

if __name__ == "__main__":
    sys.exit(main())
