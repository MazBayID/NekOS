#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN_ASSETS="$ROOT/main_assets"
RUNTIME_ICONS="$ROOT/app/src/main/assets/ikon/icons"
OUTPUT_ROOT="$ROOT/build/webp-migration"
STAGING="$OUTPUT_ROOT/staging"

command -v python3 >/dev/null 2>&1 || {
  echo "Python 3 belum tersedia."
  exit 2
}
python3 -c 'from PIL import Image' >/dev/null 2>&1 || {
  echo "Pillow belum tersedia. Jalankan: pkg install python-pillow"
  exit 2
}

mkdir -p "$STAGING"
python3 - "$ROOT" "$MAIN_ASSETS" "$RUNTIME_ICONS" "$STAGING" "$OUTPUT_ROOT" <<'PY'
import json
import shutil
import sys
from pathlib import Path
from PIL import Image

root, main_arg, runtime_arg, staging_arg, output_arg = map(Path, sys.argv[1:])
main_assets, runtime_icons = main_arg, runtime_arg
staging, output_root = staging_arg, output_arg
main_stage, runtime_stage = staging / "main_assets", staging / "runtime_icons"

sources = []
for source_root, stage_root in ((main_assets, main_stage), (runtime_icons, runtime_stage)):
    if source_root.exists():
        sources.extend((p, source_root, stage_root) for p in sorted(source_root.rglob("*"))
                       if p.is_file() and p.suffix.lower() == ".png")

if not sources:
    print("Tidak ada PNG yang ditemukan. Tidak ada perubahan.")
    raise SystemExit(0)

print(f"Ditemukan {sum(1 for _, r, _ in sources if r == main_assets)} PNG di main_assets "
      f"dan {sum(1 for _, r, _ in sources if r == runtime_icons)} PNG runtime.")

total_png = total_webp = converted = 0
converted_by_root = {main_assets: [], runtime_icons: []}

# Konversi dan verifikasi SEMUA file sebelum mengubah sumber apa pun.
for index, (source, source_root, stage_root) in enumerate(sources, 1):
    relative = source.relative_to(source_root)
    destination = (stage_root / relative).with_suffix(".webp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    print(f"[{index}/{len(sources)}] {source_root.name}/{relative}", flush=True)

    with Image.open(source) as original:
        if getattr(original, "n_frames", 1) != 1:
            raise RuntimeError(f"PNG animasi belum didukung: {source}")
        original.load()
        expected = original.convert("RGBA").tobytes()
        original_size = source.stat().st_size
        original.convert("RGBA").save(
            destination, "WEBP", lossless=True, exact=True, method=0
        )

    with Image.open(destination) as result:
        result.load()
        actual = result.convert("RGBA").tobytes()
    if actual != expected:
        raise RuntimeError(f"Verifikasi piksel gagal: {source}")
    if Image.open(destination).size != Image.open(source).size:
        raise RuntimeError(f"Ukuran gambar berubah: {source}")

    total_png += original_size
    total_webp += destination.stat().st_size
    converted += 1
    converted_by_root[source_root].append(relative)

print(f"Verifikasi piksel berhasil: {converted}/{len(sources)}.")

# Semua file sudah lolos. Salin WebP ke lokasi tujuan.
for source_root, stage_root in ((main_assets, main_stage), (runtime_icons, runtime_stage)):
    if not stage_root.exists():
        continue
    for webp in stage_root.rglob("*.webp"):
        destination = source_root / webp.relative_to(stage_root)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(webp, destination)

# Perbarui referensi JSON setelah semua WebP tersedia.
for data_root in (main_assets, root / "app/src/main/assets"):
    if not data_root.exists():
        continue
    for path in data_root.rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        def update(value):
            if isinstance(value, str):
                return value[:-4] + ".webp" if value.lower().endswith(".png") else value
            if isinstance(value, list):
                return [update(item) for item in value]
            if isinstance(value, dict):
                return {key: update(item) for key, item in value.items()}
            return value
        path.write_text(json.dumps(update(data), ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8")

for path in main_assets.rglob("*.md"):
    path.write_text(path.read_text(encoding="utf-8").replace(".png", ".webp"),
                    encoding="utf-8")

# Hapus PNG hanya setelah konversi dan verifikasi seluruhnya berhasil.
for source, _, _ in sources:
    source.unlink()

saved = total_png - total_webp
percent = round(saved * 100 / total_png) if total_png else 0
output_root.mkdir(parents=True, exist_ok=True)
summary = (
    "NekOS PNG to WebP migration\n"
    f"Images converted and pixel-verified: {converted}\n"
    f"PNG source bytes: {total_png}\n"
    f"WebP bytes: {total_webp}\n"
    f"Bytes saved: {saved}\n"
    f"Approximate reduction: {percent}%\n"
    "Encoding: Pillow WebP lossless=True, exact=True, method=0\n"
    "Runtime icons converted independently from their own PNG originals.\n"
    "PNG originals removed only after every output passed RGBA pixel verification.\n"
)
(output_root / "summary.txt").write_text(summary, encoding="utf-8")
print(summary, end="")
shutil.rmtree(staging, ignore_errors=True)
print("Selesai. PNG di main_assets dan runtime icons sudah diganti WebP.")
PY
