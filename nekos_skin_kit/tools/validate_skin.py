#!/usr/bin/env python3
"""Validasi skin NekOS (folder atau .zip) terhadap docs/03_REFERENSI_ASET.md.

Pemakaian:
  python3 -I tools/validate_skin.py <folder_skin | skin.zip>            # skin parsial (file hilang = warning)
  python3 -I tools/validate_skin.py default_skin --strict               # skin bawaan: semua file wajib ada

Exit code 1 bila ada ERROR. Hanya butuh: pillow, numpy.

Untuk .zip, aturan keamanan paket juga diperiksa (zip-slip, batas ukuran, ekstensi, symlink).
Batas di bawah adalah USULAN; samakan dengan konstanta di installer launcher.
"""
import json, os, re, stat, sys, tempfile, zipfile, shutil
import numpy as np
from PIL import Image

# ---- batas paket zip (usulan; ubah bersama kode launcher) ----
MAX_FILES = 400
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 32 * 1024 * 1024
MAX_RATIO = 100                      # uncompressed / compressed per file
MAX_IMAGE_SIDE = 4096                # sisi maks gambar apa pun (bom dekompresi gambar)
ALLOWED_EXT = {'.webp', '.png', '.json', '.md', '.txt'}

NAME_RE = re.compile(r'^icon_[a-z0-9]+(_[a-z0-9]+)*\.webp$')
ID_RE = re.compile(r'^[a-z0-9][a-z0-9._-]*$')
MOTIF_RE = re.compile(r'^motif_[a-z0-9]+(_[a-z0-9]+)*(@512)?\.png$')
GLYPH, LIVE, BADGE, MOTIF, MOTIF_S = 96, 80, 128, 1024, 512
KINDS = {'branding', 'tag_icon', 'ui_icon', 'wallpaper', 'background', 'frame', 'badge',
         'texture', 'illustration', 'font', 'other'}


class Report:
    def __init__(self):
        self.errors, self.warns = [], []

    def err(self, m): self.errors.append(m)
    def warn(self, m): self.warns.append(m)


def load(p):
    return np.array(Image.open(p).convert('RGBA'))


def bbox(alpha, t=8):
    ys, xs = np.where(alpha > t)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def check_mask(r, p, rel):
    a = load(p)
    op = a[..., 3] > 0
    if a[..., 3].max() == 0:
        r.err(f'{rel}: kosong (alpha 0 semua)')
    elif (a[..., :3][op] < 250).any():
        r.err(f'{rel}: bukan mask putih (RGB harus 255 di piksel berisi). Warna ditentukan runtime (tint).')
    return a


# ------------------------------------------------------------------ zip
def safe_extract(zpath, dest, r):
    """Ekstrak zip ke dest dengan semua pemeriksaan keamanan. Return True bila aman."""
    ok = True
    with zipfile.ZipFile(zpath) as z:
        infos = [i for i in z.infolist() if not i.is_dir()]
        if len(infos) > MAX_FILES:
            r.err(f'zip: {len(infos)} file > batas {MAX_FILES}'); return False
        if sum(i.file_size for i in infos) > MAX_TOTAL_BYTES:
            r.err(f'zip: total tidak terkompresi > {MAX_TOTAL_BYTES // 2**20} MB'); return False
        seen = set()
        for i in infos:
            n = i.filename
            parts = n.split('/')
            if n.startswith('/') or '\\' in n or re.match(r'^[A-Za-z]:', n) or '..' in parts or '' in parts[:-1]:
                r.err(f'zip: path tidak aman "{n}" (zip-slip / absolut / backslash)'); ok = False; continue
            if stat.S_ISLNK(i.external_attr >> 16):
                r.err(f'zip: symlink dilarang "{n}"'); ok = False; continue
            if n.lower() in seen:
                r.err(f'zip: nama ganda (case-insensitive) "{n}"'); ok = False; continue
            seen.add(n.lower())
            ext = os.path.splitext(n)[1].lower()
            if ext not in ALLOWED_EXT:
                r.err(f'zip: ekstensi "{ext}" tidak diizinkan ({n}). Izin: {sorted(ALLOWED_EXT)}'); ok = False; continue
            if i.file_size > MAX_FILE_BYTES:
                r.err(f'zip: {n} {i.file_size} byte > {MAX_FILE_BYTES // 2**20} MB'); ok = False; continue
            if i.compress_size and i.file_size / i.compress_size > MAX_RATIO and i.file_size > 1_000_000:
                r.err(f'zip: {n} rasio kompresi mencurigakan (zip bomb?)'); ok = False; continue
        if 'skin.json' not in {i.filename for i in infos}:
            tops = {i.filename.split('/')[0] for i in infos}
            hint = ' (skin.json ada di dalam folder pembungkus? Zip harus dibuat dari ISI folder.)' if len(tops) == 1 else ''
            r.err('zip: skin.json tidak ada di root' + hint); ok = False
        if not ok:
            return False
        for i in infos:                       # ekstrak, hitung byte NYATA (header zip bisa berbohong)
            out = os.path.join(dest, *i.filename.split('/'))
            os.makedirs(os.path.dirname(out), exist_ok=True)
            n = 0
            with z.open(i) as src, open(out, 'wb') as dst:
                while True:
                    b = src.read(65536)
                    if not b: break
                    n += len(b)
                    if n > MAX_FILE_BYTES:
                        r.err(f'zip: {i.filename} melebihi batas saat diekstrak'); return False
                    dst.write(b)
    return True


# ------------------------------------------------------------------ skin.json
def safe_rel(p):
    return isinstance(p, str) and p and not p.startswith('/') and '\\' not in p and '..' not in p.split('/')


def check_manifest(r, root, strict):
    mp = os.path.join(root, 'skin.json')
    if not os.path.exists(mp):
        r.err('skin.json tidak ada'); return None
    try:
        m = json.load(open(mp, encoding='utf-8'))
    except Exception as e:
        r.err(f'skin.json: bukan JSON valid ({e})'); return None
    if m.get('schema_version') != 1: r.err('skin.json: schema_version harus 1')
    sk = m.get('skin') or {}
    for k in ('id', 'name', 'version'):
        if not isinstance(sk.get(k), str) or not sk[k]: r.err(f'skin.json: skin.{k} wajib (string)')
    if isinstance(sk.get('id'), str) and not ID_RE.match(sk['id']):
        r.err('skin.json: skin.id harus ^[a-z0-9][a-z0-9._-]*$')
    if isinstance(sk.get('version'), str) and not re.match(r'^\d+\.\d+\.\d+', sk['version']):
        r.warn('skin.json: skin.version sebaiknya semver (1.0.0)')
    comp = m.get('compatibility') or {}
    if comp.get('contains_executable_code') not in (False, None):
        r.err('skin.json: contains_executable_code harus false')
    if comp.get('requires_network') not in (False, None):
        r.err('skin.json: requires_network harus false')
    ids = set()
    for a in m.get('assets', []):
        for k in ('id', 'kind', 'file'):
            if k not in a: r.err(f'skin.json: asset tanpa "{k}": {a}')
        if a.get('kind') not in KINDS: r.err(f'skin.json: kind "{a.get("kind")}" tidak dikenal')
        if a.get('id') in ids: r.err(f'skin.json: id asset ganda "{a.get("id")}"')
        ids.add(a.get('id'))
        f = a.get('file')
        if not safe_rel(f): r.err(f'skin.json: path file tidak aman "{f}"')
        elif not os.path.exists(os.path.join(root, f)): r.err(f'skin.json: file "{f}" terdaftar tetapi tidak ada')
    for mo in m.get('motifs', []):
        if not ID_RE.match(str(mo.get('id', ''))): r.err(f'skin.json: motif id tidak valid {mo.get("id")}')
        if mo.get('type') not in ('solid', 'line'): r.err(f'skin.json: motif {mo.get("id")} type harus solid|line')
        for key in ('file', 'file_small'):
            f = mo.get(key)
            if key == 'file' and not f: r.err(f'skin.json: motif {mo.get("id")} tanpa file')
            if f:
                if not safe_rel(f): r.err(f'skin.json: path motif tidak aman "{f}"')
                elif not os.path.exists(os.path.join(root, f)): r.err(f'skin.json: motif file "{f}" tidak ada')
    return m


# ------------------------------------------------------------------ ikon
def check_glyph_dir(r, root, sub, strict):
    d = os.path.join(root, 'icons', sub)
    if not os.path.isdir(d):
        (r.err if strict else r.warn)(f'icons/{sub}/ tidak ada'); return
    idx_p = os.path.join(d, 'icons_index.json' if sub == 'tag' else 'ui_index.json')
    idx = None
    if os.path.exists(idx_p):
        try:
            idx = json.load(open(idx_p, encoding='utf-8'))
        except Exception as e:
            r.err(f'icons/{sub}: index JSON tidak valid ({e})')
            idx = {}
    files = sorted(f for f in os.listdir(d) if f.endswith('.webp'))
    if idx is not None:
        check_index_catalog(r, idx, sub, f'icons/{sub}/')
        items = idx.get('items') if isinstance(idx, dict) else None
        if isinstance(items, list):
            listed = {
                item.get('file') for item in items
                if isinstance(item, dict) and isinstance(item.get('file'), str)
            }
        else:
            r.err(f'icons/{sub}: index harus memiliki daftar items yang valid')
            listed = set()
        for f in listed - set(files):
            (r.err if strict else r.warn)(f'icons/{sub}/{f}: ada di index tetapi file tidak ada')
        for f in set(files) - listed:
            r.err(f'icons/{sub}/{f}: file tidak terdaftar di index')
        items = idx.get('items') if isinstance(idx, dict) else None
        if isinstance(items, list) and idx.get('total') != len(items):
            r.err(f'icons/{sub}: "total" ({idx.get("total")}) != jumlah item ({len(items)})')
    elif strict:
        r.err(f'icons/{sub}: index json tidak ada')
    elif files:
        # skin parsial tanpa index: nama harus ada di katalog bawaan (dicek bila katalog tersedia)
        cat = DEFAULT_CATALOG.get(sub)
        if cat:
            for f in files:
                if f not in cat: r.warn(f'icons/{sub}/{f}: tidak ada di katalog bawaan -> akan DIABAIKAN launcher')
    for f in files:
        rel = f'icons/{sub}/{f}'
        if not NAME_RE.match(f): r.err(f'{rel}: nama harus icon_<bentuk>.webp (huruf kecil, angka, underscore)')
        if re.fullmatch(r'icon_\d+\.webp', f): r.err(f'{rel}: nama numerik dilarang')
        p = os.path.join(d, f)
        im = Image.open(p)
        if im.size != (GLYPH, GLYPH):
            r.err(f'{rel}: ukuran {im.size}, harus {GLYPH}x{GLYPH}'); continue
        if im.mode != 'RGBA': r.err(f'{rel}: mode {im.mode}, harus RGBA')
        a = check_mask(r, p, rel)
        if a[..., 3].max() == 0: continue
        x0, y0, x1, y1 = bbox(a[..., 3])
        m = (GLYPH - LIVE) // 2
        if x0 < m - 1 or y0 < m - 1 or x1 > GLYPH - m + 1 or y1 > GLYPH - m + 1:
            r.err(f'{rel}: isi ({x0},{y0})-({x1},{y1}) keluar dari live area {m}..{GLYPH - m}')
        if max(x1 - x0, y1 - y0) < 24: r.warn(f'{rel}: glyph sangat kecil ({x1 - x0}x{y1 - y0}px)')
        if os.path.getsize(p) > 20_000: r.warn(f'{rel}: {os.path.getsize(p)} byte > 20 KB')


# ------------------------------------------------------------------ komponen
def check_components(r, root, strict):
    c = os.path.join(root, 'components')
    miss = r.err if strict else r.warn
    p = os.path.join(c, 'tag_badge', 'badge_cat.webp')
    meta = os.path.join(c, 'tag_badge', 'badge.json')
    if os.path.exists(p):
        im = Image.open(p)
        if im.size != (BADGE, BADGE): r.err(f'badge_cat.webp: {im.size}, harus {BADGE}x{BADGE}')
        a = check_mask(r, p, 'badge_cat.webp')
        if a[..., 3].max() > 0:
            x0, y0, x1, y1 = bbox(a[..., 3])
            if min(x0, y0, BADGE - x1, BADGE - y1) < 3:
                r.err('badge_cat.webp: siluet menyentuh tepi (margin minimum 4px)')
            if os.path.exists(meta):
                s = json.load(open(meta))['glyph_slot']
                if s['x'] < 0 or s['y'] < 0 or s['x'] + s['w'] > BADGE or s['y'] + s['h'] > BADGE:
                    r.err('badge.json: glyph_slot keluar kanvas')
                # slot harus di dalam siluet dengan margin >= 8 px (spec §4) -> cek pusat slot terisi
                cx, cy = s['x'] + s['w'] // 2, s['y'] + s['h'] // 2
                if a[cy, cx, 3] < 200: r.err('badge.json: pusat glyph_slot jatuh di area transparan badge')
            else:
                r.warn('badge_cat.webp diganti tanpa badge.json: launcher akan memakai slot bawaan (16,22,96,96). '
                         'Bila bentuk badge berbeda, sertakan badge.json.')
    elif os.path.exists(meta):
        r.warn('badge.json ada tetapi badge_cat.webp tidak ada')
    else:
        miss('components/tag_badge/badge_cat.webp tidak ada')

    td = os.path.join(c, 'top_frame')
    p4 = os.path.join(td, 'topframe_spritesheet@4x.png')
    if os.path.exists(p4):
        im = Image.open(p4)
        if im.size != (536, 256): r.err(f'topframe_spritesheet@4x.png: {im.size}, harus 536x256')
        else:
            a = check_mask(r, p4, 'topframe_spritesheet@4x.png')
            slices = {'left_cap': (0, 128), 'bar_tile': (136, 152), 'head': (160, 400), 'right_cap': (408, 536)}
            for n, (x0, x1) in slices.items():
                if a[:, x0:x1, 3].max() == 0: r.err(f'topframe @4x: potongan {n} kosong')
            for gx0, gx1 in ((128, 136), (152, 160), (400, 408)):
                if a[:, gx0:gx1, 3].max() > 0: r.err(f'topframe @4x: celah transparan x={gx0}..{gx1} harus kosong')
            bar = a[:, 136:152, 3].astype(int)
            if (bar != bar[:, :1]).any(): r.warn('topframe @4x: bar_tile tidak polos horizontal (di-stretch, motif akan melar)')
            ys = np.where(a[:, 136:152, 3].max(axis=1) > 8)[0]
            if len(ys) and not (ys.min() >= 100 and ys.max() <= 200):
                r.warn(f'topframe @4x: band bar y={ys.min()}..{ys.max() + 1}, bawaan 128..176 (anchor dialog y=152)')
            hx = np.where(a[:, 160:400, 3].max(axis=0) > 8)[0]
            if len(hx) and abs((hx.min() + hx.max()) / 2 - 119.5) > 3: r.warn('topframe @4x: kepala tidak terpusat horizontal di slot head')
            if a[0, :, 3].max() > 8: r.warn('topframe @4x: isi menyentuh y=0 (telinga tidak boleh melewati atas)')
    else:
        miss('components/top_frame/topframe_spritesheet@4x.png tidak ada')
    for fn, sf in (('topframe_spritesheet.png', 1), ('topframe_spritesheet@2x.png', 2)):
        sp = os.path.join(td, fn)
        if os.path.exists(sp):
            im = Image.open(sp)
            if im.size != (134 * sf, 64 * sf): r.err(f'{fn}: {im.size}, harus {(134 * sf, 64 * sf)}')
            check_mask(r, sp, fn)
    atl = os.path.join(td, 'topframe_atlas.json')
    if os.path.exists(atl):
        try:
            for k, s in json.load(open(atl))['sheets'].items():
                if not os.path.exists(os.path.join(td, s['file'])) and (strict or k == '4x'):
                    r.err(f'top_frame: atlas menyebut {s["file"]} tetapi file tidak ada')
        except Exception as e:
            r.err(f'topframe_atlas.json: {e}')

    sp = os.path.join(c, 'tag_frame', 'frame_spritesheet.webp')
    if os.path.exists(sp):
        im = Image.open(sp)
        if im.size != (512, 240): r.err(f'frame_spritesheet.webp: {im.size}, harus 512x240')
        else:
            a = check_mask(r, sp, 'frame_spritesheet.webp')
            mid = a[144:160, :, 3].astype(int)
            if (mid != mid[:1]).any(): r.err('frame_spritesheet.webp: slice middle (y 144..160) harus identik di semua barisnya')
            if a[143, :, 3].max() == 0 or a[160, :, 3].max() == 0:
                r.warn('frame_spritesheet.webp: garis sisi tidak menyambung di batas slice (patahan saat di-stretch)')
            elif not ((a[143, :, 3] > 8) == (a[144, :, 3] > 8)).all() or not ((a[159, :, 3] > 8) == (a[160, :, 3] > 8)).all():
                r.warn('frame_spritesheet.webp: sisi di batas top/middle/bottom tidak sejajar (patahan)')
    else:
        miss('components/tag_frame/frame_spritesheet.webp tidak ada')


# ------------------------------------------------------------------ motif
def check_motifs(r, root, strict):
    d = os.path.join(root, 'motifs')
    if not os.path.isdir(d):
        if strict: r.err('motifs/ tidak ada')
        return
    for f in sorted(os.listdir(d)):
        if f.endswith('.json'): continue
        rel = f'motifs/{f}'
        if not MOTIF_RE.match(f): r.err(f'{rel}: nama harus motif_<nama>.png atau motif_<nama>@512.png'); continue
        p = os.path.join(d, f)
        want = MOTIF_S if f.endswith('@512.png') else MOTIF
        im = Image.open(p)
        if im.size != (want, want): r.err(f'{rel}: {im.size}, harus {want}x{want}'); continue
        a = check_mask(r, p, rel)
        al = a[..., 3].astype(float) / 255
        cov = al.mean()
        if cov < 0.02 or cov > 0.5: r.warn(f'{rel}: coverage {cov:.1%} tidak wajar (solid 15-30%, garis 5-15%)')
        # jahitan: selisih tepi-ke-tepi vs selisih antar-kolom biasa
        sx, bx = np.abs(al[:, -1] - al[:, 0]).mean(), np.abs(al[:, 1:] - al[:, :-1]).mean()
        sy, by = np.abs(al[-1] - al[0]).mean(), np.abs(al[1:] - al[:-1]).mean()
        if sx > 3 * bx + 0.02 or sy > 3 * by + 0.02:
            r.warn(f'{rel}: tepi tidak menyambung (seam X {sx:.3f}/{bx:.3f}, Y {sy:.3f}/{by:.3f}); cek tile 2x2')
        if not f.endswith('@512.png') and not os.path.exists(os.path.join(d, f.replace('.png', '@512.png'))):
            r.warn(f'{rel}: tidak ada versi @512 (untuk perangkat ringan)')


DEFAULT_CATALOG = {}
DEFAULT_ID_NAMES = {}


def load_default_catalog():
    here = os.path.dirname(os.path.abspath(__file__))
    base = os.path.join(os.path.dirname(here), 'default_skin', 'icons')
    for sub, fn in (('tag', 'icons_index.json'), ('ui', 'ui_index.json')):
        p = os.path.join(base, sub, fn)
        if os.path.exists(p):
            items = json.load(open(p, encoding='utf-8'))['items']
            DEFAULT_CATALOG[sub] = {i['file'] for i in items}
            DEFAULT_ID_NAMES[sub] = {i['id']: i['name'] for i in items}


def check_index_catalog(r, idx, sub, rel):
    """Pastikan identitas ID -> nama tidak berubah dari katalog bawaan."""
    if not isinstance(idx, dict):
        r.err(f'{rel}index: akar JSON harus berupa objek')
        return
    items = idx.get('items')
    if not isinstance(items, list):
        r.err(f'{rel}index: field items harus berupa daftar')
        return

    canonical = DEFAULT_ID_NAMES.get(sub, {})
    seen_ids, seen_names = set(), set()
    for item in items:
        if not isinstance(item, dict):
            r.err(f'{rel}index: setiap item harus berupa objek')
            continue
        glyph_id, name, filename = item.get('id'), item.get('name'), item.get('file')
        if type(glyph_id) is not int:
            r.err(f'{rel}index: ID glyph harus berupa integer ({glyph_id!r})')
            continue
        if glyph_id in seen_ids:
            r.err(f'{rel}index: ID glyph duplikat {glyph_id}')
        seen_ids.add(glyph_id)
        if not isinstance(name, str) or not name:
            r.err(f'{rel}index: nama glyph untuk ID {glyph_id} tidak valid')
            continue
        if name in seen_names:
            r.err(f'{rel}index: nama glyph duplikat "{name}"')
        seen_names.add(name)

        expected = canonical.get(glyph_id)
        if expected is None:
            r.err(f'{rel}index: ID {glyph_id} tidak dikenal katalog bawaan')
        elif name != expected:
            r.err(f'{rel}index: ID {glyph_id} harus bermakna "{expected}", bukan "{name}"')

        expected_file = f'icon_{name}.webp'
        if filename != expected_file:
            r.err(f'{rel}index: file untuk "{name}" harus "{expected_file}", bukan "{filename}"')


def check_all_images(r, root):
    for dp, _, fn in os.walk(root):
        for f in fn:
            if f.lower().endswith(('.png', '.webp')):
                rel = os.path.relpath(os.path.join(dp, f), root).replace(os.sep, '/')
                try:
                    with Image.open(os.path.join(dp, f)) as im:
                        w, h = im.size
                        if w > MAX_IMAGE_SIDE or h > MAX_IMAGE_SIDE:
                            r.err(f'{rel}: {w}x{h} melebihi batas {MAX_IMAGE_SIDE}px per sisi')
                        else:
                            im.load()
                except Exception as e:
                    r.err(f'{rel}: bukan gambar valid ({e.__class__.__name__})')


def validate(path, strict=False):
    """Return (Report, skin_manifest|None)."""
    r = Report()
    load_default_catalog()
    tmp = None
    root = path
    if os.path.isfile(path):
        if not path.lower().endswith('.zip'):
            r.err('file harus berekstensi .zip'); return r, None
        tmp = tempfile.mkdtemp(prefix='nekos_skin_')
        try:
            if not zipfile.is_zipfile(path): r.err('bukan file zip yang valid'); return r, None
            if not safe_extract(path, tmp, r): return r, None
        except zipfile.BadZipFile:
            r.err('zip rusak'); return r, None
        root = tmp
    try:
        man = check_manifest(r, root, strict)
        check_all_images(r, root)
        check_glyph_dir(r, root, 'tag', strict)
        check_glyph_dir(r, root, 'ui', strict)
        check_components(r, root, strict)
        check_motifs(r, root, strict)
        known = {'skin.json', 'icons', 'components', 'motifs', 'README.md', 'LICENSE.txt', 'LICENSE.md', 'preview.png', 'preview.webp'}
        for e in sorted(os.listdir(root)):
            if e not in known: r.warn(f'{e}: tidak dikenal launcher (diabaikan)')
    finally:
        if tmp: shutil.rmtree(tmp, ignore_errors=True)
    return r, man


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if not args:
        print(__doc__); sys.exit(2)
    r, _ = validate(args[0], strict='--strict' in sys.argv)
    for w in r.warns: print('WARN ', w)
    for e in r.errors: print('ERROR', e)
    print(f'\n{len(r.errors)} error, {len(r.warns)} warning')
    sys.exit(1 if r.errors else 0)


if __name__ == '__main__':
    main()
