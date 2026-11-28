#!/usr/bin/env python3
"""Validasi struktur, referensi, dan file gambar icon pack NekOS.

Pemakaian:
  python3 tools/validate_pack.py [root]
Memerlukan Pillow dan NumPy.
"""
import json
import os
import re
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ERRORS = []
WARNINGS = []


def read_json(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except Exception as error:
        ERRORS.append(f"{path}: JSON tidak dapat dibaca ({error})")
        return {}


def validate_mask(path, expected_size, min_edge, max_edge, alpha_threshold, label):
    try:
        with Image.open(path) as image:
            image.load()
            if image.format != "WEBP":
                ERRORS.append(f"{label}: format {image.format}, harus WebP")
            if image.size != expected_size or image.mode != "RGBA":
                ERRORS.append(
                    f"{label}: {image.size} {image.mode}, harus "
                    f"{expected_size[0]}x{expected_size[1]} RGBA"
                )
                return
            pixels = np.array(image)
            opaque = pixels[..., 3] > 0
            if not opaque.any():
                ERRORS.append(f"{label}: gambar kosong")
                return
            if (pixels[..., :3][opaque] < 250).any():
                ERRORS.append(f"{label}: bukan mask putih")
            ys, xs = np.where(pixels[..., 3] > alpha_threshold)
            if (xs.min() < min_edge or ys.min() < min_edge or
                    xs.max() > max_edge or ys.max() > max_edge):
                ERRORS.append(f"{label}: keluar dari live area")
    except Exception as error:
        ERRORS.append(f"{label}: gambar tidak dapat dibaca ({error})")


def validate_directory(path, pattern, kind):
    if not os.path.isdir(path):
        ERRORS.append(f"Direktori tidak ditemukan: {path}")
        return []
    files = sorted(name for name in os.listdir(path) if name.lower().endswith(".webp"))
    for filename in files:
        if not re.fullmatch(pattern, filename):
            ERRORS.append(f"{kind}: nama file tidak valid: {filename}")
    return files


# Glyph kategori
glyph_dir = os.path.join(ROOT, "icons", "apps")
glyph_files = validate_directory(
    glyph_dir, r"icon_[a-z0-9]+(?:_[a-z0-9]+)*\.webp", "glyph"
)
glyphs = {filename[5:-5] for filename in glyph_files}
for filename in glyph_files:
    path = os.path.join(glyph_dir, filename)
    validate_mask(path, (96, 96), 7, 88, 8, f"apps/{filename}")
    if os.path.getsize(path) > 20000:
        WARNINGS.append(f"apps/{filename}: ukuran > 20 KB")

# Peta package
app_map = read_json(os.path.join(ROOT, "app_icon_map.json"))
apps = app_map.get("apps", [])
packages = [item.get("package") for item in apps]
if len(packages) != len(set(packages)):
    ERRORS.append("package ganda di app_icon_map.json")

for item in apps:
    package = item.get("package", "")
    glyph = item.get("glyph", "")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+", package):
        ERRORS.append(f"package tidak valid: {package}")
    if glyph not in glyphs:
        ERRORS.append(f"{package}: glyph '{glyph}' tidak ada")
    if not item.get("name"):
        ERRORS.append(f"{package}: tanpa nama")

# Ikon merek dan indeks merek
brand_dir = os.path.join(ROOT, "icons", "brands")
brand_files = validate_directory(brand_dir, r"[a-z0-9]+(?:[a-z0-9._-]*[a-z0-9])?\.webp", "brand")
brand_slugs = {filename[:-5] for filename in brand_files}
brand_index = read_json(os.path.join(ROOT, "brand_index.json"))
indexed_slugs = brand_index.get("slugs", [])
if len(indexed_slugs) != len(set(indexed_slugs)):
    ERRORS.append("brand_index.json memiliki slug duplikat")
if set(indexed_slugs) != brand_slugs:
    ERRORS.append("brand_index.json tidak sinkron dengan icons/brands")
if brand_index.get("count") != len(brand_files):
    ERRORS.append("brand_index count tidak sama dengan jumlah file ikon merek")

for filename in brand_files:
    validate_mask(
        os.path.join(brand_dir, filename), (96, 96), 8, 88, 32,
        f"brands/{filename}"
    )

for item in apps:
    brand = item.get("brand")
    if brand and brand not in brand_slugs:
        ERRORS.append(f"{item.get('package')}: brand '{brand}' tidak ada")

aliases = brand_index.get("aliases", {})
for alias, target in aliases.items():
    if not re.fullmatch(r"[a-z0-9]+", alias):
        ERRORS.append(f"alias tidak ternormalisasi: '{alias}'")
    if alias in brand_slugs:
        ERRORS.append(f"alias '{alias}' bertabrakan dengan slug merek canonical")
    if target not in brand_slugs:
        ERRORS.append(f"alias {alias}: target '{target}' tidak ada")

# Konsistensi metadata dan aturan
rules = read_json(os.path.join(ROOT, "app_icon_rules.json"))
default_glyph = app_map.get("default_glyph")
if default_glyph not in glyphs:
    ERRORS.append(f"default_glyph '{default_glyph}' tidak ada")
if rules.get("default") not in glyphs:
    ERRORS.append(f"rules.default '{rules.get('default')}' tidak ada")
if default_glyph != rules.get("default"):
    ERRORS.append("default_glyph pada app_icon_map.json berbeda dari app_icon_rules.json")
if app_map.get("total_apps") != len(apps):
    ERRORS.append("total_apps tidak sama dengan jumlah apps")
if app_map.get("total_glyphs") != len(glyphs):
    ERRORS.append("total_glyphs tidak sama dengan jumlah file glyph")
if app_map.get("glyphs_used") != len({item.get("glyph") for item in apps}):
    ERRORS.append("glyphs_used tidak sama dengan jumlah glyph unik pada mapping")
if app_map.get("total_brand_icons") != len(brand_files):
    ERRORS.append("total_brand_icons tidak sama dengan jumlah file ikon merek")
if app_map.get("apps_with_brand") != sum(1 for item in apps if item.get("brand")):
    ERRORS.append("apps_with_brand tidak sama dengan jumlah mapping ber-brand")
if app_map.get("brand_index_file") != "brand_index.json":
    ERRORS.append("brand_index_file harus menunjuk brand_index.json")

prefix_rules = rules.get("prefix_rules", [])
seen_prefixes = set()
for entry in prefix_rules:
    if not isinstance(entry, list) or len(entry) != 2:
        ERRORS.append(f"format prefix rule tidak valid: {entry}")
        continue
    prefix, glyph = entry
    if not prefix or prefix in seen_prefixes:
        ERRORS.append(f"prefix kosong atau duplikat: '{prefix}'")
    seen_prefixes.add(prefix)
    if glyph not in glyphs:
        ERRORS.append(f"prefix {prefix}: glyph '{glyph}' tidak ada")

keyword_rules = rules.get("keyword_rules", [])
seen_patterns = set()
for entry in keyword_rules:
    if not isinstance(entry, list) or len(entry) != 2:
        ERRORS.append(f"format keyword rule tidak valid: {entry}")
        continue
    pattern, glyph = entry
    if pattern in seen_patterns:
        WARNINGS.append(f"keyword regex duplikat: '{pattern}'")
    seen_patterns.add(pattern)
    try:
        re.compile(pattern, re.I)
    except re.error as error:
        ERRORS.append(f"regex salah '{pattern}': {error}")
    if glyph not in glyphs:
        ERRORS.append(f"keyword '{pattern[:30]}': glyph '{glyph}' tidak ada")

# Indeks glyph
icons_index = read_json(os.path.join(glyph_dir, "icons_index.json"))
index_items = icons_index.get("items", [])
index_files = [item.get("file") for item in index_items]
if len(index_files) != len(set(index_files)):
    ERRORS.append("icons_index.json memiliki file glyph duplikat")
if set(index_files) != set(glyph_files):
    ERRORS.append("icons_index.json tidak sinkron dengan file glyph")
if icons_index.get("total") != len(index_items):
    ERRORS.append("icons_index total tidak sama dengan jumlah items")
for item in index_items:
    expected = f"icon_{item.get('name', '')}.webp"
    if item.get("file") != expected:
        ERRORS.append(f"icons_index item '{item.get('name')}': file seharusnya {expected}")

for warning in WARNINGS:
    print("WARN ", warning)
for error in ERRORS:
    print("ERROR", error)

print(
    f"{len(glyphs)} glyph, {len(brand_files)} ikon merek, "
    f"{len(apps)} aplikasi: {len(ERRORS)} error, {len(WARNINGS)} warning"
)
sys.exit(1 if ERRORS else 0)
