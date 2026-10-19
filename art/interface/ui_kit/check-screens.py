"""Перевірка робочих екранів у локальному сеансі --integration без оглядового пака."""
from pathlib import Path
from PIL import Image
import argparse
import hashlib
import html
import json
import shutil
import time
import kit

HERE = Path(__file__).resolve().parent
review = HERE.parent / 'inventories-menus-v1/live-preview'
session = json.loads((review / 'session.json').read_text())
assert session['integration']
assert not json.loads((Path(session['helper']) / 'fabric.mod.json').read_text())['mixins']
game = Path(session['gameDirectory'])
assert not (game / 'resourcepacks/gold-review').exists()
OUT = HERE / 'in-game/stages-3-5'
OUT.mkdir(parents=True, exist_ok=True)


def await_file(path):
    deadline = time.monotonic() + 90
    while not path.exists():
        if (game / 'failed.txt').exists(): raise RuntimeError((game / 'failed.txt').read_text())
        if list((game / 'crash-reports').glob('*.txt')): raise RuntimeError(f'Game crashed; inspect {game / "crash-reports"}')
        if time.monotonic() >= deadline: raise TimeoutError(str(path))
        time.sleep(.25)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--screens', nargs='+', default=['inventory', 'double-chest', 'crafting', 'crafting-page2', 'furnace', 'tooltip'])
parser.add_argument('--scales', nargs='+', type=int, default=[2, 3])
parser.add_argument('--styles', nargs='+', choices=['mod', 'vanilla'], default=['mod', 'vanilla'])
parser.add_argument('--scene', choices=['day', 'cave'], default='day')
args = parser.parse_args()
expected = dict(inventory='InventoryScreen', tooltip='InventoryScreen', chest='ContainerScreen',
    **{'double-chest': 'ContainerScreen', 'ender-chest': 'ContainerScreen', 'blast-furnace': 'BlastFurnaceScreen'},
    barrel='ContainerScreen', furnace='FurnaceScreen', smoker='SmokerScreen', anvil='AnvilScreen',
    enchanting='EnchantmentScreen', smithing='SmithingScreen', grindstone='GrindstoneScreen', loom='LoomScreen',
    cartography='CartographyTableScreen', stonecutter='StonecutterScreen', brewing='BrewingStandScreen',
    beacon='BeaconScreen', hopper='HopperScreen', dispenser='DispenserScreen', dropper='DispenserScreen',
    shulker='ShulkerBoxScreen', crafter='CrafterScreen', trading='MerchantScreen', horse='HorseInventoryScreen',
    llama='HorseInventoryScreen', nautilus='NautilusInventoryScreen', language='LanguageSelectScreen', options='OptionsScreen')
report_path = HERE / 'screens-verification.json'
records = json.loads(report_path.read_text(encoding='utf-8'))['captures'] if report_path.exists() else []
await_file(game / 'ready.txt')
(game / 'scene-ready.txt').unlink(missing_ok=True)
(game / 'request.txt').write_text(f'scene {args.scene} {time.time_ns()}', encoding='utf-8')
await_file(game / 'scene-ready.txt')
time.sleep(2)
suffix = '-cave' if args.scene == 'cave' else ''
for scale in args.scales:
    assert scale in (1, 2, 3, 4)
    for style in args.styles:
        for name in args.screens:
            source = game / 'screenshots' / f'gold-{name}{suffix}-gs{scale}.png'
            source.unlink(missing_ok=True)
            (game / 'last-shot.txt').unlink(missing_ok=True)
            (game / 'request.txt').write_text(f'{name} {scale} {time.time_ns()} {style}', encoding='utf-8')
            await_file(game / 'last-shot.txt')
            assert (game / 'last-shot.txt').read_text() == source.name
            time.sleep(.35)
            target = OUT / f'{name}{suffix}-{style}-gs{scale}.png'
            shutil.copyfile(source, target)
            assert source.read_bytes() == target.read_bytes()
            image = Image.open(target).convert('RGB')
            assert image.size == (1920, 1080)
            native = (game / 'last-screen.txt').read_text()
            assert native.endswith(f'{1920 // scale}x{1080 // scale}'), native
            screen_class = 'CreativeModeInventoryScreen' if name.startswith('creative') else 'CraftingScreen' if name.startswith('crafting') else \
                'PauseScreen' if name.startswith('pause') else 'SoundOptionsScreen' if name.startswith('settings') else expected[name]
            assert native.split()[0].split('.')[-1] == screen_class, (name, native)
            assert (game / 'style-check.txt').read_text().startswith('Native HUD settings click: ' + style.upper())
            record = dict(file=target.name, screen=name + suffix, scene=args.scene, guiScale=scale, style=style, nativeScreen=native,
                          sha256=hashlib.sha256(target.read_bytes()).hexdigest(), edited=False)
            if '.screens.inventory.' in native:
                x, y, w, h = json.loads((game / 'screen-geometry.txt').read_text())
                assert x >= 0 and y >= 0 and (x + w) * scale <= 1920 and (y + h) * scale <= 1080
                sample = (x + w - 5, y + 10) if name.startswith('creative') else (x + 3, y + 3)
                rgb = image.getpixel((sample[0] * scale, sample[1] * scale))
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
            records = [r for r in records if (r['screen'], r['style'], r['guiScale']) != (name + suffix, style, scale)] + [record]
            report_path.write_text(json.dumps(dict(passed=True, game='26.2', reviewPack=False,
                helperSkinHooks=False, captures=records), ensure_ascii=False, indent=2), encoding='utf-8')
            print(target.name, native.split(' ')[0].split('.')[-1], 'OK', flush=True)

names = dict(inventory='Інвентар E', chest='Скриня', crafting='Верстак і книга рецептів',
    **{'crafting-page2': 'Книга рецептів: сторінка 2', 'double-chest': 'Подвійна скриня',
       'creative-search': 'Творчий: український пошук', 'creative-inventory': 'Творчий: спорядження',
       'pause-focus': 'Пауза: фокус клавіатури', 'settings-hover': 'Повзунок: перетягування',
       'settings-pressed': 'Перемикач: натиснутий стан', 'blast-furnace': 'Плавильна піч', 'ender-chest': 'Ендер-скриня'},
    furnace='Піч', tooltip='Підказка', creative='Творчий: вкладки й прокрутка', pause='Пауза',
    settings='Музика й звук', options='Налаштування', language='Мова: список і пошук', smoker='Коптильня',
    anvil='Ковадло', enchanting='Зачарування', smithing='Ковальський стіл', grindstone='Точило',
    loom='Ткацький верстат', cartography='Картограф', stonecutter='Камнеріз', brewing='Зілляварка',
    beacon='Маяк', hopper='Воронка', dispenser='Роздавач', dropper='Викидач', shulker='Шалкер',
    barrel='Бочка', crafter='Автоматичний майструвач', trading='Торгівля', horse='Кінь', llama='Лама', nautilus='Наутилус')
cards = []
for r in sorted(records, key=lambda r: (r['screen'], r['guiScale'], r['style'])):
    screen = r['screen'].removesuffix('-cave')
    title = f"{names.get(screen, screen)} · {'Печера' if r['screen'].endswith('-cave') else 'День'} · Scale {r['guiScale']} · {'Мод' if r['style'] == 'mod' else 'Ванільний'}"
    cards.append(f'<figure data-style="{r["style"]}" data-scale="{r["guiScale"]}"><figcaption>{html.escape(title)}</figcaption>'
        f'<a href="{r["file"]}"><img loading="lazy" src="{r["file"]}" alt="{html.escape(title)}"></a></figure>')
(OUT / 'index.html').write_text('''<!doctype html><html lang="uk"><meta charset="utf-8"><title>Золотий інтерфейс — Minecraft 26.2</title>
<style>body{background:#191611;color:#f0e9da;font:16px system-ui;margin:24px}header{position:sticky;top:0;background:#191611;padding:12px;border:2px solid #b99a61}select{font:inherit;background:#191611;color:#dfc28b;padding:5px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(460px,1fr));gap:20px}figure{margin:0}figcaption{padding:12px}img{width:100%;image-rendering:pixelated}figure[hidden]{display:none}</style>
<header><b>Ігрові кадри 26.2</b> · PNG 1920×1080 без ретуші. Натисніть зображення для повного розміру.
<label>Стиль <select id="style"><option value="mod">Мод</option><option value="vanilla">Ванільний</option><option value="">Обидва</option></select></label>
<label>GUI Scale <select id="scale"><option value="">Усі</option><option>1</option><option>2</option><option>3</option><option>4</option></select></label></header><main>'''
    + '\n'.join(cards) + '''</main><script>function filter(){document.querySelectorAll('figure').forEach(f=>f.hidden=(style.value&&f.dataset.style!==style.value)||(scale.value&&f.dataset.scale!==scale.value))}const style=document.getElementById('style'),scale=document.getElementById('scale');style.onchange=scale.onchange=filter;filter()</script></html>''', encoding='utf-8')
