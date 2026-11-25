#!/usr/bin/env python3
"""Buat kerangka skin baru.

  python3 -I tools/new_skin.py my_skin --name "My Skin" --author "Nama" [--with-motif]

Hasil: folder my_skin/ berisi skin.json valid + folder kosong + README.md.
Skin boleh parsial: tambahkan file hanya untuk aset yang ingin kamu ganti.
Tambah `--copy badge_cat icon_brain ui:icon_gear ...` untuk menyalin aset bawaan sebagai titik awal edit
(nama ikon yang ada di tag dan ui: awali `tag:` atau `ui:`; tanpa awalan, keduanya disalin).
"""
import argparse, json, os, re, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEF = os.path.join(os.path.dirname(HERE), 'default_skin')

ap = argparse.ArgumentParser()
ap.add_argument('folder')
ap.add_argument('--name'); ap.add_argument('--author', default='')
ap.add_argument('--id'); ap.add_argument('--copy', nargs='*', default=[])
a = ap.parse_args()
sid = a.id or re.sub(r'[^a-z0-9]+', '_', os.path.basename(a.folder.rstrip('/')).lower()).strip('_') or 'my_skin'
if os.path.exists(a.folder) and os.listdir(a.folder): sys.exit(f'{a.folder} sudah ada dan tidak kosong')
for d in ('icons/tag', 'icons/ui', 'components/tag_badge', 'components/top_frame', 'components/tag_frame', 'motifs'):
    os.makedirs(os.path.join(a.folder, d), exist_ok=True)
manifest = {
    'schema_version': 1,
    'skin': {'id': sid, 'name': a.name or sid, 'version': '1.0.0', 'author': a.author,
             'description': '', 'min_nekos_version': '0.0.0'},
    'assets': [], 'motifs': [],
    'compatibility': {'min_nekos_version': '0.0.0', 'requires_network': False, 'contains_executable_code': False}}
dm = json.load(open(os.path.join(DEF, 'skin.json'), encoding='utf-8'))
dassets = {x['id']: x for x in dm['assets']}
SRC = {'badge_cat': ['components/tag_badge/badge_cat.webp', 'components/tag_badge/badge.json'],
       'top_frame': ['components/top_frame/topframe_spritesheet@4x.png', 'components/top_frame/topframe_atlas.json'],
       'tag_frame': ['components/tag_frame/frame_spritesheet.webp', 'components/tag_frame/frame_spritesheet.json']}
for n in a.copy:
    if n in SRC: files = SRC[n]; manifest['assets'].append(dassets[n])
    elif re.fullmatch(r'(tag:|ui:)?icon_[a-z0-9_]+', n):
        sub, _, nm = n.rpartition(':') if ':' in n else ('', '', n)
        in_tag = os.path.exists(f'{DEF}/icons/tag/{nm}.webp'); in_ui = os.path.exists(f'{DEF}/icons/ui/{nm}.webp')
        if not sub:
            if in_tag and in_ui: print(f'{nm} ada di tag DAN ui; menyalin keduanya (pakai tag:{nm} atau ui:{nm} untuk memilih)')
            subs = [x for x, ok in (('tag', in_tag), ('ui', in_ui)) if ok]
        else:
            subs = [sub]
        files = [f'icons/{x}/{nm}.webp' for x in subs]
    else: print('lewati (tidak dikenal):', n); continue
    for f in files:
        if os.path.exists(f'{DEF}/{f}'): shutil.copy(f'{DEF}/{f}', os.path.join(a.folder, f))
        else: print('tidak ada di bawaan:', f)
json.dump(manifest, open(os.path.join(a.folder, 'skin.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
open(os.path.join(a.folder, 'skin.json'), 'a').write('\n')
open(os.path.join(a.folder, 'README.md'), 'w', encoding='utf-8').write(
    f'# {a.name or sid}\n\nSkin NekOS. Panduan: docs/02_PANDUAN_MEMBUAT_SKIN.md di kit.\n\n'
    'Validasi: `python3 -I tools/validate_skin.py <folder ini>`\nKemas: `python3 -I tools/pack_skin.py <folder ini>`\n')
print(f'OK  kerangka skin di {a.folder}/ (id={sid})')
