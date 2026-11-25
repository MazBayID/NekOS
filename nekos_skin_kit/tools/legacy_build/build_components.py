#!/usr/bin/env python3
"""Component masks (top_frame, tag_frame), previews and authoring templates.
Usage: python3 -I build_components.py <orig_dir> <out_dir>
"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw

SRC, OUT = sys.argv[1], sys.argv[2]
K = 1.6   # edge sharpening factor (originals have ~3px soft ramps at 4x)


def mask_from(path):
    a = np.array(Image.open(path).convert('RGBA')).astype(np.float32)[..., 3] / 255
    a = np.clip((a - 0.5) * K + 0.5, 0, 1)
    return a


def write(a, path, lossless_webp=False):
    u = np.clip(a * 255 + .5, 0, 255).astype(np.uint8)
    im = Image.fromarray(np.dstack([np.full_like(u, 255)] * 3 + [u]), 'RGBA')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if path.endswith('.webp'):
        im.save(path, 'WEBP', lossless=True, exact=True, method=6)
    else:
        im.save(path, 'PNG', optimize=True)
    return im


def bbox(a, t=0.5):
    ys, xs = np.where(a > t); return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def tint(im, rgb):
    c = Image.new('RGBA', im.size, rgb + (255,)); c.putalpha(im.split()[3]); return c


def main():
    # ------------------------------------------------------------ top frame
    t4 = mask_from(f'{SRC}/source/components/top_frame/topframe_spritesheet@4x.png')
    im4 = write(t4, f'{OUT}/source/components/top_frame/topframe_spritesheet@4x.png')
    for s, name in ((2, '@2x'), (1, '')):
        sz = (im4.width // (4 // s), im4.height // (4 // s))
        im = im4.resize(sz, Image.LANCZOS)
        im.save(f'{OUT}/source/components/top_frame/topframe_spritesheet{name}.png', optimize=True)
    fr = json.load(open(f'{SRC}/source/components/top_frame/topframe_atlas.json'))['sheets']['4x']['frames']
    print('TOP content bounds @4x (px):')
    for k, f in fr.items():
        b = bbox(t4[f['y']:f['y'] + f['h'], f['x']:f['x'] + f['w']])
        print('  ', k, f, '->', b)

    # assemble preview at 360dp wide (1440px @4x)
    W = 1440
    bar_w = (W - 2 * 128 - 240) // 2
    def crop(k): f = fr[k]; return im4.crop((f['x'], f['y'], f['x'] + f['w'], f['y'] + f['h']))
    row = Image.new('RGBA', (W, 256), (0, 0, 0, 0))
    row.alpha_composite(crop('left_cap'), (0, 0))
    row.alpha_composite(crop('bar_tile').resize((bar_w, 256), Image.BILINEAR), (128, 0))
    row.alpha_composite(crop('head'), (128 + bar_w, 0))
    row.alpha_composite(crop('bar_tile').resize((bar_w, 256), Image.BILINEAR), (128 + bar_w + 240, 0))
    row.alpha_composite(crop('right_cap'), (W - 128, 0))
    prev = Image.new('RGBA', (W, 256 + 320), (235, 225, 210, 255))
    body = Image.new('RGBA', (W - 64, 320 + 64), (252, 250, 245, 255)); prev.alpha_composite(body, (32, 256 - 128 + 32))
    prev.alpha_composite(tint(row, (110, 60, 230)), (0, 0))
    os.makedirs(f'{OUT}/previews', exist_ok=True)
    prev.convert('RGB').save(f'{OUT}/previews/top_frame_360dp_tinted.png', optimize=True)

    # ------------------------------------------------------------ tag frame
    g = mask_from(f'{SRC}/source/components/tag_frame/frame_spritesheet_smooth.webp')
    write(g, f'{OUT}/source/components/tag_frame/frame_spritesheet.webp')
    print('TAG sheet', g.shape, 'bbox', bbox(g))
    # stroke & head measures
    row_ = g[100]; xs = np.where(row_ > .5)[0]; print('  row100 first/last', xs[0], xs[-1])
    run = 0
    for x in xs:
        if x - xs[0] == run: run += 1
        else: break
    print('  left stroke thickness @ row100:', run)
    mid = g[148]; xs = np.where(mid > .5)[0]; print('  mid row 148 opaque x:', xs.tolist()[:6], '...', xs.tolist()[-6:])
    toprow = g[60]; xs = np.where(toprow > .5)[0]
    gaps = np.where(np.diff(xs) > 1)[0]
    print('  row60 segments:', [(int(xs[0]) if i == 0 else int(xs[gaps[i-1] + 1]), int(xs[gaps[i]]) if i < len(gaps) else int(xs[-1])) for i in range(len(gaps) + 1)])
    hb = bbox(g[:144, 150:370]); print('  head bbox (x offset 150):', hb)

    # tag-frame preview: three heights tinted
    top, mid_, bot = g[0:144], g[144:160], g[160:240]
    def frame(h):
        mh = h - 144 - 80
        m = np.array(Image.fromarray((mid_ * 255).astype(np.uint8)).resize((512, mh), Image.BILINEAR)).astype(np.float32) / 255
        return np.vstack([top, m, bot])
    imgs = []
    for h in (240, 480, 800):
        a = frame(h); u = (a * 255).astype(np.uint8)
        im = Image.fromarray(np.dstack([np.full_like(u, 255)] * 3 + [u]), 'RGBA')
        imgs.append(tint(im, (110, 60, 230)))
    P = Image.new('RGBA', (512 * 3 + 80, 800 + 40), (235, 225, 210, 255))
    for i, im in enumerate(imgs): P.alpha_composite(im, (20 + i * 532, 20))
    P.convert('RGB').save(f'{OUT}/previews/tag_frame_heights_tinted.png', optimize=True)

    # ------------------------------------------------------------ templates
    os.makedirs(f'{OUT}/templates', exist_ok=True)
    def guide(size, scale, rects, lines, path, label):
        im = Image.new('RGBA', (size[0] * scale, size[1] * scale), (255, 255, 255, 255))
        d = ImageDraw.Draw(im)
        for x in range(0, size[0] + 1, 8):
            d.line([(x * scale, 0), (x * scale, size[1] * scale)], fill=(235, 235, 240, 255))
        for y in range(0, size[1] + 1, 8):
            d.line([(0, y * scale), (size[0] * scale, y * scale)], fill=(235, 235, 240, 255))
        for (x, y, w, h, col, nm) in rects:
            d.rectangle([x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1], outline=col, width=2)
            d.text((x * scale + 4, y * scale + 3), nm, fill=col)
        for (p, q, col) in lines:
            d.line([(p[0] * scale, p[1] * scale), (q[0] * scale, q[1] * scale)], fill=col, width=1)
        d.text((4, size[1] * scale - 14), label, fill=(120, 120, 120, 255))
        im.save(path, optimize=True)
    RED, BLUE, GRN = (220, 40, 40, 255), (40, 90, 220, 255), (30, 150, 70, 255)
    guide((96, 96), 6, [(0, 0, 96, 96, RED, 'canvas 96'), (8, 8, 80, 80, BLUE, 'live area 80')],
          [((48, 0), (48, 96), GRN), ((0, 48), (96, 48), GRN)], f'{OUT}/templates/glyph_96.png', '1 grid = 8px (2dp)')
    guide((128, 128), 5, [(0, 0, 128, 128, RED, 'canvas 128'), (4, 4, 120, 120, BLUE, 'silhouette safe 4px'),
                          (16, 22, 96, 96, GRN, 'glyph slot 96 @(16,22)')],
          [((64, 0), (64, 128), (200, 200, 200, 255))], f'{OUT}/templates/badge_128.png', '1 grid = 8px (2dp)')
    guide((536, 256), 2, [(0, 0, 128, 256, RED, 'left_cap 128x256'), (136, 0, 16, 256, BLUE, 'bar_tile 16x256'),
                          (160, 0, 240, 256, GRN, 'head 240x256'), (408, 0, 128, 256, RED, 'right_cap 128x256')],
          [((0, 128), (536, 128), (200, 200, 200, 255)), ((0, 176), (536, 176), (200, 200, 200, 255))],
          f'{OUT}/templates/topframe_spritesheet@4x.png', 'sheet 536x256 | gaps 8px | bar band y=128..176 (guide)')
    guide((512, 240), 2, [(0, 0, 512, 144, RED, 'top 512x144 fixed'), (0, 144, 512, 16, BLUE, 'middle 512x16 stretch_y'),
                          (0, 160, 512, 80, GRN, 'bottom 512x80 fixed')],
          [], f'{OUT}/templates/tag_frame_sheet.png', 'sheet 512x240')


main()
