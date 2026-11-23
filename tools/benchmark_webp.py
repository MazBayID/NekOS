#!/usr/bin/env python3
"""Benchmark PNG icon assets against lossless WebP without modifying source files.

Run from the repository root:
    python tools/benchmark_webp.py

Requires Pillow with WebP support.

On Termux, prefer the native package to avoid a slow source build:
    pkg install python-pillow

On desktop Python:
    python -m pip install --user Pillow
"""

from __future__ import annotations

import argparse
import csv
import io
import sys
from pathlib import Path

try:
    from PIL import Image, features
except ImportError:
    print("Pillow is required.", file=sys.stderr)
    if "/com.termux/" in str(Path.home()):
        print("On Termux, install the native package with: pkg install python-pillow", file=sys.stderr)
    else:
        print("Install it with: python -m pip install --user Pillow", file=sys.stderr)
    raise SystemExit(2)


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = REPOSITORY_ROOT / "app/src/main/assets/ikon/icons"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "build/webp-benchmark"


def format_bytes(value: int) -> str:
    return f"{value / (1024 * 1024):.2f} MiB"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE,
                        help="Directory containing source PNG icons.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help="Directory for CSV and summary output (default: build/webp-benchmark).")
    parser.add_argument("--method", type=int, choices=range(7), default=0,
                        help="WebP effort level 0 (fastest) to 6 (slowest); default: 0.")
    parser.add_argument("--write-candidates", action="store_true",
                        help="Also write WebP candidates here; source PNG files remain untouched.")
    args = parser.parse_args()

    if not features.check("webp"):
        print("This Pillow installation has no WebP encoder/decoder support.", file=sys.stderr)
        return 2
    if not args.source.is_dir():
        print(f"Source directory not found: {args.source}", file=sys.stderr)
        return 2

    files = sorted(args.source.glob("*.png"))
    if not files:
        print(f"No PNG files found in: {args.source}", file=sys.stderr)
        return 2

    args.output.mkdir(parents=True, exist_ok=True)
    candidate_dir = args.output / "webp"
    if args.write_candidates:
        candidate_dir.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, object]] = []
    total_png = 0
    total_webp = 0
    smaller_count = 0
    exact_count = 0
    failed_count = 0

    for index, path in enumerate(files, start=1):
        print(f"[{index}/{len(files)}] Processing {path.name}...", flush=True)
        png_size = path.stat().st_size
        try:
            with Image.open(path) as source:
                source.load()
                rgba = source.convert("RGBA")
                width, height = rgba.size
                alpha_extrema = rgba.getchannel("A").getextrema()
                has_transparency = alpha_extrema[0] < 255

                buffer = io.BytesIO()
                rgba.save(
                    buffer,
                    format="WEBP",
                    lossless=True,
                    quality=100,
                    method=args.method,
                    exact=True,
                )
                webp_data = buffer.getvalue()

            with Image.open(io.BytesIO(webp_data)) as decoded:
                decoded.load()
                decoded_rgba = decoded.convert("RGBA")
                exact_match = (
                    decoded_rgba.size == (width, height)
                    and decoded_rgba.tobytes() == rgba.tobytes()
                )

            webp_size = len(webp_data)
            saving = png_size - webp_size
            saving_percent = (saving / png_size * 100.0) if png_size else 0.0
            total_png += png_size
            total_webp += webp_size
            smaller_count += int(webp_size < png_size)
            exact_count += int(exact_match)
            if not exact_match:
                failed_count += 1

            if args.write_candidates:
                (candidate_dir / f"{path.stem}.webp").write_bytes(webp_data)

            rows.append({
                "file": path.name,
                "width": width,
                "height": height,
                "source_mode": source.mode,
                "has_transparency": has_transparency,
                "png_bytes": png_size,
                "webp_lossless_bytes": webp_size,
                "saved_bytes": saving,
                "saved_percent": round(saving_percent, 2),
                "pixel_exact_match": exact_match,
            })
        except Exception as error:
            failed_count += 1
            rows.append({
                "file": path.name,
                "width": "",
                "height": "",
                "source_mode": "",
                "has_transparency": "",
                "png_bytes": png_size,
                "webp_lossless_bytes": "",
                "saved_bytes": "",
                "saved_percent": "",
                "pixel_exact_match": False,
                "error": str(error),
            })

    csv_path = args.output / "webp-benchmark.csv"
    columns = [
        "file", "width", "height", "source_mode", "has_transparency",
        "png_bytes", "webp_lossless_bytes", "saved_bytes",
        "saved_percent", "pixel_exact_match", "error",
    ]
    with csv_path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    total_saved = total_png - total_webp
    total_percent = (total_saved / total_png * 100.0) if total_png else 0.0
    summary = [
        "NekOS PNG vs lossless WebP benchmark",
        f"Source: {args.source}",
        f"Images found: {len(files)}",
        f"WebP method: {args.method} (0 = fastest, 6 = slowest)",
        f"Successfully encoded: {len(files) - failed_count}",
        f"Pixel-exact matches: {exact_count}/{len(files)}",
        f"Files where WebP is smaller: {smaller_count}/{len(files)}",
        f"PNG total: {format_bytes(total_png)} ({total_png:,} bytes)",
        f"WebP total: {format_bytes(total_webp)} ({total_webp:,} bytes)",
        f"Net saved: {format_bytes(total_saved)} ({total_saved:,} bytes; {total_percent:.2f}%)",
        f"CSV report: {csv_path}",
        "Source PNG files were not modified.",
    ]
    summary_path = args.output / "summary.txt"
    summary_path.write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("\n".join(summary))

    if failed_count or exact_count != len(files):
        print("RESULT: REVIEW REQUIRED — at least one encode/decode or exact-pixel check failed.")
        return 1
    if total_saved <= 0:
        print("RESULT: KEEP PNG — lossless WebP does not reduce the catalog's total size.")
    else:
        print("RESULT: WEBP IS A SIZE CANDIDATE — review per-file results and test Android decode/render performance before migration.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
