#!/usr/bin/env python3
"""Map package names to NekOS icon-pack entries.

Input accepts one package per line or CSV rows: package.name[,App Name].
Quoted CSV names may contain commas. Exact mappings always win, then keyword
rules, then the configured default glyph.
"""
import argparse
import csv
import json
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")


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


def read_packages(path):
    with open(path, encoding="utf-8-sig", newline="") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            try:
                row = next(csv.reader([line], skipinitialspace=True))
            except csv.Error as error:
                raise ValueError(f"Baris {line_number}: CSV tidak valid: {error}") from error
            if not row:
                continue
            package = row[0].strip()
            name = ",".join(row[1:]).strip()
            if not PACKAGE_RE.fullmatch(package):
                raise ValueError(f"Baris {line_number}: package name tidak valid: {package!r}")
            yield package, name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", help="File teks/CSV berisi package name")
    parser.add_argument("-o", "--output", default="extra_map.json")
    args = parser.parse_args()

    base = load_json(os.path.join(ROOT, "app_icon_map.json"))
    exact = {item["package"]: item for item in base["apps"]}
    rules = load_json(os.path.join(HERE, "keyword_rules.json"))
    compiled = [(re.compile(pattern, re.I), glyph) for pattern, glyph in rules["rules"]]

    output = []
    stats = {"exact": 0, "rule": 0, "default": 0}
    seen = set()
    for package, name in read_packages(args.input):
        if package in seen:
            continue
        seen.add(package)
        if package in exact:
            entry = dict(exact[package])
            entry["source"] = "exact"
            stats["exact"] += 1
        else:
            haystack = f"{package} {name}".lower()
            glyph, source = rules["default"], "default"
            for pattern, candidate in compiled:
                if pattern.search(haystack):
                    glyph, source = candidate, "rule"
                    break
            entry = {
                "package": package,
                "name": name or package.rsplit(".", 1)[-1],
                "glyph": glyph,
                "kind": "glyph",
                "icon": None,
                "source": source,
            }
            stats[source] += 1
        output.append(entry)

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
