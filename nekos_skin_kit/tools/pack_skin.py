#!/usr/bin/env python3
"""Validasi lalu kemas folder skin menjadi <id>-<versi>.zip (isi folder di ROOT zip, bukan folder pembungkus).

  python3 -I tools/pack_skin.py my_skin [--out dist] [--strict]

Zip dibuat deterministik (urutan & timestamp tetap) supaya hash-nya stabil antar build.
"""
import json, os, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import validate_skin as V

SKIP_NAMES = {'.DS_Store', 'Thumbs.db'}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    out = 'dist'
    if '--out' in sys.argv:
        out = sys.argv[sys.argv.index('--out') + 1]
        args = [a for a in args if a != out]
    if not args: print(__doc__); sys.exit(2)
    src = args[0]
    r, man = V.validate(src, strict='--strict' in sys.argv)
    for w in r.warns: print('WARN ', w)
    for e in r.errors: print('ERROR', e)
    if r.errors:
        print(f'\n{len(r.errors)} error: zip TIDAK dibuat.'); sys.exit(1)
    sk = man['skin']
    os.makedirs(out, exist_ok=True)
    dst = os.path.join(out, f"{sk['id']}-{sk['version']}.zip")
    files = []
    for dp, dn, fn in os.walk(src):
        dn[:] = sorted(d for d in dn if not d.startswith('.') and d != '__pycache__')
        for f in sorted(fn):
            if f in SKIP_NAMES or f.startswith('.'): continue
            full = os.path.join(dp, f)
            files.append((os.path.relpath(full, src).replace(os.sep, '/'), full))
    files.sort(key=lambda t: (t[0] != 'skin.json', t[0]))      # skin.json paling depan
    with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, full in files:
            zi = zipfile.ZipInfo(rel, (1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, open(full, 'rb').read())
    r2, _ = V.validate(dst, strict='--strict' in sys.argv)       # validasi ulang hasil zip
    if r2.errors:
        for e in r2.errors: print('ERROR', e)
        os.remove(dst); sys.exit(1)
    print(f'OK  {dst}  ({os.path.getsize(dst) / 1024:.0f} KB, {len(files)} file, {len(r.warns)} warning)')


if __name__ == '__main__':
    main()
