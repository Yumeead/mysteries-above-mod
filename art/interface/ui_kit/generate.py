"""Етапи 1–2: кіт, фон E і фон скрині; жодних глобальних ресурсів minecraft:."""
from pathlib import Path
from PIL import Image, ImageColor
import argparse
import hashlib
import io
import json
import zipfile
import kit

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / 'mod/src/client/resources/assets/mysteries_above/textures/gui'


def recolor(original):
    im = original.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if not a or max(r, g, b) - min(r, g, b) >= 6:
                continue
            if r >= 180:
                d = ((x * 3 + y * 5) % 17 == 0) - ((x * 5 + y * 3) % 19 == 0)
                rgb = tuple(c + d for c in ImageColor.getrgb(kit.BODY))
            else:
                rgb = ImageColor.getrgb(kit.GOLD['mid'] if r >= 90 else kit.GOLD['dark'] if r >= 45 else kit.DARK)
            im.putpixel((x, y), (*rgb, a))
    return im


def native_slots(source, target):
    found = []
    px = source.load()
    for size in (26, 24, 18):
        for y in range(source.height - size + 1):
            for x in range(source.width - size + 1):
                if px[x, y][:3] != (55, 55, 55) or px[x + 1, y + 1][:3] != (139, 139, 139):
                    continue
                if px[x + size - 2, y + size - 2][:3] != (139, 139, 139) or px[x + size - 1, y + size - 2][:3] != (255, 255, 255):
                    continue
                if any(xx <= x < xx + ss and yy <= y < yy + ss for xx, yy, ss in found):
                    continue
                if not all(px[xx, yy][:3] == (139, 139, 139) for xx in range(x + 1, x + size - 1)
                           for yy in range(y + 1, y + size - 1)):
                    continue
                kit.slot(target, x, y, size=size)
                found.append((x, y, size))
    return found


def build(jar):
    images, slots = {}, {}
    def sprite(name, im, border=None):
        metadata = None if border is None else {'gui': {'scaling': {
            'type': 'nine_slice', 'width': im.width, 'height': im.height, 'border': border}}}
        images['sprites/interface/' + name + '.png'] = (im, metadata)

    sprite('panel_light', kit.PANEL_LIGHT, 4)
    sprite('panel_dark', kit.PANEL_DARK, 4)
    for name, w, h in [('slot', 18, 18), ('crafting_arrow_small', 18, 15), ('crafting_arrow', 22, 15),
                       ('magnifier', 13, 15), ('page_counter', 38, 17), ('scroller', 6, 32)]:
        im = kit.canvas(w, h)
        if name == 'slot': kit.slot(im, 0, 0)
        elif name.startswith('crafting_arrow'): kit.arrow(im, 0, 0, w)
        elif name == 'magnifier': kit.magnifier(im, 0, 0)
        elif name == 'page_counter': kit.button(im, 0, 0, w, h=h)
        else: kit.scrollbar(im, 0, 0, h)
        sprite(name, im)
    for state in ('normal', 'hover', 'pressed', 'focus', 'disabled'):
        im = kit.canvas(200, 20)
        kit.button(im, 0, 0, 200, state)
        sprite('button_' + state, im, 3)
    for backward in (False, True):
        for highlighted in (False, True):
            sprite('page_' + ('backward' if backward else 'forward') + ('_highlighted' if highlighted else ''),
                   kit.page_arrow(backward, highlighted))
    for handle in (False, True):
        for highlighted in (False, True):
            border = {'left': 2, 'top': 2, 'right': 2, 'bottom': 3} if handle else 2
            sprite(('slider_handle' if handle else 'slider') + ('_highlighted' if highlighted else ''),
                   kit.slider_sprite(8 if handle else 200, 20, highlighted, handle), border)

    with zipfile.ZipFile(jar) as archive:
        for name, height in [('inventory', 166), ('generic_54', 222)]:
            original = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/gui/container/' + name + '.png'))).convert('RGBA')
            target = recolor(original)
            slots[name] = native_slots(original, target)
            assert len(slots[name]) == (46 if name == 'inventory' else 90), slots[name]
            kit.metal_frame(target, 0, 0, 176, height)
            if name == 'inventory':
                assert (76, 61, 18) in slots[name], 'Втрачено ванільний слот другої руки'
                kit.rect(target, 134, 26, 19, 17, kit.BODY)
                kit.arrow(target, 134, 27, w=18)
            assert target.size == original.size == (256, 256)
            images['interface/' + name + '.png'] = (target, None)
        for name in ('container/inventory/effect_background', 'container/inventory/effect_background_ambient',
                     'recipe_book/button', 'recipe_book/button_highlighted'):
            path = 'assets/minecraft/textures/gui/sprites/' + name + '.png'
            original = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
            target = recolor(original)
            meta = json.loads(archive.read(path + '.mcmeta')) if path + '.mcmeta' in archive.namelist() else None
            if name.startswith('container/inventory/'):
                target = Image.new('RGBA', original.size, kit.DARK)
                kit.metal_frame(target, 0, 0, *target.size)
            sprite(name, target)
            images['sprites/interface/' + name + '.png'] = (target, meta)
    return images, slots


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-jar', type=Path, default=Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar')
    parser.add_argument('--check', action='store_true', help='Перевірити відтворюваність без запису файлів')
    args = parser.parse_args()
    images, slots = build(args.minecraft_jar)
    manifest = []
    for relative, (im, metadata) in images.items():
        path = OUT / relative
        buffer = io.BytesIO()
        im.save(buffer, format='PNG')
        raw = buffer.getvalue()
        if args.check:
            assert path.read_bytes() == raw, f'Перегенеруйте {path}'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        if metadata is not None:
            meta_path = path.with_suffix('.png.mcmeta')
            meta_raw = json.dumps(metadata, ensure_ascii=False, indent=2) + '\n'
            if args.check: assert meta_path.read_text(encoding='utf-8') == meta_raw, meta_path
            else: meta_path.write_text(meta_raw, encoding='utf-8')
        manifest.append(dict(path=str(path.relative_to(ROOT)).replace('\\', '/'), size=list(im.size), sha256=hashlib.sha256(raw).hexdigest()))
    report = dict(minecraft='26.2', stages=[1, 2], jarSha256=hashlib.sha256(args.minecraft_jar.read_bytes()).hexdigest(),
                  generated=manifest, nativeSlots=slots, globalMinecraftOverrides=False)
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.check: assert (HERE / 'manifest.json').read_text(encoding='utf-8') == serialized
    else: (HERE / 'manifest.json').write_text(serialized, encoding='utf-8')
    print(('Перевірено' if args.check else 'Створено') + f' {len(images)} текстур; усі координати слотів ванільні.')


if __name__ == '__main__':
    main()
