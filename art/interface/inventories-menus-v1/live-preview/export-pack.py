"""Bake the shared design kit into an isolated review pack, never into mod resources."""
from pathlib import Path
from PIL import Image
import hashlib
import io
import json
import runpy
import zipfile

HERE = Path(__file__).resolve().parent
ART = HERE.parent
KIT = runpy.run_path(str(ART / 'build-review.py'))
PACK = HERE / 'review-pack'
JAR = Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar'
ROOT = 'assets/minecraft/textures/gui/'
records = []


def save(name, image, metadata=None):
    target = PACK / name
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    if metadata is not None:
        target.with_name(target.name + '.mcmeta').write_text(json.dumps(metadata), encoding='utf-8')
    records.append(dict(path=name, size=list(image.size), sha256=hashlib.sha256(target.read_bytes()).hexdigest()))


def blank(w, h):
    return Image.new('RGBA', (w, h))


def recolor(original):
    im = original.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a and max(r, g, b) - min(r, g, b) < 6:
                if r >= 180:
                    d = (1 if (x * 3 + y * 5) % 17 == 0 else 0) - (1 if (x * 5 + y * 3) % 19 == 0 else 0)
                    color = (220 + d, 197 + d, 141 + d, a)
                elif r >= 90: color = (185, 154, 97, a)
                elif r >= 45: color = (101, 83, 58, a)
                else: color = (25, 22, 17, a)
                im.putpixel((x, y), color)
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
                KIT['slot'](target, x, y, size=size)
                found.append((x, y, size))
    return found


with zipfile.ZipFile(JAR) as archive:
    for name in archive.namelist():
        if not name.startswith(ROOT + 'container/') or not name.endswith('.png'):
            continue
        original = Image.open(io.BytesIO(archive.read(name))).convert('RGBA')
        target = recolor(original)
        slots = native_slots(original, target)
        # All native backgrounds retain their dimensions and every original slot origin.
        if '/creative_inventory/' in name:
            KIT['metal_frame'](target, 0, 0, 195, 136)
        elif name.endswith('generic_54.png'):
            KIT['metal_frame'](target, 0, 0, 176, 222)
        elif name.endswith(('inventory.png', 'crafting_table.png', 'furnace.png')):
            KIT['metal_frame'](target, 0, 0, 176, 166)
        assert target.size == original.size
        save(name, target)
        records[-1]['slots'] = slots
        if name.endswith('inventory.png') and '/creative_' not in name:
            assert (76, 61, 18) in slots and (7, 141, 18) in slots
            KIT['rect'](target, 134, 26, 19, 17, KIT['BODY'])
            KIT['arrow'](target, 134, 27, w=18)
            save(name, target)
        if name.endswith('crafting_table.png'):
            KIT['rect'](target, 89, 32, 25, 19, KIT['BODY'])
            KIT['arrow'](target, 89, 33)
            save(name, target)
    name = ROOT + 'recipe_book.png'
    original = Image.open(io.BytesIO(archive.read(name))).convert('RGBA')
    target = recolor(original)
    KIT['panel'](target, 1, 1, 147, 166)
    KIT['rect'](target, 25, 13, 83, 15, KIT['DARK'])
    KIT['outline'](target, 25, 13, 83, 15, KIT['GOLD']['dark'])
    KIT['magnifier'](target, 9, 13)
    # This texture is sampled from UV (1, 1); keep the kit in screen coordinates.
    KIT['page_counter'](target, 1, 1)
    save(name, target)
    for name in archive.namelist():
        if not name.startswith(ROOT + 'sprites/') or not name.endswith('.png'):
            continue
        rel = name.split('/sprites/')[1]
        wanted = rel.startswith(('recipe_book/', 'pause_menu/')) or rel in ('container/slot.png', 'friends/friends.png')
        shared = rel in ('widget/button.png', 'widget/button_disabled.png', 'widget/button_highlighted.png',
                         'widget/slider.png', 'widget/slider_highlighted.png',
                         'widget/slider_handle.png', 'widget/slider_handle_highlighted.png',
                         'tooltip/background.png', 'tooltip/frame.png')
        if not wanted and not shared:
            continue
        original = Image.open(io.BytesIO(archive.read(name))).convert('RGBA')
        meta = json.loads(archive.read(name + '.mcmeta')) if name + '.mcmeta' in archive.namelist() else None
        target = blank(*original.size)
        if rel.startswith('widget/button'):
            state = 'disabled' if '_disabled' in rel else 'hover' if '_highlighted' in rel else 'normal'
            KIT['button'](target, 0, 0, target.width, '', state, h=target.height)
        elif rel.startswith('widget/slider'):
            handle = 'slider_handle' in rel
            highlighted = '_highlighted' in rel
            assert original.size == ((8, 20) if handle else (200, 20))
            target = KIT['slider_sprite'](*original.size, highlighted=highlighted, handle=handle)
            if not handle:
                # Preserve the full two-pixel bevel when vanilla changes a slider's width.
                meta['gui']['scaling']['border'] = 2
            assert target.getpixel((0, 0)) == (101, 83, 58, 255)
            assert target.getpixel((1, 1)) == (223, 194, 139, 255)
            assert target.getpixel((2, 2)) == ((185, 154, 97, 255) if handle else (25, 22, 17, 255))
        elif rel.startswith('tooltip/'):
            if rel.endswith('/background.png'):
                KIT['rect'](target, 8, 8, 84, 84, (25, 22, 17, 238))
            else:
                KIT['metal_frame'](target, 8, 8, 84, 84)
                KIT['rect'](target, 10, 10, 80, 80, (0, 0, 0, 0))
        elif rel.startswith('recipe_book/page_'):
            target = KIT['page_arrow'](backward='backward' in rel, highlighted='_highlighted' in rel)
            assert target.size == original.size
        elif rel in ('recipe_book/tab.png', 'recipe_book/tab_selected.png'):
            selected = '_selected' in rel
            KIT['panel'](target, 0 if selected else 3, 0, target.width - (0 if selected else 3), target.height)
            if selected:
                KIT['rect'](target, target.width - 2, 2, 2, target.height - 4, KIT['BODY'])
        elif rel.startswith('recipe_book/slot_') or rel == 'container/slot.png':
            KIT['slot'](target, 0, 0, size=min(target.size))
        elif rel.startswith(('pause_menu/', 'friends/')):
            target = KIT['gold_icon'](original)
        else:
            target = recolor(original)
        out = 'assets/mysteries_preview/textures/gui/sprites/' + rel if shared else name
        save(out, target, meta)
    save('assets/mysteries_preview/textures/gui/sprites/panel/dark.png', KIT['PANEL_DARK'],
         {'gui': {'scaling': {'type': 'nine_slice', 'width': 24, 'height': 24, 'border': 4}}})

(PACK / 'pack.mcmeta').write_text(json.dumps({'pack': {'description': 'Огляд золотого інтерфейсу — лише тестова копія',
    'min_format': [88, 0], 'max_format': [88, 0]}}), encoding='utf-8')
unique = {}
for record in records:
    unique[record['path']] = {**unique.get(record['path'], {}), **record}
(HERE / 'pack-manifest.json').write_text(json.dumps(list(unique.values()), indent=2), encoding='utf-8')
print('Review textures:', len(unique))
