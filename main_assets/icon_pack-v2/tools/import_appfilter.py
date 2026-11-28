#!/usr/bin/env python3
"""Import package mappings from standard ADW/Nova appfilter.xml files.

Unmapped packages are stored as glyph-only entries (kind="glyph", icon=null).
The NekOS runtime resolves those entries from the glyph catalog; the runner
does not fabricate app-specific image files.
"""
import argparse
import json
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
COMPONENT_RE = re.compile(r"ComponentInfo\{([^/}]+)/")


def load_json(path):
    with open(path, encoding="utf-8") as source:
        return json.load(source)


def write_json(path, data):
    target = os.path.abspath(path)
    directory = os.path.dirname(target)
    os.makedirs(directory, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".nekos-icon-map-", suffix=".tmp", dir=directory)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            json.dump(data, output, indent=2, ensure_ascii=False)
            output.write("\n")
        os.replace(temporary, target)
    except Exception:
        try:
            os.unlink(temporary)
        except OSError:
            pass
        raise


def load_rules():
    rules = load_json(os.path.join(HERE, "keyword_rules.json"))
    compiled = [(re.compile(pattern, re.I), glyph) for pattern, glyph in rules["rules"]]
    glyph_dir = os.path.join(ROOT, "icons", "apps")
    required = {rules["default"], *(glyph for _, glyph in rules["rules"])}
    missing = sorted(glyph for glyph in required if not os.path.isfile(
        os.path.join(glyph_dir, f"icon_{glyph}.webp")
    ))
    if missing:
        raise ValueError("Glyph tidak ditemukan: " + ", ".join(missing))
    return rules, compiled


def recount_metadata(base):
    apps = base["apps"]
    base["schema_version"] = 2
    base["total_apps"] = len(apps)
    base["glyphs_used"] = len({item["glyph"] for item in apps if item.get("glyph")})
    base["icons_bespoke"] = sum(item.get("kind") == "bespoke" for item in apps)
    base["icons_monogram"] = sum(item.get("kind") == "monogram" for item in apps)
    return base


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("appfilter", help="Path to appfilter.xml")
    parser.add_argument("-o", "--output", default="extra_map.json")
    parser.add_argument(
        "--merge",
        action="store_true",
        help="gabungkan package baru sebagai entri glyph-only; entri lama tidak ditimpa",
    )
    args = parser.parse_args()

    map_path = os.path.join(ROOT, "app_icon_map.json")
    base = load_json(map_path)
    exact = {item["package"]: item for item in base["apps"]}
    rules, compiled = load_rules()

    try:
        root = ET.parse(args.appfilter).getroot()
    except ET.ParseError as error:
        raise ValueError(f"appfilter.xml tidak valid: {error}") from error

    output = []
    seen = set()
    stats = {"exact": 0, "rule": 0, "default": 0, "invalid": 0}
    for item in root.iter():
        if item.tag.rsplit("}", 1)[-1] != "item":
            continue
        match = COMPONENT_RE.search(item.get("component", ""))
        if not match:
            continue
        package = match.group(1).strip()
        if not PACKAGE_RE.fullmatch(package):
            stats["invalid"] += 1
            continue
        if package in seen:
            continue
        seen.add(package)
        name = (item.get("name") or item.get("drawable") or package.rsplit(".", 1)[-1]).strip()

        if package in exact:
            entry = dict(exact[package])
            entry["source"] = "exact"
        else:
            haystack = " ".join((package, name, item.get("drawable", ""))).lower()
            glyph, source = rules["default"], "default"
            for pattern, candidate in compiled:
                if pattern.search(haystack):
                    glyph, source = candidate, "rule"
                    break
            entry = {
                "package": package,
                "name": name,
                "glyph": glyph,
                "kind": "glyph",
                "icon": None,
                "source": source,
            }
        stats[entry["source"]] += 1
        output.append(entry)

    if args.merge:
        existing = set(exact)
        new_entries = []
        for entry in output:
            if entry["package"] in existing:
                continue
            clean = {key: value for key, value in entry.items() if key != "source"}
            new_entries.append(clean)
            existing.add(entry["package"])
        base["apps"].extend(new_entries)
        recount_metadata(base)
        write_json(map_path, base)
        print(
            f"{len(new_entries)} aplikasi baru digabung -> total {base['total_apps']}  "
            f"{stats}"
        )
    else:
        write_json(args.output, {
            "schema_version": 2,
            "default_glyph": rules["default"],
            "apps": output,
        })
        print(f"{len(output)} aplikasi -> {args.output}  {stats}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(2)
