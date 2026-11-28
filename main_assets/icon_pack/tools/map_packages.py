#!/usr/bin/env python3
"""Petakan daftar package Android ke glyph NekOS dan ikon merek bila cocok.

Contoh:
  python3 tools/map_packages.py packages.txt -o extra_map.json

packages.txt: satu baris per aplikasi, "package.name" atau "package.name,Nama Aplikasi".
Daftar dari perangkat:
  adb shell pm list packages -3 | sed 's/package://' > packages.txt

Prioritas:
- Package exact: glyph dan brand dari app_icon_map.json adalah otoritas.
- Package yang belum terdaftar: cari brand dari label aplikasi (exact setelah normalisasi).
- Glyph fallback package yang belum terdaftar: prefix terpanjang, keyword pertama yang cocok,
  lalu gunakan ikon asli Android jika tidak terklasifikasi.

Field "source" menjelaskan asal glyph fallback. Field "brand_source" menjelaskan asal ikon merek.
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def load(root=ROOT):
    app_map = read_json(os.path.join(root, "app_icon_map.json"))
    rules = read_json(os.path.join(root, "app_icon_rules.json"))
    brand_index = read_json(os.path.join(root, "brand_index.json"))

    exact = {item["package"]: item for item in app_map["apps"]}
    prefixes = sorted(rules["prefix_rules"], key=lambda item: -len(item[0]))
    keywords = [(re.compile(pattern, re.I), glyph)
                for pattern, glyph in rules["keyword_rules"]]
    brand_data = (set(brand_index["slugs"]), brand_index.get("aliases", {}))
    return exact, prefixes, keywords, brand_data


def normalize_label(value):
    value = value.casefold().replace("+", " plus ").replace("&", " and ")
    return re.sub(r"[^a-z0-9]", "", value)


def brand_for(name, tables):
    if not name:
        return None
    slugs, aliases = tables[3]
    # Parenthetical qualifiers such as "(Beta)" should not prevent a brand match.
    label = re.sub(r"\([^)]*\)", " ", name)
    normalized = normalize_label(label)
    slug = aliases.get(normalized, normalized)
    return slug if slug in slugs else None


def resolve_glyph(pkg, name, tables):
    exact, prefixes, keywords, _ = tables
    if pkg in exact:
        return exact[pkg]["glyph"], "exact"

    haystack = (pkg + " " + name).casefold()
    for pattern, glyph in keywords:
        if pattern.search(haystack):
            return glyph, "rule"

    for prefix, glyph in prefixes:
        if pkg.startswith(prefix):
            return glyph, "prefix"

    return None, "native"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", help="Daftar package, satu per baris")
    parser.add_argument("-o", "--output", default="extra_map.json")
    args = parser.parse_args()

    tables = load()
    exact = tables[0]
    output = []
    stats = {
        "exact": 0, "prefix": 0, "rule": 0, "native": 0,
        "brand_exact": 0, "brand_label": 0
    }
    seen = set()

    with open(args.input, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue

            package, _, name = line.partition(",")
            package = package.strip()
            name = name.strip()
            if not package or package in seen:
                continue
            seen.add(package)

            glyph, source = resolve_glyph(package, name, tables)
            if package in exact:
                brand = exact[package].get("brand")
                brand_source = "exact" if brand else None
            else:
                brand = brand_for(name, tables)
                brand_source = "label" if brand else None

            stats[source] += 1
            entry = {
                "package": package,
                "name": name or package.split(".")[-1],
                "source": source
            }
            if glyph:
                entry["glyph"] = glyph
            if brand:
                entry["brand"] = brand
                entry["brand_source"] = brand_source
                stats["brand_" + brand_source] += 1
            output.append(entry)

    result = {
        "schema_version": 3,
        "apps": output
    }
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    print(f"{len(output)} aplikasi -> {args.output}  {stats}")


if __name__ == "__main__":
    sys.exit(main())
