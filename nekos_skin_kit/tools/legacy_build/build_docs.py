#!/usr/bin/env python3
"""Writes indexes, component json, specs, docs and tools into <out_dir>.
Usage: python3 -I build_docs.py <orig_dir> <out_dir>"""
import json, os, shutil, sys

SRC, OUT = sys.argv[1], sys.argv[2]
meta = json.load(open(f'{OUT}/_build_meta.json'))
slot = meta['meta']['slot']


def w(path, text):
    os.makedirs(os.path.dirname(f'{OUT}/{path}'), exist_ok=True)
    open(f'{OUT}/{path}', 'w', encoding='utf-8').write(text)


def wj(path, obj):
    w(path, json.dumps(obj, ensure_ascii=False, indent=2) + '\n')


# ------------------------------------------------------------------ indexes
tag_items = [dict(id=i + 1, **{k: v for k, v in it.items() if k != 'legacy_file'}, legacy_file=it['legacy_file'])
             for i, it in enumerate(meta['tag'])]
ui_items = [dict(id=i + 1, **it) for i, it in enumerate(meta['ui'])]
common = dict(canvas_px={'width': 96, 'height': 96}, live_area_px={'x': 8, 'y': 8, 'width': 80, 'height': 80},
              color_mode='alpha_mask_white', format='webp_lossless')
wj('source/icons/tag/icons_index.json', dict(
    category='tag_icon', total=len(tag_items), size='96x96', **common,
    tint_role='on_badge', composition='badge_cat.webp (tint: Shared UI Color) + glyph di glyph_slot (tint: on_badge)',
    items=tag_items))
wj('source/icons/ui/ui_index.json', dict(
    category='ui_icon', total=len(ui_items), size='96x96', **common,
    tint_role='foreground', items=ui_items))

md = ['# Katalog Ikon Tag NekOS', '',
      f'**Kategori:** `tag_icon` · **Jumlah:** {len(tag_items)} · **Kanvas:** 96 × 96 px (24 dp @4x) · **Live area:** 80 × 80 px',
      '**Format:** WebP lossless, mask putih + alpha (warna ditentukan runtime / skin).', '',
      'Ikon tag = `components/tag_badge/badge_cat.webp` (tint: Shared UI Color) + glyph file ini di atasnya '
      f'(tint: warna "on badge", default krem `#F5F2ED`). Glyph ditaruh di slot `x={slot[0]} y={slot[1]} {slot[2]}×{slot[3]}` pada badge 128 px.',
      '', 'Nama file = bentuk gambar: `icon_<bentuk>.webp`. Nomor urut hanya ada di tabel ini.', '',
      '| No | File | Bentuk | Tampilan | Pemakaian |', '|---|---|---|---|---|']
for it in tag_items:
    md.append(f"| {it['id']} | `{it['file']}` | `{it['name']}` | {it['visual']} | {it['description']} |")
w('source/icons/tag/icons_index.md', '\n'.join(md) + '\n')

md = ['# Katalog UI Icons NekOS', '',
      f'**Kategori:** `ui_icon` · **Jumlah:** {len(ui_items)} · **Kanvas:** 96 × 96 px (24 dp @4x) · **Live area:** 80 × 80 px',
      '**Format:** WebP lossless, mask putih + alpha. Tint runtime = warna foreground tema.', '',
      '`RTL` = ikon harus dicerminkan di layout kanan-ke-kiri. `Asal`: converted = diambil dari ikon lama, '
      'generated = digambar ulang secara geometris (tidak ada di set lama), mirrored = cermin dari ikon lain.', '']
groups = []
for it in ui_items:
    if it['group'] not in groups: groups.append(it['group'])
for g in groups:
    md += [f'## {g.capitalize()}', '', '| No | File | Bentuk | Tampilan | Pemakaian | RTL | Asal |', '|---|---|---|---|---|---|---|']
    for it in ui_items:
        if it['group'] == g:
            md.append(f"| {it['id']} | `{it['file']}` | `{it['name']}` | {it['visual']} | {it['description']} | {'ya' if it['mirror_in_rtl'] else '-'} | {it['origin']} |")
    md.append('')
w('source/icons/ui/ui_index.md', '\n'.join(md))

# ---------------------------------------------------------- component json
wj('source/components/tag_badge/badge.json', dict(
    component_id='tag_badge', schema_version=1,
    file='badge_cat.webp', canvas_px={'width': 128, 'height': 128}, display_dp={'width': 32, 'height': 32},
    scale_note='1 dp = 4 px pada master (xxxhdpi). Turunan densitas dibuat runtime dengan downscale.',
    silhouette_bounds_px={'x': 6, 'y': 4, 'width': 116, 'height': 120},
    safe_margin_px=4, color_mode='alpha_mask_white', tint_role='shared_ui',
    glyph_slot={'x': slot[0], 'y': slot[1], 'w': slot[2], 'h': slot[3],
                'note': 'Glyph 96x96 digambar 1:1 (atau diskalakan proporsional) ke dalam slot ini. Skin yang mengganti bentuk badge WAJIB menyesuaikan slot ini.'},
    glyph_tint_role='on_badge', default_on_badge_color='#F5F2ED'))

atlas = json.load(open(f'{SRC}/source/components/top_frame/topframe_atlas.json'))
atlas['color_mode'] = 'alpha_mask_white'
atlas['tint_role'] = 'shared_ui'
atlas['master_scale'] = 4
atlas['derived_sheets_note'] = '1x dan 2x diturunkan dari 4x (downscale Lanczos); skin cukup menyediakan 4x lalu menjalankan tools/make_derived_sheets (atau runtime downscale).'
wj('source/components/top_frame/topframe_atlas.json', atlas)
formula = json.load(open(f'{SRC}/source/components/top_frame/topframe_formula.json'))
formula['rendering']['color'] = ('Spritesheet adalah mask putih + alpha. Runtime men-tint dengan warna Shared UI Color (SRC_IN). '
                                 'Tidak memerlukan recolor berbasis hue.')
formula['measured_4x'] = {
    'left_cap_content_px': {'x': 0, 'y': 128, 'w': 128, 'h': 96, 'note': 'sudut luar berbentuk elips 128x96 px = 32x24 dp (bukan lingkaran)'},
    'bar_band_px': {'y': 128, 'h': 48, 'note': 'ketebalan bar 12 dp; seluruh area y<128 di cap/bar kosong (ruang telinga kepala)'},
    'head_content_px': {'x': 10, 'y': 14, 'w': 220, 'h': 222, 'note': 'kepala 55x55.5 dp, terpusat di frame head 240 px'},
}
wj('source/components/top_frame/topframe_formula.json', formula)

tf = json.load(open(f'{SRC}/source/components/tag_frame/frame_spritesheet_smooth.json'))
tf['image'] = 'frame_spritesheet.webp'
tf['color_mode'] = 'alpha_mask_white'
tf['tint_role'] = 'shared_ui'
tf['measured_px'] = {'stroke_thickness': 19, 'outer_margin': {'left': 18, 'top': 15, 'bottom': 18, 'right': 15},
                     'head': {'width': 87, 'height': 120, 'note': 'lebih langsing (rasio 0.73) daripada kepala badge/top_frame (rasio ~0.98)'}}
tf['runtime']['source_image_path'] = 'reskin_assets/source/components/tag_frame/frame_spritesheet.webp'
wj('source/components/tag_frame/frame_spritesheet.json', tf)

# ----------------------------------------------------------------- specs
os.makedirs(f'{OUT}/specs', exist_ok=True)
shutil.copy(f'{SRC}/specs/COMPONENT_BLUEPRINT.template.md', f'{OUT}/specs/COMPONENT_BLUEPRINT.template.md')
shutil.copy(f'{SRC}/specs/design_tokens.template.json', f'{OUT}/specs/design_tokens.template.json')
schema = json.load(open(f'{SRC}/specs/asset_manifest.schema.json'))
it = schema['properties']['assets']['items']['properties']
if 'badge' not in it['kind']['enum']:
    it['kind']['enum'].insert(it['kind']['enum'].index('frame') + 1, 'badge')
it['tint_role'] = {'enum': ['none', 'shared_ui', 'on_badge', 'foreground'],
                   'description': 'Peran warna runtime untuk mask putih. none = aset berwarna penuh (mis. wallpaper).'}
wj('specs/asset_manifest.schema.json', schema)
ex = json.load(open(f'{SRC}/specs/skin_manifest.example.json'))
ex['skin']['description'] = 'Contoh. Hanya daftarkan aset yang diganti; yang tidak ada memakai aset bawaan.'
ex['assets'] = [
    dict(id='icon_brain', kind='tag_icon', file='icons/tag/icon_brain.webp', format='webp', canvas_px={'width': 96, 'height': 96},
         safe_area_px={'x': 8, 'y': 8, 'width': 80, 'height': 80}, rendering={'scale_mode': 'fit', 'tint': 'on_badge'},
         tint_role='on_badge', runtime={'status': 'source_only', 'fallback_id': 'default.icon_brain'}),
    dict(id='badge_cat', kind='badge', file='components/tag_badge/badge_cat.webp', format='webp', canvas_px={'width': 128, 'height': 128},
         display_dp={'width': 32, 'height': 32}, tint_role='shared_ui', runtime={'status': 'source_only', 'fallback_id': 'default.badge_cat'}),
    dict(id='top_frame', kind='frame', file='components/top_frame/topframe_spritesheet@4x.png', format='png',
         canvas_px={'width': 536, 'height': 256}, display_dp={'height': 64}, tint_role='shared_ui',
         rendering={'scale_mode': 'atlas', 'content_scale': 'FillBounds', 'preserve_aspect_ratio': False},
         runtime={'status': 'source_only', 'fallback_id': 'default.top_frame'}),
]
ex['compatibility']['min_nekos_version'] = '0.0.0'
wj('specs/skin_manifest.example.json', ex)

# ----------------------------------------------------------- tools
os.makedirs(f'{OUT}/tools/legacy_build', exist_ok=True)
for f in ('validate_assets.py',):
    shutil.copy(f'/home/claude/work/{f}', f'{OUT}/tools/{f}')
for f in ('build_assets.py', 'build_components.py', 'build_docs.py'):
    shutil.copy(f'/home/claude/work/{f}', f'{OUT}/tools/legacy_build/{f}')
os.remove(f'{OUT}/_build_meta.json')
print('docs ok')
