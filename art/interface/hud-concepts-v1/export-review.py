"""Render the offline design sheet using integer Canvas coordinates in headless Chrome."""
from pathlib import Path
import base64
import html
import json
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parent
profile = tempfile.mkdtemp(prefix='mysteries-hud-design-chrome-')
result = subprocess.run([
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '--headless', '--disable-gpu',
    '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--disable-background-networking',
    '--no-sandbox', '--user-data-dir=' + profile, '--force-device-scale-factor=1',
    '--virtual-time-budget=12000', '--dump-dom', (root / 'review.html').as_uri() + '?export=1'],
    capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
match = re.search(r'<pre id="export-data">(.*?)</pre>', result.stdout, re.S)
if not match:
    errors = re.findall(r'data-error="([^"]*)"', result.stdout)
    raise RuntimeError('Browser did not export: ' + str(errors) + '\n' + result.stderr[-800:])
data = json.loads(html.unescape(match.group(1)))
for name, url in data['images'].items():
    (root / name).write_bytes(base64.b64decode(url.split(',', 1)[1]))
(root / 'browser-verification.json').write_text(json.dumps(data['verification'], ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Exported PNGs:', len(data['images']), data['verification'])
