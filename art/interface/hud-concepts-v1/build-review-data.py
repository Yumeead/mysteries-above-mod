"""Embed immutable design references into the offline review, without changing game assets."""
from pathlib import Path
from PIL import Image
import base64
import hashlib
import io
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
PAGE = OUT / 'review.html'
html = PAGE.read_text(encoding='utf-8')
old_html = (ROOT / 'art/interface/design-kit-v1/review.html').read_text(encoding='utf-8')
old = json.loads(re.search(r'<script id="payload" type="application/json">(.*?)</script>', old_html, re.S).group(1))
paths = old['paths']
images = {'provided': old['images']['provided']}
sources = []


def embed(key, data, origin):
    images[key] = 'data:image/png;base64,' + base64.b64encode(data).decode()
    sources.append({'key': key, 'source': origin, 'sha256': hashlib.sha256(data).hexdigest()})


embed('cleanProvided', (OUT / 'sources/user-background-clean-imagegen.png').read_bytes(), 'sources/user-background-clean-imagegen.png; imagegen built-in background cleanup only')
for name in ['sky', 'cave', 'snow']:
    embed(name, (OUT / 'sources' / f'bg-{name}.png').read_bytes(), f'sources/bg-{name}.png')
for scale in range(1, 5):
    embed('font' + str(scale), (OUT / 'font-proof' / f'font-gui{scale}.png').read_bytes(), f'font-proof/font-gui{scale}.png')
resources = ROOT / 'mod/src/client/resources/assets/mysteries_above'
flask_paths = {}
for path in paths:
    identity = path['id']
    source = resources / 'hud/spirituality' / f'{identity.lower()}.json'
    if not source.exists():
        continue
    description = json.loads(source.read_text(encoding='utf-8'))
    if 'liquids' not in description:
        continue
    flask_paths[identity] = True
    for layer in ['frame', 'engraving', 'glass', 'neck', 'tag']:
        relative = description[layer].split(':')[1]
        file = resources / 'textures/gui/sprites' / f'{relative}.png'
        embed(f'flask-{identity}-{layer}', file.read_bytes(), str(file.relative_to(ROOT)))
    for state, index in [('full', -1), ('low', 1)]:
        file = resources / description['liquids'][index]['texture'].split(':')[1]
        embed(f'flask-{identity}-{state}', file.read_bytes(), str(file.relative_to(ROOT)))

glyphs = {' ': {'advance': 4, 'offset': 0, 'pixels': []}}
needed = set(html + ''.join(p['label'] for p in paths) +
             'іїєґІЇЄҐ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
             'АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯабвгґдеєжзиіїйклмнопрстуфхцчшщьюя’')


def mix(first, second, fraction):
    return tuple(round(first[i] * (1 - fraction) + second[i] * fraction) for i in range(3))


def stylize(archive, key, filename, tint, empty=False):
    source = archive.read('assets/minecraft/textures/gui/sprites/' + filename + '.png')
    image = Image.open(io.BytesIO(source)).convert('RGBA')
    base = tuple(int(tint[i:i + 2], 16) for i in (1, 3, 5))
    shapes = []
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = image.getpixel((x, y))
            if alpha < 30:
                continue
            value = (max(red, green, blue) + min(red, green, blue)) / 2
            if max(red, green, blue) < 65:
                color = (9, 12, 16)
            elif empty:
                color = (44, 57, 65) if value < 190 else (77, 95, 105)
            elif value < 70:
                color = mix((9, 12, 16), base, .5)
            elif value < 155:
                color = base
            else:
                color = mix(base, (235, 241, 231), .55)
            if y < 3 and x < 4 and max(red, green, blue) > 90 and not empty:
                color = mix(base, (222, 229, 217), .7)
            if '/heart/' in filename and not empty and max(red, green, blue) >= 65:
                if x + y < 7:
                    color = mix(base, (235, 241, 231), .48)
                elif x >= 4 and y >= 3:
                    color = mix((9, 12, 16), base, .68)
                else:
                    color = base
                if x in (2, 3) and y in (1, 2):
                    color = mix(base, (240, 246, 232), .8)
            shapes.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="#{color[0]:02x}{color[1]:02x}{color[2]:02x}"/>')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{image.width}" height="{image.height}" '
           f'viewBox="0 0 {image.width} {image.height}" shape-rendering="crispEdges">' + ''.join(shapes) + '</svg>')
    images[key] = 'data:image/svg+xml;base64,' + base64.b64encode(svg.encode()).decode()


jar = Path('C:/Users/yuryp/.gradle/caches/fabric-loom/26.2/minecraft-client.jar')
with zipfile.ZipFile(jar) as archive:
    for provider in json.loads(archive.read('assets/minecraft/font/include/default.json'))['providers']:
        if provider['type'] != 'bitmap':
            continue
        image = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/' + provider['file'].split(':')[1]))).convert('RGBA')
        width = image.width // len(provider['chars'][0])
        height = image.height // len(provider['chars'])
        for row, line in enumerate(provider['chars']):
            for column, char in enumerate(line):
                if char in glyphs or char not in needed or char == '\0':
                    continue
                pixels = [(x, y) for y in range(height) for x in range(width)
                          if image.getpixel((column * width + x, row * height + y))[3] > 0]
                if pixels:
                    glyphs[char] = {'advance': max(x for x, y in pixels) + 2,
                                    'offset': 7 - provider['ascent'], 'pixels': pixels}
    for kind, prefix, tint in [('normal', '', '#D65357'), ('poison', 'poisoned_', '#86B96B'),
                               ('wither', 'withered_', '#918899'), ('frozen', 'frozen_', '#B3DAEC'),
                               ('absorb', 'absorbing_', '#E4C46B')]:
        for fill in ['full', 'half']:
            stylize(archive, f'heart-{kind}-{fill}', 'hud/heart/' + prefix + fill, tint)
            if kind != 'absorb':
                stylize(archive, f'heart-{kind}-{fill}-hc', 'hud/heart/' + prefix + 'hardcore_' + fill, tint)
    stylize(archive, 'heart-empty', 'hud/heart/container', '#35434B', True)
    stylize(archive, 'heart-empty-hc', 'hud/heart/container_hardcore', '#35434B', True)
    for kind, prefix, tint in [('food', 'food', '#BD7C46'), ('hunger', 'food', '#A68D59'),
                               ('armor', 'armor', '#99ADB9'), ('mount', 'heart/vehicle', '#D65357')]:
        for state in ['empty', 'full', 'half']:
            suffix = '_hunger' if kind == 'hunger' else ''
            filename = 'hud/' + prefix + '_' + state + suffix
            if kind == 'mount' and state == 'empty':
                filename = 'hud/heart/vehicle_container'
            stylize(archive, kind + '-' + state, filename, tint, state == 'empty')
    for state, filename in [('full', 'air'), ('burst', 'air_bursting'), ('empty', 'air_empty')]:
        stylize(archive, 'air-' + state, 'hud/' + filename, '#78C5E3', state == 'empty')
    for item, filename in [('diamond', 'diamond'), ('iron_sword', 'iron_sword'), ('golden_apple', 'golden_apple'),
                           ('ender_pearl', 'ender_pearl'), ('amethyst_shard', 'amethyst_shard'), ('torch', 'stick'),
                           ('shield', 'iron_ingot'), ('helmet', 'iron_helmet'), ('chestplate', 'iron_chestplate'),
                           ('leggings', 'iron_leggings'), ('boots', 'iron_boots')]:
        filename = 'assets/minecraft/textures/item/' + filename + '.png'
        embed(item, archive.read(filename), 'JAR:' + filename)
    embed('crosshair', archive.read('assets/minecraft/textures/gui/sprites/hud/crosshair.png'), 'JAR:gui/sprites/hud/crosshair.png')
    for name in ['speed', 'resistance', 'poison']:
        filename = 'assets/minecraft/textures/mob_effect/' + name + '.png'
        embed('effect-' + name, archive.read(filename), 'JAR:' + filename)
    embed('steve', archive.read('assets/minecraft/textures/entity/player/wide/steve.png'), 'JAR:player/wide/steve.png')

payload = {'glyphs': glyphs, 'paths': paths, 'images': images, 'flaskPath': flask_paths,
           'items': ['iron_sword', 'golden_apple', 'ender_pearl', 'amethyst_shard', 'torch']}
assert len(flask_paths) == 22 and all(char in glyphs for char in 'іїєґІЇЄҐ')
encoded = json.dumps(payload, ensure_ascii=False, separators=(',', ':'))
if '__HUD_PAYLOAD__' in html:
    html = html.replace('__HUD_PAYLOAD__', encoded)
else:
    html = re.sub(r'(?<=<script id="payload" type="application/json">).*?(?=</script>)',
                  lambda _: encoded, html, count=1, flags=re.S)
PAGE.write_text(html, encoding='utf-8')
(OUT / 'provenance.json').write_text(json.dumps({'sources': sources, 'glyphCount': len(glyphs),
    'flaskPaths': len(flask_paths), 'sourceScreenshotUnchanged': True,
    'renderer': 'Standalone Canvas UI; vanilla icon silhouettes re-expressed as SVG primitives; not an installed pack',
    'worldBackgrounds': 'Actual F1 captures in an isolated copied world; sources/*.png'},
    ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Review MB:', round(PAGE.stat().st_size / 1e6, 2), 'images:', len(images), 'glyphs:', len(glyphs))
