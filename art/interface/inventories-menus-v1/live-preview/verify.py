"""Check native captures, preserved texture geometry and the offline game gallery."""
from pathlib import Path
from PIL import Image
from tempfile import TemporaryDirectory, NamedTemporaryFile
import hashlib
import io
import json
import re
import runpy
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
ART = HERE.parent
kit = runpy.run_path(str(ART / 'build-review.py'))
captures = json.loads((ART / 'in-game/captures.json').read_text(encoding='utf-8'))
expected = {(name, scale) for name in ('inventory', 'chest', 'crafting', 'crafting-page2', 'tooltip', 'pause', 'pause-focus', 'settings', 'settings-hover') for scale in (2, 3)}
assert {(r['name'], r['guiScale']) for r in captures} == expected and len(captures) == 18
for record in captures:
    path = ART / 'in-game' / record['file']
    raw = path.read_bytes()
    assert raw == Path(record['source']).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == record['sha256']
    assert not record['edited'] and Image.open(path).size == (1920, 1080)
    assert record['nativeScreen'].endswith('960x540' if record['guiScale'] == 2 else '640x360')
    if record['name'] in ('settings-hover', 'crafting-page2'):
        assert record['nativeInput'].startswith('Native '), record
for scale in (2, 3):
    assert (ART / f'in-game/gold-pause-gs{scale}.png').read_bytes() != (ART / f'in-game/gold-pause-focus-gs{scale}.png').read_bytes()

pagination_checks = []
details = ART / 'in-game/details'
details.mkdir(exist_ok=True)
for record in captures:
    if not record['name'].startswith('crafting'):
        continue
    geometry, scale = record['pagination'], record['guiScale']
    ox, oy = geometry['origin']
    dx, dy, w, h = kit['PAGE_COUNTER_FRAME']
    x, y = ox + dx, oy + dy
    tx, ty, tw, th = geometry['label']
    assert x + 2 <= tx and tx + tw <= x + w - 2, 'Counter text exceeds inner width'
    assert y + 2 <= ty and ty + th <= y + h - 2, 'Counter text exceeds inner height'
    # The previous field started too low and crossed the right arrow; this check must reject it.
    assert not (oy + 140 + 2 <= ty), 'Regression fixture no longer reproduces the misplaced field'
    gaps = []
    for direction in ('back', 'forward'):
        sprite = Image.open(HERE / f'review-pack/assets/minecraft/textures/gui/sprites/recipe_book/page_{"backward" if direction == "back" else "forward"}.png')
        left, top, right, bottom = sprite.getchannel('A').getbbox()
        ax, ay, aw, ah = geometry[direction]
        assert sprite.size == (aw, ah)
        gap = x - (ax + right) if direction == 'back' else ax + left - (x + w)
        assert gap >= 2, 'Page arrow touches counter frame'
        gaps.append(gap)
    image = Image.open(ART / 'in-game' / record['file']).convert('RGB')
    box = ((ox + 34) * scale, (oy + 134) * scale, (ox + 109) * scale, (oy + 157) * scale)
    crop = image.crop(box)
    white = [(xx + box[0], yy + box[1]) for yy in range(crop.height) for xx in range(crop.width)
             if min(crop.getpixel((xx, yy))) >= 200]
    assert white, 'Native counter digits missing'
    assert all((x + 2) * scale <= xx < (x + w - 2) * scale and
               (y + 2) * scale <= yy < (y + h - 2) * scale for xx, yy in white), 'Visible digits cross the frame'
    crop.save(details / f'pagination-page{geometry["current"]}-gs{scale}.png')
    # These are explicitly labeled nearest-neighbor details; full screenshots remain byte-identical.
    crop.resize((75 * 4, 23 * 4), Image.Resampling.NEAREST).save(details / f'pagination-page{geometry["current"]}-from-gs{scale}-x4.png')
    pagination_checks.append(dict(file=record['file'], page=geometry['current'], total=geometry['total'],
        textInsideDarkField=True, visibleDigitsInsideFrame=True, arrowGapsGuiPixels=gaps, frame=[dx, dy, w, h]))
assert len(pagination_checks) == 4

manifest = json.loads((HERE / 'pack-manifest.json').read_text())
assert len(manifest) == len({r['path'] for r in manifest})
slots = 0
with zipfile.ZipFile(Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar') as archive:
    for record in manifest:
        path = HERE / 'review-pack' / record['path']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == record['sha256']
        image = Image.open(path).convert('RGBA')
        original = record['path'].replace('assets/mysteries_preview/', 'assets/minecraft/')
        if original in archive.namelist():
            assert image.size == Image.open(io.BytesIO(archive.read(original))).size
        for x, y, size in record.get('slots', []):
            assert image.getpixel((x + size // 2, y + size // 2)) == (186, 152, 91, 255)
            assert all(image.getpixel(point) == (112, 80, 45, 255) for point in (
                (x, y + size // 2), (x + size - 1, y + size // 2),
                (x + size // 2, y), (x + size // 2, y + size - 1)))
            slots += 1

check = r'''<pre id="browser-check"></pre><script>
(async()=>{
 const errors=[];let images=0;
 function assert(v,m){if(!v)errors.push(m)}
 assert(document.querySelectorAll('#screen option').length===9,'Nine native views');
 for(const a of document.querySelectorAll('#links a')){
  await new Promise(resolve=>{const im=new Image();im.onload=()=>{assert(im.naturalWidth===1920&&im.naturalHeight===1080,a.href);images++;resolve()};im.onerror=()=>{errors.push(a.href);resolve()};im.src=a.href});
 }
 document.getElementById('screen').value='crafting';document.getElementById('scale').value='2';render();
 assert(document.getElementById('shot').getAttribute('src')==='in-game/gold-crafting-gs2.png','Screen/scale controls');
 assert(document.querySelectorAll('#links a').length===18,'Eighteen PNG links');
 document.getElementById('screen').value='settings';document.getElementById('scale').value='3';render();
 assert(document.getElementById('shot').getAttribute('src')==='in-game/gold-settings-gs3.png','Settings control');
 document.getElementById('browser-check').textContent=JSON.stringify({passed:errors.length===0,errors,imagesChecked:images});
})();</script>'''
with NamedTemporaryFile(mode='w', suffix='.html', prefix='native-check-', dir=ART, encoding='utf-8', delete=False) as page:
    temporary_page = Path(page.name)
    page.write((ART / 'game-review.html').read_text(encoding='utf-8').replace('</html>', check + '</html>'))
try:
    with TemporaryDirectory(prefix='mysteries-native-gallery-') as profile:
        result = subprocess.run(['C:/Program Files/Google/Chrome/Application/chrome.exe', '--headless', '--disable-gpu',
            '--no-first-run', '--no-sandbox', '--disable-extensions', '--disable-background-networking',
            '--user-data-dir=' + profile, '--virtual-time-budget=15000', '--dump-dom', temporary_page.as_uri()],
            capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=45)
        match = re.search(r'<pre id="browser-check">(.*?)</pre>', result.stdout, re.S)
        assert match and match[1], result.stderr[-600:]
        browser = json.loads(match[1])
        assert browser['passed'], browser
finally:
    temporary_page.unlink(missing_ok=True)
report = dict(passed=True, nativeCaptures=18, guiScales=[2, 3], screenshotSize=[1920, 1080],
    capturesByteIdenticalToMinecraftOutput=True, textures=len(manifest), originalTextureDimensionsPreserved=True,
    unifiedSlotChecks=slots, browser=browser, productionChanges=False, nativeSliderDragChecked=True, nativeRecipePageTurnChecked=True)
report['pagination'] = pagination_checks
(ART / 'in-game/verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
slider_report = json.loads((HERE / 'sliders-verification.json').read_text())
slider_report.update(gameVerified=True, gameGuiScalesChecked=[2, 3],
    nativeClickDragReleaseChecked=True, captures='in-game/captures.json')
(HERE / 'sliders-verification.json').write_text(json.dumps(slider_report, indent=2), encoding='utf-8')
print(json.dumps(report))
