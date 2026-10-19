"""Перевірка етапу 2 у локальному сеансі run-review.py --integration, без оглядового пака."""
from pathlib import Path
from PIL import Image
import hashlib
import json
import shutil
import time

HERE = Path(__file__).resolve().parent
review = HERE.parent / 'inventories-menus-v1/live-preview'
session = json.loads((review / 'session.json').read_text())
assert session['integration'], 'Потрібен сеанс --integration'
game = Path(session['gameDirectory'])
OUT = HERE / 'in-game'
OUT.mkdir(exist_ok=True)


def await_file(path):
    deadline = time.monotonic() + 90
    while not path.exists():
        if (game / 'failed.txt').exists(): raise RuntimeError((game / 'failed.txt').read_text())
        if time.monotonic() >= deadline: raise TimeoutError(str(path))
        time.sleep(.25)


await_file(game / 'ready.txt')
assert not (game / 'resourcepacks/gold-review').exists()
records = []
for scale in (2, 3):
    for style in ('vanilla', 'mod'):
        for name in ('inventory', 'chest'):
            source = game / 'screenshots' / f'gold-{name}-gs{scale}.png'
            source.unlink(missing_ok=True)
            (game / 'request.txt').write_text(f'{name} {scale} {time.time_ns()} {style}', encoding='utf-8')
            await_file(source)
            time.sleep(.4)
            target = OUT / f'{name}-{style}-gs{scale}.png'
            shutil.copyfile(source, target)
            image = Image.open(target).convert('RGB')
            assert image.size == (1920, 1080)
            gui = (game / 'last-screen.txt').read_text()
            assert gui.endswith('960x540' if scale == 2 else '640x360'), gui
            assert ('.InventoryScreen ' if name == 'inventory' else '.ContainerScreen ') in gui
            origin = ((1920 // scale - 176) // 2, (1080 // scale - (166 if name == 'inventory' else 168)) // 2)
            # An empty middle slot proves the live pipeline selected the intended texture.
            px = ((origin[0] + 7 + 9) * scale, (origin[1] + (101 if name == 'inventory' else 17 + 18) + 9) * scale)
            expected = (186, 152, 91) if style == 'mod' else (139, 139, 139)
            assert image.getpixel(px) == expected, (name, style, px, image.getpixel(px))
            proof = (game / 'style-check.txt').read_text()
            assert style.upper() in proof and 'unchanged' in proof
            records.append(dict(file=target.name, guiScale=scale, style=style, nativeScreen=gui,
                sha256=hashlib.sha256(target.read_bytes()).hexdigest(), edited=False, nativeToggle=proof))
            print(target.name, 'OK', flush=True)
report = dict(passed=True, game='26.2', screenshots=records, reviewResourcePack=False,
    reviewSkinHooks=False, existingStyleToggleUsed=True, modScreenSubclassUnchanged=True,
    sixRowChestDeferred=True, pauseUnchanged=True, vanillaSlotPixelVerified=True, modSlotPixelVerified=True)
(OUT / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
(HERE / 'game-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('Етап 2: усі 8 ігрових перевірок пройшли.')
