"""Save untouched native Minecraft screenshots, plus capture provenance."""
from pathlib import Path
from PIL import Image
import datetime
import hashlib
import json
import shutil
import sys
import time

HERE = Path(__file__).resolve().parent
game = Path(json.loads((HERE / 'session.json').read_text())['gameDirectory'])
OUT = HERE.parent / 'in-game'
OUT.mkdir(exist_ok=True)


def await_file(path, timeout=70):
    deadline = time.monotonic() + timeout
    while not path.exists():
        if (game / 'failed.txt').exists():
            raise RuntimeError((game / 'failed.txt').read_text())
        if time.monotonic() >= deadline:
            raise TimeoutError(str(path))
        time.sleep(.25)


await_file(game / 'ready.txt')
names = sys.argv[1:] or ['inventory', 'chest', 'crafting', 'crafting-page2', 'tooltip', 'pause', 'pause-focus', 'settings', 'settings-hover']
report_path = OUT / 'captures.json'
report = json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else []
for scale in (2, 3):
    for name in names:
        assert name in ('inventory', 'chest', 'crafting', 'crafting-page2', 'tooltip', 'pause', 'pause-focus', 'settings', 'settings-hover')
        source = game / 'screenshots' / f'gold-{name}-gs{scale}.png'
        source.unlink(missing_ok=True)
        (game / 'request.txt').write_text(f'{name} {scale} {time.time_ns()}', encoding='utf-8')
        await_file(source)
        time.sleep(.35)
        target = OUT / source.name
        shutil.copyfile(source, target)
        with Image.open(target) as im:
            assert im.size == (1920, 1080), im.size
        gui = (game / 'last-screen.txt').read_text()
        assert gui.endswith('960x540' if scale == 2 else '640x360'), gui
        expected = 'PauseScreen' if name.startswith('pause') else 'SoundOptionsScreen' if name.startswith('settings') else 'InventoryScreen' if name in ('inventory', 'tooltip') else 'ContainerScreen' if name == 'chest' else 'CraftingScreen'
        assert gui.split(' ')[0].endswith('.' + expected), gui
        raw = target.read_bytes()
        assert raw == source.read_bytes()
        record = dict(name=name, guiScale=scale, file=target.name, nativeScreen=gui,
            physicalSize=[1920, 1080], source=str(source), sha256=hashlib.sha256(raw).hexdigest(),
            edited=False, capturedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat())
        if name in ('settings-hover', 'crafting-page2'):
            record['nativeInput'] = (game / ('slider-input.txt' if name == 'settings-hover' else 'page-input.txt')).read_text()
        if name.startswith('crafting'):
            values = json.loads((game / 'pagination.txt').read_text())
            record['pagination'] = dict(origin=values[:2], label=values[2:6], back=values[6:10],
                forward=values[10:14], current=values[14], total=values[15])
        report = [r for r in report if not (r['name'] == name and r['guiScale'] == scale)] + [record]
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(target.name, gui, flush=True)
