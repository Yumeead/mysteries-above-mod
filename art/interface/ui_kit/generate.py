"""Генератор затвердженого інтерфейсу; жодних глобальних ресурсів minecraft:."""
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
                if not all(px[xx, y][:3] == (55, 55, 55) for xx in range(x, x + size - 1)):
                    continue
                if not all(px[x, yy][:3] == (55, 55, 55) for yy in range(y, y + size - 1)):
                    continue
                kit.slot(target, x, y, size=size)
                # Keep native embedded symbols while replacing the recessed gray field.
                for yy in range(y + 2, y + size - 2):
                    for xx in range(x + 2, x + size - 2):
                        if px[xx, yy][:3] != (139, 139, 139):
                            target.putpixel((xx, yy), recolor(source.crop((xx, yy, xx + 1, yy + 1))).getpixel((0, 0)))
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
        for path in archive.namelist():
            prefix = 'assets/minecraft/textures/gui/sprites/'
            if not path.startswith(prefix) or not path.endswith('.png'):
                continue
            name = path[len(prefix):-4]
            if not (name.startswith(('widget/', 'pause_menu/')) or name == 'friends/friends'):
                continue
            original = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
            meta = json.loads(archive.read(path + '.mcmeta')) if path + '.mcmeta' in archive.namelist() else None
            target = kit.canvas(*original.size)
            highlighted = 'highlighted' in name
            if name.startswith('widget/button'):
                kit.button(target, 0, 0, target.width, 'disabled' if 'disabled' in name else 'hover' if highlighted else 'normal', target.height)
                meta['gui']['scaling']['border'] = 3
            elif name.startswith('widget/slider'):
                handle = 'handle' in name
                target = kit.slider_sprite(*original.size, highlighted, handle)
                if not handle: meta['gui']['scaling']['border'] = 2
            elif name.startswith(('widget/text_field', 'widget/preedit')):
                kit.rect(target, 0, 0, *target.size, kit.DARK)
                kit.metal_frame(target, 0, 0, *target.size, highlighted)
                meta['gui']['scaling']['border'] = 2
            elif name.startswith('widget/tab'):
                kit.button(target, 0, 0, target.width, 'hover' if highlighted else 'normal', target.height, 'selected' in name)
            elif name.startswith('widget/checkbox'):
                kit.button(target, 0, 0, target.width, 'hover' if highlighted else 'normal', target.height)
                if 'selected' in name:
                    for x, y in [(5, 10), (6, 11), (7, 12), (8, 11), (9, 10), (10, 9), (11, 8), (12, 7), (13, 6)]:
                        kit.rect(target, x, y, 1, 2, kit.GOLD['light'])
            elif name.startswith('widget/scroller'):
                if name.endswith('_background'):
                    kit.rect(target, 0, 0, *target.size, kit.DARK)
                    kit.outline(target, 0, 0, *target.size, kit.GOLD['dark'])
                else: kit.scrollbar(target, 0, 0, target.height, target.height, w=target.width)
            elif name == 'widget/slot_frame':
                kit.metal_frame(target, 0, 0, *target.size)
                meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': target.width, 'height': target.height, 'border': 2}}}
            else:
                target = kit.gold_icon(original)
            assert target.size == original.size, name
            images['sprites/interface/' + name + '.png'] = (target, meta)
        for path in archive.namelist():
            root = 'assets/minecraft/textures/gui/'
            if not path.startswith(root + 'container/') or not path.endswith('.png'):
                continue
            name = path[len(root + 'container/'):]
            if name in ('inventory.png', 'generic_54.png', 'gamemode_switcher.png') or name.startswith('creative_inventory/'):
                continue
            original = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
            target = recolor(original)
            slots[name] = native_slots(original, target)
            _, _, w, h = original.getchannel('A').getbbox()
            kit.metal_frame(target, 0, 0, w, h)
            if name == 'crafting_table.png':
                kit.rect(target, 89, 32, 25, 19, kit.BODY)
                kit.arrow(target, 89, 33)
            assert target.size == original.size
            images['interface/container/' + name] = (target, None)
        path = 'assets/minecraft/textures/gui/recipe_book.png'
        original = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
        target = recolor(original)
        kit.panel(target, 1, 1, 147, 166)
        kit.rect(target, 25, 13, 83, 15, kit.DARK)
        kit.outline(target, 25, 13, 83, 15, kit.GOLD['dark'])
        kit.magnifier(target, 9, 13)
        kit.page_counter(target, 1, 1)
        images['interface/recipe_book.png'] = (target, None)
        for path in archive.namelist():
            prefix = 'assets/minecraft/textures/gui/sprites/'
            if not path.startswith(prefix) or not path.endswith('.png'):
                continue
            name = path[len(prefix):-4]
            if not name.startswith(('container/', 'recipe_book/', 'tooltip/')) or name.startswith('container/creative_inventory/'):
                continue
            original = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
            target = recolor(original)
            meta = json.loads(archive.read(path + '.mcmeta')) if path + '.mcmeta' in archive.namelist() else None
            if name.startswith('container/inventory/') or name in ('recipe_book/button', 'recipe_book/button_highlighted'):
                continue
            if name.startswith('tooltip/'):
                target = kit.canvas(*original.size)
                if name.endswith('/background'):
                    kit.rect(target, 8, 8, 84, 84, (*ImageColor.getrgb(kit.DARK), 238))
                else:
                    kit.metal_frame(target, 8, 8, 84, 84)
                    kit.rect(target, 10, 10, 80, 80, (0, 0, 0, 0))
            elif 'slot_highlight_' in name:
                target = kit.canvas(*original.size)
                if name.endswith('_front'): kit.outline(target, 3, 3, 18, 18, kit.GOLD['light'])
            elif name == 'container/slot' or name == 'container/crafter/disabled_slot':
                target = kit.canvas(*original.size)
                kit.slot(target, 0, 0)
                if 'disabled' in name:
                    kit.rect(target, 3, 8, 12, 2, kit.GOLD['dark'])
            elif name.startswith('container/slot/'):
                for y in range(target.height):
                    for x in range(target.width):
                        r, g, b, a = original.getpixel((x, y))
                        if a: target.putpixel((x, y), (*ImageColor.getrgb(kit.GOLD['dark']), a))
            elif name.endswith('/chest_slots'):
                native_slots(original, target)
            elif name.startswith('recipe_book/page_'):
                target = kit.page_arrow('backward' in name, '_highlighted' in name)
            elif name in ('recipe_book/tab', 'recipe_book/tab_selected'):
                target = kit.canvas(*original.size)
                selected = name.endswith('_selected')
                kit.panel(target, 0 if selected else 3, 0, target.width - (0 if selected else 3), target.height)
                if selected: kit.rect(target, target.width - 2, 2, 2, target.height - 4, kit.BODY)
            elif name.startswith('recipe_book/slot_'):
                target = kit.canvas(*original.size)
                kit.slot(target, 0, 0, size=original.width)
            elif name.endswith('/slot_background'):
                target = kit.canvas(*original.size)
                kit.slot(target, 3, 3)
            elif '/scroller' in name:
                target = kit.canvas(*original.size)
                kit.scrollbar(target, 0, 0, target.height, target.height, w=target.width)
                if 'disabled' in name: target = recolor(target)
            elif name.startswith('container/beacon/button') or '/enchantment_slot' in name:
                target = kit.canvas(*original.size)
                state = 'disabled' if 'disabled' in name else 'hover' if 'highlighted' in name else 'normal'
                kit.button(target, 0, 0, target.width, state, target.height, 'selected' in name)
            elif '/pattern' in name or name.startswith('container/stonecutter/recipe'):
                target = Image.new('RGBA', original.size, kit.SLOT_FILL)
                kit.metal_frame(target, 0, 0, *target.size, highlighted='selected' in name or 'highlighted' in name)
            elif name.startswith('container/villager/experience_bar'):
                target = kit.gold_icon(original)
            assert target.size == original.size, name
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
    registry = {}
    for relative in images:
        if relative.startswith('sprites/interface/'):
            registry[relative[len('sprites/interface/'):-4]] = 'interface/' + relative[len('sprites/interface/'):-4]
        elif relative.startswith('interface/container/'):
            registry['textures/gui/container/' + relative[len('interface/container/'):]] = 'textures/gui/' + relative
        elif relative in ('interface/inventory.png', 'interface/generic_54.png'):
            registry['textures/gui/container/' + relative.split('/')[-1]] = 'textures/gui/' + relative
        elif relative == 'interface/recipe_book.png': registry['textures/gui/recipe_book.png'] = 'textures/gui/' + relative
    registry_path = OUT.parents[1] / 'interface-textures.json'
    registry_raw = json.dumps(registry, ensure_ascii=False, indent=2) + '\n'
    if args.check: assert registry_path.read_text(encoding='utf-8') == registry_raw
    else: registry_path.write_text(registry_raw, encoding='utf-8')
    report = dict(minecraft='26.2', stages=[1, 2, 3, 4], jarSha256=hashlib.sha256(args.minecraft_jar.read_bytes()).hexdigest(),
                  generated=manifest, nativeSlots=slots, globalMinecraftOverrides=False)
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.check: assert (HERE / 'manifest.json').read_text(encoding='utf-8') == serialized
    else: (HERE / 'manifest.json').write_text(serialized, encoding='utf-8')
    print(('Перевірено' if args.check else 'Створено') + f' {len(images)} текстур; усі координати слотів ванільні.')


if __name__ == '__main__':
    main()
