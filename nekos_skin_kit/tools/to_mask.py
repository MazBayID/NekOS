#!/usr/bin/env python3
"""Ubah gambar apa pun menjadi aset mask putih yang sesuai spesifikasi.

  python3 -I tools/to_mask.py gambar.png --kind glyph --out my_skin/icons/tag/icon_brain.webp
  python3 -I tools/to_mask.py kepala.png --kind badge --out my_skin/components/tag_badge/badge_cat.webp
  python3 -I tools/to_mask.py gelap_di_putih.jpg --kind glyph --out x.webp          # bentuk = piksel gelap
  python3 -I tools/to_mask.py terang_di_hitam.jpg --kind glyph --light-ink --out x.webp

--kind glyph : kanvas 96x96, isi di-fit ke live area 80x80 dan dipusatkan (bbox).
--kind badge : kanvas 128x128, isi di-fit ke 116x120 dan dipusatkan, margin >= 4 px.
Bila gambar sudah punya alpha, alpha dipakai apa adanya; bila tidak, alpha dihitung dari kontras
terhadap latar polos. Warna asli DIBUANG: RGB selalu 255.
"""
import argparse, os, numpy as np
from PIL import Image

ap = argparse.ArgumentParser()
ap.add_argument('input'); ap.add_argument('--out', required=True)
ap.add_argument('--kind', choices=['glyph', 'badge'], default='glyph')
ap.add_argument('--light-ink', action='store_true', help='bentuk = piksel terang di latar gelap')
ap.add_argument('--threshold', type=float, default=0.02, help='buang noise alpha di bawah nilai ini (0..1)')
a = ap.parse_args()

im = Image.open(a.input).convert('RGBA')
arr = np.array(im).astype(float)
if arr[..., 3].min() < 250:                       # sudah transparan
    alpha = arr[..., 3] / 255
else:
    L = arr[..., :3].min(axis=2) if not a.light_ink else 255 - arr[..., :3].max(axis=2)
    bg, ink = np.percentile(L, 99.5), np.percentile(L, 0.5)
    alpha = np.clip((bg - L) / max(bg - ink, 1e-6), 0, 1)
alpha = np.where(alpha < a.threshold, 0, alpha)
if alpha.max() == 0: raise SystemExit('hasil kosong: coba --light-ink atau cek gambar')
ys, xs = np.where(alpha > 0.03)
crop = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
canvas, box = (96, (80, 80)) if a.kind == 'glyph' else (128, (116, 120))
ss = 4                                            # supersample agar tepi halus
h, w = crop.shape
s = min(box[0] * ss / w, box[1] * ss / h)
big = Image.fromarray((crop * 255).astype(np.uint8)).resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
small = big.resize((max(1, round(big.width / ss)), max(1, round(big.height / ss))), Image.LANCZOS)
out = np.zeros((canvas, canvas, 4), np.uint8); out[..., :3] = 255
al = np.zeros((canvas, canvas), np.uint8)
ox = (canvas - small.width) // 2
oy = (canvas - small.height) // 2 if a.kind == 'glyph' else (canvas - small.height) // 2
al[oy:oy + small.height, ox:ox + small.width] = np.array(small)
out[..., 3] = al
os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
Image.fromarray(out, 'RGBA').save(a.out, 'WEBP', lossless=True, exact=True, method=6)
print(f'OK  {a.out}  {canvas}x{canvas}, isi {small.width}x{small.height}, {os.path.getsize(a.out)} byte')
