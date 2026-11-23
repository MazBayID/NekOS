#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN_ASSETS="$ROOT/main_assets"
RUNTIME_ICONS="$ROOT/app/src/main/assets/ikon/icons"
OUTPUT_ROOT="$ROOT/build/webp-migration"
STAGING="$OUTPUT_ROOT/staging"

command -v ffmpeg >/dev/null 2>&1 || {
  echo "FFmpeg belum terpasang. Jalankan: pkg install ffmpeg"
  exit 2
}
command -v python3 >/dev/null 2>&1 || {
  echo "Python 3 belum tersedia."
  exit 2
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "sha256sum tidak ditemukan."
  exit 2
}

mkdir -p "$STAGING"
mapfile -d '' PNG_FILES < <(find "$MAIN_ASSETS" -type f -iname '*.png' -print0 | sort -z)

if (("${#PNG_FILES[@]}" == 0)); then
  echo "Tidak ada PNG di main_assets. Tidak ada perubahan."
  exit 0
fi

echo "Ditemukan ${#PNG_FILES[@]} PNG di main_assets."
echo "Tahap 1/4: konversi FFmpeg WebP lossless + verifikasi piksel."

pixel_hash() {
  ffmpeg -v error -i "$1" -frames:v 1 -f rawvideo -pix_fmt rgba - 2>/dev/null | sha256sum | awk '{print $1}'
}

TOTAL_PNG=0
TOTAL_WEBP=0
INDEX=0

for source in "${PNG_FILES[@]}"; do
  INDEX=$((INDEX + 1))
  relative="${source#"$MAIN_ASSETS/"}"
  output="$STAGING/${relative%.*}.webp"
  mkdir -p "$(dirname "$output")"

  printf '[%d/%d] %s\n' "$INDEX" "${#PNG_FILES[@]}" "$relative"
  ffmpeg -y -v error -i "$source" -frames:v 1 \
    -c:v libwebp -lossless 1 -compression_level 6 -quality 100 \
    -preset picture "$output"

  source_hash="$(pixel_hash "$source")"
  output_hash="$(pixel_hash "$output")"
  if [[ -z "$source_hash" || "$source_hash" != "$output_hash" ]]; then
    echo "GAGAL: hasil piksel tidak identik: $relative" >&2
    exit 1
  fi

  TOTAL_PNG=$((TOTAL_PNG + $(stat -c '%s' "$source")))
  TOTAL_WEBP=$((TOTAL_WEBP + $(stat -c '%s' "$output")))
done

echo "Semua ${#PNG_FILES[@]} gambar lolos verifikasi piksel."
echo "Tahap 2/4: siapkan WebP di main_assets dan runtime aplikasi."

# Pastikan semua 100 ikon runtime memiliki pasangan sumber.
for source in "$MAIN_ASSETS"/ikon/icons/*.png; do
  [[ -e "$source" ]] || continue
  relative="${source#"$MAIN_ASSETS/"}"
  [[ -s "$STAGING/${relative%.*}.webp" ]] || {
    echo "WebP ikon runtime tidak ditemukan: $relative" >&2
    exit 1
  }
done

while IFS= read -r -d '' webp; do
  relative="${webp#"$STAGING/"}"
  destination="$MAIN_ASSETS/$relative"
  mkdir -p "$(dirname "$destination")"
  cp "$webp" "$destination"
done < <(find "$STAGING" -type f -iname '*.webp' -print0)

mkdir -p "$RUNTIME_ICONS"
for webp in "$MAIN_ASSETS"/ikon/icons/*.webp; do
  [[ -e "$webp" ]] || continue
  cp "$webp" "$RUNTIME_ICONS/$(basename "$webp")"
done

echo "Tahap 3/4: perbarui referensi JSON dan dokumentasi indeks."
python3 - "$MAIN_ASSETS" "$ROOT/app/src/main/assets" <<'PY'
import json
import sys
from pathlib import Path

roots = [Path(sys.argv[1]), Path(sys.argv[2])]

def replace_png(value):
    if isinstance(value, str):
        return value[:-4] + ".webp" if value.lower().endswith(".png") else value
    if isinstance(value, list):
        return [replace_png(item) for item in value]
    if isinstance(value, dict):
        return {key: replace_png(item) for key, item in value.items()}
    return value

for root in roots:
    if not root.exists():
        continue
    for path in root.rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        updated = replace_png(data)
        path.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

for path in Path(sys.argv[1]).rglob("*.md"):
    text = path.read_text(encoding="utf-8")
    path.write_text(text.replace(".png", ".webp"), encoding="utf-8")
PY

echo "Tahap 4/4: hapus PNG hanya setelah seluruh WebP lolos verifikasi."
find "$MAIN_ASSETS" -type f -iname '*.png' -delete
find "$RUNTIME_ICONS" -type f -iname '*.png' -delete

SAVED=$((TOTAL_PNG - TOTAL_WEBP))
if ((TOTAL_PNG > 0)); then
  PERCENT=$((SAVED * 100 / TOTAL_PNG))
else
  PERCENT=0
fi

mkdir -p "$OUTPUT_ROOT"
{
  echo "NekOS PNG to WebP migration"
  echo "Images converted and pixel-verified: ${#PNG_FILES[@]}"
  echo "PNG source bytes: $TOTAL_PNG"
  echo "WebP bytes: $TOTAL_WEBP"
  echo "Bytes saved: $SAVED"
  echo "Approximate reduction: $PERCENT%"
  echo "Encoding: FFmpeg libwebp lossless, compression_level=6, quality=100"
  echo "PNG originals removed only after every output passed RGBA pixel-hash verification."
} | tee "$OUTPUT_ROOT/summary.txt"

rm -rf "$STAGING"
echo "Selesai. PNG di main_assets dan runtime icons sudah diganti WebP."
