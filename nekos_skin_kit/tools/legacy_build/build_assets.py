#!/usr/bin/env python3
"""NekOS reskin asset builder.

Converts the original 512px "baked colour" icon sheets into small, tintable
alpha-mask glyphs, builds a canonical badge, and normalises frame components.

Usage:  python3 -I build_assets.py <orig_dir> <out_dir>
"""
import json, os, sys, shutil
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SRC, OUT = sys.argv[1], sys.argv[2]

GLYPH = 96            # glyph canvas (px) = 24dp @4x
BADGE = 128           # badge canvas (px) = 32dp @4x
SS = 4                # supersampling for drawing
SCALE = 132 / 512     # original 512 canvas -> new px (keeps relative stroke weights)
TEAL_L, CREAM_L = 105.0, 238.0   # luminance of baked teal / cream in the originals


def save_mask(alpha01, path):
    """alpha01: float array 0..1 -> white RGBA lossless WebP (RGB forced to 255)."""
    a = np.clip(alpha01 * 255 + 0.5, 0, 255).astype(np.uint8)
    rgba = np.dstack([np.full_like(a, 255)] * 3 + [a])
    im = Image.fromarray(rgba, 'RGBA')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if path.endswith('.webp'):
        im.save(path, 'WEBP', lossless=True, exact=True, method=6)
    else:
        im.save(path, 'PNG', optimize=True)


def load(path):
    return np.array(Image.open(path).convert('RGBA')).astype(np.float32)


def glyph_alpha(path):
    a = load(path)
    L = a[..., :3].mean(2)
    g = np.clip((L - TEAL_L) / (CREAM_L - TEAL_L), 0, 1)
    inside = Image.fromarray(((a[..., 3] > 250) * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(9))
    g = g * (np.array(inside) > 0)
    p = np.percentile(g[g > 0.05], 95) if (g > 0.05).any() else 1.0
    return np.clip(g / max(p, 1e-3), 0, 1)


def bbox(a, t=0.5):
    ys, xs = np.where(a > t)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def to_canvas(g512, center=None):
    """Scale a 512px glyph by SCALE and centre its bbox in GLYPH canvas."""
    x0, y0, x1, y1 = bbox(g512)
    big_n = round(512 * SCALE * SS)
    big = np.array(Image.fromarray((g512 * 255).astype(np.uint8)).resize((big_n, big_n), Image.LANCZOS)).astype(np.float32) / 255
    k = big_n / 512
    cx, cy = (x0 + x1) / 2 * k, (y0 + y1) / 2 * k
    canvas = np.zeros((GLYPH * SS, GLYPH * SS), np.float32)
    ox, oy = round(GLYPH * SS / 2 - cx), round(GLYPH * SS / 2 - cy)
    # paste with clipping
    sx0, sy0 = max(0, -ox), max(0, -oy)
    dx0, dy0 = max(0, ox), max(0, oy)
    w = min(big_n - sx0, GLYPH * SS - dx0)
    h = min(big_n - sy0, GLYPH * SS - dy0)
    canvas[dy0:dy0 + h, dx0:dx0 + w] = big[sy0:sy0 + h, sx0:sx0 + w]
    return down(canvas)


def down(canvas):
    im = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8))
    im = im.resize((canvas.shape[1] // SS, canvas.shape[0] // SS), Image.BOX)
    return np.array(im).astype(np.float32) / 255


# ---------------------------------------------------------------- drawn glyphs
STROKE = 10  # px at 96 canvas (source median ~ 10-12)

def line(d, p, q, w=STROKE):
    d.line([(p[0] * SS, p[1] * SS), (q[0] * SS, q[1] * SS)], fill=255, width=int(w * SS))
    r = w * SS / 2
    for x, y in (p, q):
        d.ellipse([x * SS - r, y * SS - r, x * SS + r, y * SS + r], fill=255)

def new_canvas():
    im = Image.new('L', (GLYPH * SS, GLYPH * SS), 0)
    return im, ImageDraw.Draw(im)

def finish(im):
    return np.array(im.resize((GLYPH, GLYPH), Image.BOX)).astype(np.float32) / 255

def draw_chevron(direction):
    im, d = new_canvas()
    a, b, c = (34, 20), (62, 48), (34, 76)    # points right, 28px half-height
    pts = {'right': (a, b, c),
           'left': ((96 - a[0], a[1]), (96 - b[0], b[1]), (96 - c[0], c[1])),
           'down': ((a[1], a[0]), (b[1], b[0]), (c[1], c[0])),
           'up': ((a[1], 96 - a[0]), (b[1], 96 - b[0]), (c[1], 96 - c[0]))}[direction]
    line(d, pts[0], pts[1]); line(d, pts[1], pts[2])
    return finish(im)

def draw_dots(layout):
    im, d = new_canvas()
    r = 6.5 * SS
    if layout == 'vertical':
        pts = [(48, 20), (48, 48), (48, 76)]
    elif layout == 'drag':   # 2 x 3 grip
        pts = [(x, y) for x in (34, 62) for y in (22, 48, 74)]
        r = 5.5 * SS
    for x, y in pts:
        d.ellipse([x * SS - r, y * SS - r, x * SS + r, y * SS + r], fill=255)
    return finish(im)


# ------------------------------------------------------------------ catalogues
# (old_index, new_name, visual, description)   -- descriptions in Indonesian
TAG = [
    # komunikasi
    (11, 'mail', 'Amplop', 'email / surat'),
    (12, 'chat', 'Gelembung chat', 'pesan / chat / sosial'),
    (13, 'contacts', 'Buku kontak', 'kontak / buku alamat'),
    # media
    (8, 'music_note', 'Not musik', 'musik / audio'),
    (35, 'film', 'Klaper film dengan tombol play', 'video / film / streaming'),
    (10, 'camera', 'Kamera', 'kamera / foto'),
    (20, 'image', 'Gambar pemandangan', 'galeri / foto / wallpaper'),
    (31, 'gamepad', 'Stik gim', 'game'),
    (33, 'palette', 'Palet cat', 'seni / desain / kustomisasi'),
    # internet & cloud
    (7, 'globe', 'Globe', 'browser / internet'),
    (1, 'cloud', 'Awan', 'cloud / sinkronisasi'),
    (2, 'google', 'Huruf G', 'layanan Google'),
    (44, 'network', 'Diagram hierarki / jaringan', 'jaringan / hirarki / server'),
    (18, 'download', 'Panah turun ke baki', 'unduhan'),
    # produktivitas
    (24, 'briefcase', 'Koper kerja', 'kerja / bisnis'),
    (14, 'calendar', 'Kalender', 'kalender / agenda'),
    (15, 'alarm_clock', 'Jam beker', 'jam / alarm / waktu'),
    (17, 'folder', 'Folder', 'berkas / file manager'),
    (22, 'newspaper', 'Koran', 'berita / bacaan'),
    (29, 'book', 'Buku terbuka', 'buku / baca'),
    (30, 'graduation_cap', 'Topi toga', 'pendidikan / belajar'),
    (61, 'calculator', 'Kalkulator', 'kalkulator / utilitas'),
    (27, 'bar_chart', 'Grafik batang', 'keuangan / statistik'),
    (28, 'cart', 'Keranjang belanja', 'belanja / e-commerce'),
    # gaya hidup & lokasi
    (16, 'weather', 'Matahari di balik awan', 'cuaca'),
    (23, 'location_pin', 'Pin lokasi', 'peta / navigasi / lokasi'),
    (26, 'medical_cross', 'Tanda plus tebal', 'kesehatan / medis'),
    (25, 'running', 'Orang berlari', 'olahraga / kebugaran'),
    (41, 'home', 'Rumah', 'beranda / rumah pintar'),
    (90, 'star', 'Bintang', 'favorit'),
    # sistem & developer
    (3, 'gear_wrench', 'Roda gigi dengan kunci pas', 'sistem / alat'),
    (42, 'shield', 'Perisai', 'keamanan / privasi'),
    (4, 'brain', 'Otak', 'AI / pengetahuan'),
    (78, 'terminal', 'Jendela terminal', 'terminal / shell'),
    (77, 'code', 'Kurung sudut </>', 'pemrograman / developer'),
    (79, 'database', 'Tumpukan silinder', 'database / penyimpanan data'),
]
UI = [
    # (old_index|None, name, visual, description, group)
    (1, 'pencil', 'Pensil', 'edit', 'aksi'),
    (4, 'search', 'Kaca pembesar', 'cari', 'aksi'),
    (3, 'gear', 'Roda gigi', 'pengaturan', 'aksi'),
    (12, 'dots_horizontal', 'Tiga titik horizontal', 'menu lainnya', 'aksi'),
    ('gen', 'dots_vertical', 'Tiga titik vertikal', 'menu lainnya (overflow)', 'aksi'),
    (85, 'plus', 'Tanda plus', 'tambah', 'aksi'),
    (86, 'minus', 'Tanda minus', 'kurangi / hapus dari daftar', 'aksi'),
    (87, 'trash', 'Tong sampah', 'hapus', 'aksi'),
    (110, 'close', 'Tanda silang', 'tutup / batal', 'aksi'),
    (109, 'check', 'Tanda centang', 'konfirmasi / terpilih', 'aksi'),
    (92, 'copy', 'Dua lembar tumpuk', 'salin / duplikat', 'aksi'),
    (94, 'undo', 'Panah putar balik', 'urungkan', 'aksi'),
    (96, 'redo', 'Panah putar maju', 'ulangi', 'aksi'),
    (98, 'refresh', 'Panah melingkar', 'muat ulang / reset', 'aksi'),
    ('mirror:arrow_right', 'arrow_left', 'Panah kiri', 'kembali (cermin dari arrow_right)', 'navigasi'),
    (30, 'arrow_right', 'Panah kanan', 'lanjut', 'navigasi'),
    ('gen', 'chevron_left', 'Chevron kiri', 'kembali / buka sub-halaman', 'navigasi'),
    ('gen', 'chevron_right', 'Chevron kanan', 'masuk / lanjut', 'navigasi'),
    ('gen', 'chevron_up', 'Chevron atas', 'ciutkan', 'navigasi'),
    ('gen', 'chevron_down', 'Chevron bawah', 'bentangkan', 'navigasi'),
    ('gen', 'drag_handle', 'Grip enam titik 2x3', 'pegangan seret / urutkan', 'navigasi'),
    (115, 'menu', 'Tiga garis', 'menu utama', 'tampilan'),
    (116, 'grid', 'Empat kotak', 'tampilan grid / semua aplikasi', 'tampilan'),
    (117, 'list', 'Daftar berbutir', 'tampilan daftar', 'tampilan'),
    (102, 'filter', 'Corong', 'filter', 'tampilan'),
    (111, 'info', 'Huruf i dalam lingkaran', 'informasi', 'status'),
    (112, 'warning', 'Segitiga tanda seru', 'peringatan', 'status'),
    (113, 'help', 'Tanda tanya dalam lingkaran', 'bantuan', 'status'),
    (61, 'lock', 'Gembok tertutup', 'terkunci', 'status'),
    (62, 'lock_open', 'Gembok terbuka', 'tidak terkunci', 'status'),
    (13, 'star', 'Bintang', 'favorit', 'konten'),
    (2, 'heart', 'Hati', 'suka', 'konten'),
    (14, 'bookmark', 'Penanda buku', 'simpan', 'konten'),
    ('tag:89', 'pin', 'Paku payung', 'sematkan', 'konten'),
    (15, 'tag', 'Label harga', 'tag / label', 'konten'),
    (9, 'folder', 'Folder', 'folder', 'konten'),
    (6, 'home', 'Rumah', 'beranda', 'konten'),
    (7, 'user', 'Siluet orang', 'profil', 'konten'),
    (31, 'share', 'Tiga titik terhubung', 'bagikan', 'konten'),
    (16, 'bell', 'Lonceng', 'notifikasi', 'sistem'),
    (82, 'palette', 'Palet cat', 'tema / warna', 'sistem'),
    (35, 'image', 'Gambar pemandangan', 'wallpaper / gambar', 'sistem'),
    (54, 'cloud', 'Awan', 'cloud / cadangan', 'sistem'),
    (52, 'download', 'Panah turun ke baki', 'unduh / impor', 'sistem'),
    (53, 'upload', 'Panah naik dari baki', 'unggah / ekspor', 'sistem'),
]
RTL_MIRROR = {'arrow_left', 'arrow_right', 'chevron_left', 'chevron_right', 'undo', 'redo'}


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    tag_out = f'{OUT}/source/icons/tag'
    ui_out = f'{OUT}/source/icons/ui'

    # ---- canonical badge (cat head) from median alpha of the 100 tag icons -----
    alphas, gcent = [], []
    for i in range(1, 101):
        a = load(f'{SRC}/source/icons/tag/icon_{i:03d}.webp')
        alphas.append(a[..., 3] / 255)
        g = glyph_alpha(f'{SRC}/source/icons/tag/icon_{i:03d}.webp')
        if (g > .5).sum() > 50:
            x0, y0, x1, y1 = bbox(g)
            gcent.append(((x0 + x1) / 2, (y0 + y1) / 2))
    med = np.median(np.array(alphas), 0)
    sm = np.array(Image.fromarray((med * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))).astype(np.float32) / 255
    crisp = np.clip((sm - 0.5) * 14 + 0.5, 0, 1)             # clean silhouette, ~0.1px AA at 512
    bx0, by0, bx1, by1 = bbox(crisp)
    s_b = 120 / (by1 - by0)                                 # head height -> 120px, 4px margin
    n = round(512 * s_b * SS)
    big = np.array(Image.fromarray((crisp * 255).astype(np.uint8)).resize((n, n), Image.LANCZOS)).astype(np.float32) / 255
    k = n / 512
    canvas = np.zeros((BADGE * SS, BADGE * SS), np.float32)
    ox = round(BADGE * SS / 2 - (bx0 + bx1) / 2 * k)
    oy = round(BADGE * SS / 2 - (by0 + by1) / 2 * k)
    ys0, xs0 = max(0, -oy), max(0, -ox)
    h = min(n - ys0, BADGE * SS - max(0, oy)); w = min(n - xs0, BADGE * SS - max(0, ox))
    canvas[max(0, oy):max(0, oy) + h, max(0, ox):max(0, ox) + w] = big[ys0:ys0 + h, xs0:xs0 + w]
    badge = np.array(Image.fromarray((canvas * 255).astype(np.uint8)).resize((BADGE, BADGE), Image.BOX)).astype(np.float32) / 255
    save_mask(badge, f'{OUT}/source/components/tag_badge/badge_cat.webp')
    mcx = float(np.median([c[0] for c in gcent])); mcy = float(np.median([c[1] for c in gcent]))
    gx = (mcx * k + ox) / SS; gy = (mcy * k + oy) / SS      # glyph centre in badge px
    slot_x, slot_y = round(gx - GLYPH / 2), round(gy - GLYPH / 2)
    hb = bbox(badge, 0.5)
    print('badge bbox', hb, 'glyph centre', gx, gy, 'slot', slot_x, slot_y)

    # ---- tag glyphs ----------------------------------------------------------
    tag_items = []
    for old, name, vis, desc in TAG:
        g = to_canvas(glyph_alpha(f'{SRC}/source/icons/tag/icon_{old:03d}.webp'))
        save_mask(g, f'{tag_out}/icon_{name}.webp')
        tag_items.append(dict(file=f'icon_{name}.webp', name=name, visual=vis, description=desc, legacy_file=f'icon_{old:03d}.webp'))

    # ---- ui glyphs -----------------------------------------------------------
    ui_cache = {}
    def ui_src(old):
        if old not in ui_cache:
            ui_cache[old] = to_canvas(glyph_alpha(f'{SRC}/source/icons/ui/icon_{old:03d}.webp'))
        return ui_cache[old]

    ui_items = []
    for old, name, vis, desc, group in UI:
        legacy = None
        if old == 'gen':
            g = {'dots_vertical': lambda: draw_dots('vertical'), 'drag_handle': lambda: draw_dots('drag'),
                 'chevron_left': lambda: draw_chevron('left'), 'chevron_right': lambda: draw_chevron('right'),
                 'chevron_up': lambda: draw_chevron('up'), 'chevron_down': lambda: draw_chevron('down')}[name]()
            origin = 'generated'
        elif isinstance(old, str) and old.startswith('mirror:'):
            g = np.array(Image.fromarray((ui_src(30) * 255).astype(np.uint8)).transpose(Image.FLIP_LEFT_RIGHT)).astype(np.float32) / 255
            origin = 'mirrored'
        elif isinstance(old, str) and old.startswith('tag:'):
            o = int(old.split(':')[1])
            g = to_canvas(glyph_alpha(f'{SRC}/source/icons/tag/icon_{o:03d}.webp'))
            origin = 'converted'; legacy = f'tag/icon_{o:03d}.webp'
        else:
            g = ui_src(old); origin = 'converted'; legacy = f'ui/icon_{old:03d}.webp'
        save_mask(g, f'{ui_out}/icon_{name}.webp')
        ui_items.append(dict(file=f'icon_{name}.webp', name=name, visual=vis, description=desc, group=group,
                             origin=origin, mirror_in_rtl=name in RTL_MIRROR, legacy_file=legacy))

    meta = dict(glyph=GLYPH, badge=BADGE, slot=[slot_x, slot_y, GLYPH, GLYPH])
    json.dump(dict(meta=meta, tag=tag_items, ui=ui_items), open(f'{OUT}/_build_meta.json', 'w'), ensure_ascii=False, indent=1)


main()
