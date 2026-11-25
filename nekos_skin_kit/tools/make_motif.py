#!/usr/bin/env python3
"""
make_motif.py - ubah gambar motif apa pun menjadi aset motif standar:
  * 1024x1024 px (rasio 1:1), seamless (bisa di-tile tanpa sambungan)
  * objek putih murni (#FFFFFF) + background transparan (siap di-tint)

Pemakaian:
  python make_motif.py INPUT.jpg --name bunga --out my_skin/motifs --type line
  python make_motif.py INPUT.png --name bunga --x 200 --y 100 --overlap 160
  python make_motif.py INPUT.jpg --name bunga --light-ink      # objek terang, bg gelap

Kebutuhan: Python 3.8+, pip install pillow numpy
Hasil    : <out>/motif_<name>.png (1024) dan motif_<name>@512.png (512)
"""
import argparse, os, sys
import numpy as np
from PIL import Image


def extract_alpha(im, light_ink=False):
    """Alpha = seberapa 'berbeda' sebuah piksel dari background polos."""
    if im.mode in ("RGBA", "LA"):
        a = np.array(im.convert("RGBA"))[..., 3].astype(float) / 255
        if a.min() < 0.99:  # sudah transparan -> pakai apa adanya
            return a
    rgb = np.array(im.convert("RGB")).astype(float)
    if light_ink:  # objek terang di bg gelap
        m = 255 - rgb.max(axis=2)
    else:  # objek gelap/berwarna di bg putih
        m = rgb.min(axis=2)
    bg, ink = np.percentile(m, 99.5), np.percentile(m, 1)
    a = np.clip((bg - m) / max(bg - ink, 1e-6), 0, 1)
    return np.clip((a - 0.02) / 0.98, 0, 1)


def _wrap_x(R, N, o):
    """Buat lebar N seamless horizontal lewat jahitan (seam) yang minim ink."""
    H = R.shape[0]
    cost = R[:, :o] + R[:, N:N + o]  # jahit di tempat kedua sisi kosong
    acc = cost.copy()
    inf = np.inf
    for y in range(1, H):
        p = acc[y - 1]
        best = np.minimum(p, np.minimum(np.r_[inf, p[:-1]], np.r_[p[1:], inf]))
        acc[y] += best
    path = np.zeros(H, int)
    path[-1] = int(np.argmin(acc[-1]))
    for y in range(H - 2, -1, -1):
        k = path[y + 1]
        lo, hi = max(k - 1, 0), min(k + 2, o)
        path[y] = lo + int(np.argmin(acc[y, lo:hi]))
    T = R[:, :N].copy()
    k = np.arange(o)
    for y in range(H):
        T[y, :o] = np.where(k < path[y], R[y, N:N + o], R[y, :o])
    return T


def make_seamless(a, N, o, x0, y0):
    if a.shape[1] < x0 + N + o or a.shape[0] < y0 + N + o:
        sys.exit(
            f"Gambar terlalu kecil: butuh minimal {x0 + N + o}x{y0 + N + o}px dari titik crop "
            f"(gambar: {a.shape[1]}x{a.shape[0]}). Kecilkan --overlap/--x/--y atau pakai gambar lebih besar."
        )
    R = a[y0:y0 + N + o, x0:x0 + N + o]
    T = _wrap_x(R, N, o)  # (N+o) x N
    T = _wrap_x(T.T.copy(), N, o).T  # N x N
    return T


def save_white(alpha, path):
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.clip(alpha * 255 + .5, 0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path, optimize=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("--name", required=True)
    ap.add_argument("--out", default=".")
    ap.add_argument("--size", type=int, default=1024)
    ap.add_argument("--overlap", type=int, default=128, help="lebar zona jahitan (px)")
    ap.add_argument("--x", type=int, default=0)
    ap.add_argument("--y", type=int, default=0)
    ap.add_argument("--light-ink", action="store_true")
    ap.add_argument("--no-seamless", action="store_true", help="hanya crop, tanpa jahit")
    ap.add_argument("--type", choices=["solid", "line"], default="line", help="jenis motif (untuk snippet skin.json)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    alpha = extract_alpha(Image.open(a.input), a.light_ink)
    N = a.size
    if a.no_seamless:
        T = alpha[a.y:a.y + N, a.x:a.x + N]
    else:
        T = make_seamless(alpha, N, a.overlap, a.x, a.y)
    save_white(T, os.path.join(a.out, f"motif_{a.name}.png"))
    half = Image.fromarray((T * 255).astype(np.uint8)).resize((N // 2, N // 2), Image.LANCZOS)
    save_white(np.array(half).astype(float) / 255, os.path.join(a.out, f"motif_{a.name}@512.png"))
    print(f"OK -> {a.out}/motif_{a.name}.png (+@512)  coverage={T.mean():.1%}")
    import json
    print("Tambahkan ke \"motifs\" di skin.json:")
    print(json.dumps({"id": a.name, "file": f"motifs/motif_{a.name}.png", "file_small": f"motifs/motif_{a.name}@512.png",
                      "type": a.type, "coverage": round(float(T.mean()), 3),
                      "suggested_display_dp": 320 if a.type == "solid" else 600,
                      "suggested_opacity": 0.2 if a.type == "solid" else 0.3}, indent=2))


if __name__ == "__main__":
    main()
