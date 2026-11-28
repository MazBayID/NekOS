#!/usr/bin/env python3
"""Normalize NekOS app icon catalog v2 and restore explicit installed-app mappings."""
import argparse
import json
import os
import re
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_PATH = ROOT / "app_icon_map.json"
PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
INVALID_LEGACY_PACKAGES = {"generic_card", "generic_alphabet"}
EXACT_MAPPINGS = (
    ("com.albiononline", "Albion Online", "gamepad"),
    ("name.antonsmirnov.android.arduinodroid2", "ArduinoDroid", "chip"),
    ("com.handycloset.android.eraser", "Background Eraser", "image"),
    ("com.fbiego.chronos", "Chronos", "alarm_clock"),
    ("com.pri.compass", "Compass", "compass"),
    ("com.zeroday.cyberprotocol", "Cyber Protocol", "terminal"),
    ("ru.iiec.cxxdroid", "Cxxdroid", "code"),
    ("com.delgeo.desygner", "Desygner", "palette"),
    ("com.esp_flash.esp_flash_app", "ESP32 Flash", "chip"),
    ("com.ovelin.guitartuna", "GuitarTuna", "music_note"),
    ("jp.ne.ibis.ibispaintx.app", "ibisPaint X", "palette"),
    ("com.google.android.apps.safetyhub", "Keselamatan", "shield"),
    ("com.mobilechess.gp", "Magic Chess: GO GO", "gamepad"),
    ("com.pytutor.baru", "PyTutor Indonesia", "graduation_cap"),
    ("br.com.rodrigokolb.realdrum", "Real Drum", "music_note"),
    ("com.xiaomi.midrop", "ShareMe", "network"),
    ("com.android.stk", "SIM Toolkit", "chip"),
    ("com.pri.toolbox", "Toolbox Luar", "gear_wrench"),
)


def validate(data):
    apps = data["apps"]
    packages = [item["package"] for item in apps]
    if len(packages) != len(set(packages)):
        raise ValueError("Duplicate package names remain in app_icon_map.json")
    invalid = [name for name in packages if not PACKAGE_RE.fullmatch(name)]
    if invalid:
        raise ValueError(f"Invalid Android package names remain: {invalid[:10]}")
    for item in apps:
        if item["kind"] == "glyph" and item.get("icon") is not None:
            # Existing legacy glyph-only records may still point directly to glyph assets.
            if not str(item["icon"]).startswith("icons/apps/"):
                raise ValueError(f"Glyph entry has a non-glyph icon path: {item['package']}")
        if item.get("source") in {"rule", "default"}:
            if item["kind"] != "glyph" or item.get("icon") is not None:
                raise ValueError(f"Generated entry is not glyph-only: {item['package']}")
    counts = {
        "bespoke": sum(item["kind"] == "bespoke" for item in apps),
        "monogram": sum(item["kind"] == "monogram" for item in apps),
        "glyph": sum(item["kind"] == "glyph" for item in apps),
    }
    if data["schema_version"] != 2:
        raise ValueError("Catalog schema_version must be 2")
    if data["total_apps"] != len(apps):
        raise ValueError("total_apps does not match apps length")
    if data["glyphs_used"] != len({item["glyph"] for item in apps}):
        raise ValueError("glyphs_used does not match the catalog")
    if data["icons_bespoke"] != counts["bespoke"]:
        raise ValueError("icons_bespoke metadata is incorrect")
    if data["icons_monogram"] != counts["monogram"]:
        raise ValueError("icons_monogram metadata is incorrect")
    if data["icons_glyph_only"] != counts["glyph"]:
        raise ValueError("icons_glyph_only metadata is incorrect")
    expected = {package: glyph for package, _, glyph in EXACT_MAPPINGS}
    actual = {item["package"]: item["glyph"] for item in apps}
    missing = [package for package in expected if actual.get(package) != expected[package]]
    if missing:
        raise ValueError(f"Exact mappings missing or incorrect: {missing}")
    return counts


def normalize(data):
    apps = [
        item for item in data["apps"]
        if item.get("package") not in INVALID_LEGACY_PACKAGES
        and PACKAGE_RE.fullmatch(str(item.get("package", "")))
    ]
    by_package = {item["package"]: item for item in apps}
    for package, name, glyph in EXACT_MAPPINGS:
        item = by_package.get(package)
        if item is None:
            item = {"package": package}
            apps.append(item)
            by_package[package] = item
        item.update({
            "name": name,
            "glyph": glyph,
            "kind": "glyph",
            "icon": None,
            "source": "exact",
        })

    for item in apps:
        if item.get("source") in {"rule", "default"}:
            item["kind"] = "glyph"
            item["icon"] = None

    data["schema_version"] = 2
    data["apps"] = apps
    data["total_apps"] = len(apps)
    data["glyphs_used"] = len({item["glyph"] for item in apps})
    data["icons_bespoke"] = sum(item["kind"] == "bespoke" for item in apps)
    data["icons_monogram"] = sum(item["kind"] == "monogram" for item in apps)
    data["icons_glyph_only"] = sum(item["kind"] == "glyph" for item in apps)
    data["note"] = (
        "Package yang tidak tercantum dicocokkan dengan keyword_rules.json sebelum "
        "memakai default_glyph. Entri source: default juga menjalankan pencocokan "
        "keyword berdasarkan label aplikasi yang terpasang."
    )
    sources = data.setdefault("sources", [])
    lawnicons_note = (
        "app/assets/appfilter.xml Lawnicons (Apache-2.0); entri hasil impor bertanda "
        "source: rule/default. Commit upstream tidak tercatat pada snapshot impor."
    )
    data["sources"] = [
        source for source in sources
        if "appfilter.xml Lawnicons" not in source
    ] + [lawnicons_note]
    validate(data)
    return data


def write_atomic(path, data):
    fd, temporary = tempfile.mkstemp(prefix=".app-icon-map-", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            json.dump(data, output, indent=2, ensure_ascii=False)
            output.write("\n")
        os.replace(temporary, path)
    except Exception:
        try:
            os.unlink(temporary)
        except OSError:
            pass
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate without modifying the catalog")
    args = parser.parse_args()
    with MAP_PATH.open(encoding="utf-8") as source:
        data = json.load(source)
    if args.check:
        counts = validate(data)
    else:
        data = normalize(data)
        write_atomic(MAP_PATH, data)
        counts = validate(data)
    print(
        f"Catalog OK: {data['total_apps']} apps; "
        f"{counts['bespoke']} bespoke, {counts['monogram']} monogram, "
        f"{counts['glyph']} glyph-only; {data['glyphs_used']} glyphs used."
    )


if __name__ == "__main__":
    main()
