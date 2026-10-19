"""Перевірка робочих екранів у локальному сеансі --integration без оглядового пака."""
from pathlib import Path
from PIL import Image
import argparse
import hashlib
import json
import shutil
import time
import kit

HERE = Path(__file__).resolve().parent
review = HERE.parent / 'inventories-menus-v1/live-preview'
session = json.loads((review / 'session.json').read_text())
assert session['integration']
game = Path(session['gameDirectory'])
assert not (game / 'resourcepacks/gold-review').exists()
OUT = HERE / 'in-game/stages-3-5'
OUT.mkdir(parents=True, exist_ok=True)


def await_file(path):
    deadline = time.monotonic() + 90
    while not path.exists():
        if (game / 'failed.txt').exists(): raise RuntimeError((game / 'failed.txt').read_text())
        if time.monotonic() >= deadline: raise TimeoutError(str(path))
        time.sleep(.25)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--screens', nargs='+', default=['inventory', 'double-chest', 'crafting', 'crafting-page2', 'furnace', 'tooltip'])
parser.add_argument('--scales', nargs='+', type=int, default=[2, 3])
parser.add_argument('--styles', nargs='+', choices=['mod', 'vanilla'], default=['mod', 'vanilla'])
args = parser.parse_args()
report_path = HERE / 'screens-verification.json'
records = json.loads(report_path.read_text(encoding='utf-8'))['captures'] if report_path.exists() else []
await_file(game / 'ready.txt')
for scale in args.scales:
    assert scale in (1, 2, 3, 4)
    for style in args.styles:
        for name in args.screens:
            source = game / 'screenshots' / f'gold-{name}-gs{scale}.png'
            source.unlink(missing_ok=True)
            (game / 'request.txt').write_text(f'{name} {scale} {time.time_ns()} {style}', encoding='utf-8')
            await_file(source)
            time.sleep(.35)
            target = OUT / f'{name}-{style}-gs{scale}.png'
            shutil.copyfile(source, target)
            assert source.read_bytes() == target.read_bytes()
            image = Image.open(target).convert('RGB')
            assert image.size == (1920, 1080)
            native = (game / 'last-screen.txt').read_text()
            assert native.endswith(f'{1920 // scale}x{1080 // scale}'), native
            assert (game / 'style-check.txt').read_text().startswith('Native HUD settings click: ' + style.upper())
            record = dict(file=target.name, screen=name, guiScale=scale, style=style, nativeScreen=native,
                          sha256=hashlib.sha256(target.read_bytes()).hexdigest(), edited=False)
            if '.screens.inventory.' in native:
                x, y, w, h = json.loads((game / 'screen-geometry.txt').read_text())
                assert x >= 0 and y >= 0 and (x + w) * scale <= 1920 and (y + h) * scale <= 1080
                rgb = image.getpixel(((x + 3) * scale, (y + 3) * scale))
                if style == 'mod': assert all(abs(c - want) <= 1 for c, want in zip(rgb, (220, 197, 141))), (name, rgb)
                else: assert rgb[0] == rgb[1] == rgb[2], (name, rgb)
                record['panel'] = [x, y, w, h]
            if name.startswith('crafting'):
                v = json.loads((game / 'pagination.txt').read_text())
                ox, oy, tx, ty, tw, th, bx, by, bw, bh, fx, fy, fw, fh, page, total = v
                if style == 'mod':
                    dx, dy, cw, ch = kit.PAGE_COUNTER_FRAME
                    cx, cy = ox + dx, oy + dy
                    assert cx + 2 <= tx and tx + tw <= cx + cw - 2
                    assert cy + 2 <= ty and ty + th <= cy + ch - 2
                    assert fx + 1 - (cx + cw) >= 2 and cx - (bx + bw - 1) >= 2
                    box = ((ox + 34) * scale, (oy + 134) * scale, (ox + 109) * scale, (oy + 157) * scale)
                    crop = image.crop(box)
                    white = [(xx + box[0], yy + box[1]) for yy in range(crop.height) for xx in range(crop.width)
                             if min(crop.getpixel((xx, yy))) >= 200]
                    assert white and all((cx + 2) * scale <= xx < (cx + cw - 2) * scale and
                        (cy + 2) * scale <= yy < (cy + ch - 2) * scale for xx, yy in white)
                    crop.resize((300, 92), Image.Resampling.NEAREST).save(OUT / f'pagination-page{page}-gs{scale}-x4.png')
                record['pagination'] = dict(page=page, total=total, textInsideFrame=style == 'mod')
            records = [r for r in records if (r['screen'], r['style'], r['guiScale']) != (name, style, scale)] + [record]
            report_path.write_text(json.dumps(dict(passed=True, game='26.2', reviewPack=False,
                helperSkinHooks=False, captures=records), ensure_ascii=False, indent=2), encoding='utf-8')
            print(target.name, native.split(' ')[0].split('.')[-1], 'OK', flush=True)
