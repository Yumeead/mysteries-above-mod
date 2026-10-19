"""Regression checks for native geometry, slot contrast and fixed pagination spacing."""
from pathlib import Path
from PIL import Image, ImageColor
import hashlib
import io
import json
import zipfile
import kit

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
manifest = json.loads((HERE / 'manifest.json').read_text())
assert not manifest['globalMinecraftOverrides']
registry = json.loads((ROOT / 'mod/src/client/resources/assets/mysteries_above/interface-textures.json').read_text())
with zipfile.ZipFile(Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar') as archive:
    for source, target in registry.items():
        is_raw = source.startswith('textures/')
        original = 'assets/minecraft/' + (source if is_raw else 'textures/gui/sprites/' + source + '.png')
        own = ROOT / 'mod/src/client/resources/assets/mysteries_above' / (target if is_raw else 'textures/gui/sprites/' + target + '.png')
        if original in archive.namelist():
            assert Image.open(own).size == Image.open(io.BytesIO(archive.read(original))).size, source
    for record in manifest['generated']:
        assert hashlib.sha256((ROOT / record['path']).read_bytes()).hexdigest() == record['sha256'], record['path']
        if '/creative_inventory/tab_' in record['path'] and '/sprites/' in record['path']:
            image = Image.open(ROOT / record['path']).convert('RGBA')
            expected = kit.GOLD['light'] if '_selected_' in record['path'] else kit.GOLD['mid']
            assert image.getpixel((image.width - 2, image.height // 2))[:3] == ImageColor.getrgb(expected), record['path']
    for name, slots in manifest['nativeSlots'].items():
        native = 'assets/minecraft/textures/gui/container/' + (name if name.endswith('.png') else name + '.png')
        original = Image.open(io.BytesIO(archive.read(native))).convert('RGBA')
        own = 'interface/' + name + '.png' if name in ('inventory', 'generic_54') else 'interface/container/' + name
        image = Image.open(ROOT / 'mod/src/client/resources/assets/mysteries_above/textures/gui' / own).convert('RGBA')
        for x, y, size in slots:
            for point in ((x, y + size // 2), (x + size - 1, y + size // 2), (x + size // 2, y), (x + size // 2, y + size - 1)):
                assert image.getpixel(point)[:3] == ImageColor.getrgb(kit.SLOT_OUTLINE), (name, point)
            point = (x + size // 2, y + size // 2)
            if original.getpixel(point)[:3] == (139, 139, 139):
                assert image.getpixel(point)[:3] == ImageColor.getrgb(kit.SLOT_FILL), (name, point)
for w in (20, 98, 150, 200, 204, 310):
    image = kit.canvas(w, 20)
    kit.button(image, 0, 0, w)
    assert image.getpixel((0, 0))[:3] == ImageColor.getrgb(kit.GOLD['dark'])
    assert image.getpixel((1, 1))[:3] == ImageColor.getrgb(kit.GOLD['light'])
    assert image.getpixel((w - 2, 18))[:3] == ImageColor.getrgb(kit.GOLD['mid'])
    for scale in (1, 2, 3, 4):
        expanded = image.resize((w * scale, 20 * scale), Image.Resampling.NEAREST)
        assert expanded.getpixel((scale, scale)) == image.getpixel((1, 1))
for highlighted in (False, True):
    assert kit.page_arrow(True, highlighted).tobytes() == kit.page_arrow(False, highlighted).transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes()
x, y, w, h = kit.PAGE_COUNTER_FRAME
assert x + w <= 93 and x >= 50 and y + 2 <= 141 and 141 + 9 <= y + h - 2
assert (x, y, w, h) != (54, 140, 42, 18), 'Rejected overlapping review layout must not return'
print('Native dimensions, uniform slot edges, GUI scales 1–4 and pagination spacing: OK')
