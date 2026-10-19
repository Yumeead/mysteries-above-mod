"""Launch the unchanged mod with a review-only helper, copied world and optional pack."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
cfg = json.loads((HERE / 'launch.json').read_text())
integration = '--integration' in sys.argv
helper = HERE / 'helper'
sources = ['ReviewClient.java'] + ([] if integration else ['GraphicsSkinMixin.java'])
subprocess.run([str(Path(cfg['java']).with_name('javac.exe')), '-encoding', 'UTF-8', '-proc:none',
    '-cp', cfg['classpath'], '-d', str(helper), *[str(HERE / name) for name in sources]], check=True)
(helper / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'mysteries_menu_review',
    'version': '0.0.1', 'name': 'Isolated menu visual review', 'environment': 'client',
    'entrypoints': {'client': ['preview.ReviewClient']}, 'mixins': [] if integration else ['menu-review.mixins.json'],
    'depends': {'minecraft': '26.2', 'fabricloader': '>=0.19.3', 'fabric-api': '*'}}), encoding='utf-8')
(helper / 'menu-review.mixins.json').write_text(json.dumps({'required': True, 'package': 'preview.mixin',
    'compatibilityLevel': 'JAVA_25', 'client': ['GraphicsSkinMixin'], 'injectors': {'defaultRequire': 1}}), encoding='utf-8')
game = Path(tempfile.mkdtemp(prefix='mysteries-gold-menu-review-'))
source = ROOT / 'mod/run-flask-review-20261018'
shutil.copytree(source / 'saves/Flask Review', game / 'saves/Menu Review', ignore=shutil.ignore_patterns('session.lock'))
shutil.copytree(source / 'config', game / 'config')
if not integration:
    shutil.copytree(HERE / 'review-pack', game / 'resourcepacks/gold-review')
options = (source / 'options.txt').read_text(encoding='utf-8')
for key, value in {'lang': 'uk_ua', 'guiScale': '3', 'pauseOnLostFocus': 'false', 'fullscreen': 'false',
                   'overrideWidth': '1920', 'overrideHeight': '1080', 'renderDistance': '5', 'simulationDistance': '5',
                   'resourcePacks': '["vanilla","fabric"]' if integration else '["vanilla","fabric","file/gold-review"]'}.items():
    if re.search(r'^' + key + ':', options, re.M):
        options = re.sub(r'^' + key + r':.*$', lambda _: key + ':' + value, options, flags=re.M)
    else: options += '\n' + key + ':' + value
(game / 'options.txt').write_text(options, encoding='utf-8')
jvm = [s for s in cfg['jvmArgs'] if not s.startswith('@')]
if integration:
    jvm.append('-Dmysteries.menu.integration=true')
command = [cfg['java'], *jvm, '-Dfabric.addMods=' + str(helper), '-cp', cfg['classpath'], cfg['main'],
    '--username', 'Tester1', '--version', '26.2', '--accessToken', '0', '--gameDir', str(game),
    '--width', '1920', '--height', '1080']
env = dict(os.environ)
env.pop('MYSTERIES_WHEEL_SHOTS', None)
env.pop('MYSTERIES_SHOTS_WORLD', None)
log = open(game / 'launch.log', 'w', encoding='utf-8')
process = subprocess.Popen(command, cwd=ROOT / 'mod', stdout=log, stderr=subprocess.STDOUT,
    env=env, creationflags=subprocess.CREATE_NO_WINDOW)
(HERE / 'session.json').write_text(json.dumps({'pid': process.pid, 'gameDirectory': str(game),
    'helper': str(helper), 'integration': integration, 'productionSourcesUnchanged': not integration}, indent=2), encoding='utf-8')
print(json.dumps({'pid': process.pid, 'gameDirectory': str(game)}))
