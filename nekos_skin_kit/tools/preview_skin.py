#!/usr/bin/env python3
"""Render lembar pratinjau sebuah skin (folder atau .zip). Aset yang tidak ada di skin diambil dari default_skin.

  python3 -I tools/preview_skin.py my_skin --out previews/my_skin   # menulis 4 PNG
Hasil: *_tags.png, *_ui.png, *_frames.png, *_motifs.png (tile 2x2 tiap motif)

Warna di sini hanya CONTOH (bukan palet NekOS). Tes final tetap di perangkat dengan 12 warna Shared UI Color.
Skala TagFrame memakai asumsi 4 px = 1 dp (belum diverifikasi dari runtime).
"""
import argparse, json, os, shutil, sys, tempfile, zipfile
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
DEF = os.path.join(os.path.dirname(HERE), 'default_skin')
SAMPLE = {'Purple': '#6B3FE6', 'Ocean': '#1E6F8C', 'Silver': '#C9CED6', 'Dark': '#23262B'}
CREAM = '#F5F2ED'

ap = argparse.ArgumentParser()
ap.add_argument('skin'); ap.add_argument('--out', default='preview')
a = ap.parse_args()
tmp = None
root = a.skin
if os.path.isfile(root):
    tmp = tempfile.mkdtemp(); zipfile.ZipFile(root).extractall(tmp); root = tmp


def P(rel):
    p = os.path.join(root, rel)
    return p if os.path.exists(p) else os.path.join(DEF, rel)


def tint(img, color):
    img = img.convert('RGBA')
    solid = Image.new('RGBA', img.size, color)
    solid.putalpha(img.getchannel('A'))
    return solid


def sheet(w, h, bg): return Image.new('RGBA', (w, h), bg)


def label(d, xy, t, fill='#888'): d.text(xy, t, fill=fill)


# ---------- tag icons ----------
badge = Image.open(P('components/tag_badge/badge_cat.webp')).convert('RGBA')
slot = json.load(open(P('components/tag_badge/badge.json')))['glyph_slot']
tags = sorted(f for f in set(os.listdir(os.path.join(DEF, 'icons/tag'))) if f.endswith('.webp'))
cols = 9; cell = 148
rows = -(-len(tags) // cols)
out = sheet(cols * cell, rows * cell * len(SAMPLE) + 40 * len(SAMPLE), '#EDE6DA')
d = ImageDraw.Draw(out); y0 = 0
for cn, col in SAMPLE.items():
    label(d, (6, y0 + 6), f'badge = {cn} {col} (contoh) | glyph = on_badge {CREAM}', '#444')
    y0 += 30
    b = tint(badge, col)
    for i, t in enumerate(tags):
        g = Image.open(P(f'icons/tag/{t}')).convert('RGBA') if os.path.exists(P(f'icons/tag/{t}')) else None
        c = Image.new('RGBA', (128, 128), (0, 0, 0, 0)); c.alpha_composite(b)
        c.alpha_composite(tint(g, CREAM), (slot['x'], slot['y']))
        out.alpha_composite(c.resize((128, 128)), ((i % cols) * cell + 10, y0 + (i // cols) * cell))
    y0 += rows * cell + 10
out.crop((0, 0, out.width, y0)).convert('RGB').save(a.out + '_tags.png')

# ---------- ui icons ----------
ui = sorted(f for f in os.listdir(os.path.join(DEF, 'icons/ui')) if f.endswith('.webp'))
cols = 12; cell = 120; rows = -(-len(ui) // cols)
out = sheet(cols * cell, rows * cell * 2, '#FAF7F2'); d = ImageDraw.Draw(out)
d.rectangle((0, rows * cell, out.width, out.height), fill='#23262B')
for k, (fg) in enumerate(('#2B2B2B', '#F2EFE9')):
    for i, f in enumerate(ui):
        g = Image.open(P(f'icons/ui/{f}'))
        out.alpha_composite(tint(g, fg).resize((96, 96)), ((i % cols) * cell + 12, k * rows * cell + (i // cols) * cell + 12))
out.convert('RGB').save(a.out + '_ui.png')

# ---------- frames ----------
sp = Image.open(P('components/top_frame/topframe_spritesheet@4x.png')).convert('RGBA')
S = {'left_cap': (0, 128), 'bar': (136, 152), 'head': (160, 400), 'right_cap': (408, 536)}
cut = {k: sp.crop((x0, 0, x1, 256)) for k, (x0, x1) in S.items()}


def top_frame(wdp, color):
    W = wdp * 4; bar = (W - 2 * 128 - 240) // 2
    im = Image.new('RGBA', (W, 256), (0, 0, 0, 0)); x = 0
    for part, w in (('left_cap', 128), ('bar', bar), ('head', 240), ('bar', bar), ('right_cap', 128)):
        if w <= 0: continue
        im.alpha_composite(cut[part].resize((w, 256), Image.LANCZOS) if part == 'bar' else cut[part], (x, 0)); x += w
    return tint(im, color)


tf = Image.open(P('components/tag_frame/frame_spritesheet.webp')).convert('RGBA')


def tag_frame(h, color):
    top, mid, bot = tf.crop((0, 0, 512, 144)), tf.crop((0, 144, 512, 160)), tf.crop((0, 160, 512, 240))
    im = Image.new('RGBA', (512, h), (0, 0, 0, 0)); im.alpha_composite(top)
    if h > 224: im.alpha_composite(mid.resize((512, h - 224), Image.NEAREST), (0, 144))
    im.alpha_composite(bot, (0, h - 80))
    return tint(im, color)


out = sheet(900, 1000, '#EAE2D4'); d = ImageDraw.Draw(out)
y = 10
for wdp in (360, 124):
    label(d, (10, y), f'TopFrame {wdp} dp (Purple contoh)', '#444'); y += 16
    fr = top_frame(wdp, SAMPLE['Purple'])
    out.alpha_composite(fr.resize((fr.width // 2, fr.height // 2), Image.LANCZOS), (10, y)); y += 140
x = 10; label(d, (10, y), 'TagFrame tinggi 224 / 480 / 720 px (asumsi 4 px = 1 dp)', '#444'); y += 18
for h in (224, 480, 720):
    fr = tag_frame(h, SAMPLE['Ocean']); fr = fr.resize((fr.width // 2, fr.height // 2), Image.LANCZOS)
    out.alpha_composite(fr, (x, y)); x += fr.width + 20
out.convert('RGB').crop((0, 0, out.width, y + 370)).save(a.out + '_frames.png')

# ---------- motifs ----------
mdir = os.path.join(root, 'motifs') if os.path.isdir(os.path.join(root, 'motifs')) else os.path.join(DEF, 'motifs')
ms = sorted(f for f in os.listdir(mdir) if f.endswith('@512.png'))
out = sheet(1024, 1024 * -(-len(ms) // 2) // 1, '#1E506E')
for i, f in enumerate(ms):
    m = tint(Image.open(os.path.join(mdir, f)), '#FFFFFF')
    for dx in (0, 256):
        for dy in (0, 256):
            out.alpha_composite(m.resize((256, 256), Image.LANCZOS), ((i % 2) * 512 + dx, (i // 2) * 512 + dy))
out.convert('RGB').crop((0, 0, 1024, 512 * -(-len(ms) // 2))).save(a.out + '_motifs.png')
if tmp: shutil.rmtree(tmp, ignore_errors=True)
print('OK ->', a.out + '_{tags,ui,frames,motifs}.png')
