"""Read-only Minecraft 26.2 evidence; writes only beside this design document."""
from pathlib import Path
from PIL import Image
import hashlib
import io
import json
import re
import subprocess
import zipfile

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
JAR = Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def protected():
    files = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    return {p: sha(ROOT / p) for p in files if p and (ROOT / p).is_file()
            and not p.startswith('art/interface/inventories-menus-v1/')}


def write(name, value):
    (OUT / name).write_text(value, encoding='utf-8')


if __name__ == '__main__':
    baseline = OUT / 'protected-before.json'
    if not baseline.exists():
        write(baseline.name, json.dumps(protected(), ensure_ascii=False, indent=2))
    with zipfile.ZipFile(JAR) as archive:
        names = archive.namelist()
        version = json.loads(archive.read('version.json'))
        assert version['id'] == '26.2'
        classes = sorted(n[:-6].replace('/', '.') for n in names if n.endswith('Screen.class')
                         and '$' not in n and (n.startswith('net/minecraft/client/gui/screens/')
                         or n.startswith('com/mojang/realmsclient/') or n == 'net/minecraft/realms/RealmsScreen.class'))
        extra = ['net.minecraft.client.gui.components.' + n for n in [
            'AbstractButton', 'AbstractWidget', 'AbstractSliderButton', 'Button', 'CycleButton',
            'Checkbox', 'EditBox', 'StateSwitchingButton', 'ImageButton', 'AbstractSelectionList']]
        extra += ['net.minecraft.client.gui.screens.inventory.EffectsInInventory',
                  'net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil',
                  'net.minecraft.client.gui.GuiGraphicsExtractor',
                  'net.minecraft.client.gui.screens.recipebook.RecipeBookComponent',
                  'net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton',
                  'net.minecraft.client.gui.screens.recipebook.RecipeButton']
        dump = subprocess.run(['javap', '-p', '-c', '-classpath', str(JAR), *classes, *extra],
                              capture_output=True, text=True, encoding='utf-8', errors='replace', check=True)
        write('minecraft-bytecode.txt', dump.stdout)
        common = JAR.with_name('minecraft-common.jar')
        menu_names = ['AbstractContainerMenu', 'ChestMenu', 'InventoryMenu', 'CraftingMenu', 'AbstractFurnaceMenu',
                      'AnvilMenu', 'BeaconMenu', 'BrewingStandMenu', 'CartographyTableMenu', 'CrafterMenu',
                      'DispenserMenu', 'EnchantmentMenu', 'GrindstoneMenu', 'HopperMenu', 'HorseInventoryMenu',
                      'LoomMenu', 'MerchantMenu', 'ShulkerBoxMenu', 'SmithingMenu', 'StonecutterMenu']
        menus = subprocess.run(['javap', '-p', '-c', '-classpath', str(common),
            *['net.minecraft.world.inventory.' + n for n in menu_names]], capture_output=True, text=True,
            encoding='utf-8', errors='replace', check=True)
        write('slot-layout-bytecode.txt', menus.stdout)
        chunks = re.split(r'(?=Compiled from ")', dump.stdout)
        by_class = {}
        for chunk in chunks:
            match = re.search(r'(?:class|interface) ([\w.$]+)', chunk)
            if match:
                by_class[match[1]] = chunk
        resource_rows = ['path\twidth\theight\trendering\tscaling_json\tsha256']
        resources = []
        for name in sorted(names):
            if not name.startswith('assets/minecraft/textures/gui/') or not name.endswith('.png'):
                continue
            raw = archive.read(name)
            im = Image.open(io.BytesIO(raw))
            meta = json.loads(archive.read(name + '.mcmeta')) if name + '.mcmeta' in names else {}
            scaling = meta.get('gui', {}).get('scaling', {})
            sprite = '/gui/sprites/' in name
            kind = ('sprite-nine_slice' if scaling.get('type') == 'nine_slice' else
                    'sprite-' + scaling.get('type', 'stretch')) if sprite else 'direct-texture-UV'
            record = dict(path=name, width=im.width, height=im.height, rendering=kind, scaling=scaling,
                          sha256=hashlib.sha256(raw).hexdigest())
            resources.append(record)
            resource_rows.append('\t'.join([name, str(im.width), str(im.height), kind,
                                           json.dumps(scaling, separators=(',', ':')), record['sha256']]))
        write('textures-all.tsv', '\n'.join(resource_rows) + '\n')
        effects = ['path\twidth\theight\trendering\tscaling_json\tsha256']
        for name in sorted(names):
            if name.startswith('assets/minecraft/textures/mob_effect/') and name.endswith('.png'):
                raw = archive.read(name)
                im = Image.open(io.BytesIO(raw))
                effects.append('\t'.join([name, str(im.width), str(im.height), 'atlas-sprite; no nine_slice', '{}',
                                          hashlib.sha256(raw).hexdigest()]))
        write('effects-all.tsv', '\n'.join(effects) + '\n')
        relevant = [r for r in resources if r['path'].endswith('/gui/recipe_book.png') or '/gui/container/' in r['path'] or any(
            '/gui/sprites/' + p in r['path'] for p in ['container/', 'widget/', 'recipe_book/', 'tooltip/', 'pause_menu/'])]
        write('textures-in-scope.tsv', '\n'.join([resource_rows[0]] + [line for line in resource_rows[1:]
              if line.split('\t')[0] in {r['path'] for r in relevant}]) + '\n')
        screen_rows = ['class\tparent\tscope\tdirect_resources\trendering']
        for name in classes:
            chunk = by_class[name]
            parent = re.search(r' extends ([\w.$<>]+)', chunk)
            refs = sorted(set(re.findall(r'// String ([^\r\n]+)', chunk)))
            refs = [r for r in refs if r != '.png' and (r.endswith('.png') or r.startswith(('container/', 'recipe_book/', 'widget/', 'pause_menu/', 'tooltip/')))]
            kinds = set()
            for ref in refs:
                for r in resources:
                    if r['path'].endswith('/' + ref + ('' if ref.endswith('.png') else '.png')):
                        kinds.add(r['rendering'])
            scope = 'inventory' if '.inventory.' in name else 'options' if '.options.' in name else 'pause' if name.endswith('.PauseScreen') else 'reference-only'
            screen_rows.append('\t'.join([name, parent[1] if parent else '', scope, ';'.join(refs),
                ';'.join(sorted(kinds)) if kinds else 'code/inherited/shared-widgets; see bytecode and AUDIT.md']))
        write('screens-all.tsv', '\n'.join(screen_rows) + '\n')
        write('screens-all.md', '# Усі класи екранів 26.2\n\n'
            'Повний перелік наявності, включно з абстрактними основами; не розширює обсяг змін. '
            'Віджети/текст/прямокутники з коду не мають окремої великої фонової PNG. '
            'У колонці показані прямі посилання; успадковані фони й віджети див. у вказаного базового класу та в AUDIT.md.\n\n'
            '| Клас | Базовий клас | Область | Прямі ресурси | Спосіб |\n|---|---|---|---|---|\n' + '\n'.join(
                '| ' + ' | '.join('`' + v.replace('net.minecraft.client.gui.screens.', '').replace('net.minecraft.client.gui.components.', '') + '`'
                if v else '—' for v in row.split('\t')) + ' |' for row in screen_rows[1:]) + '\n')
        write('textures-in-scope.md', '# Реальні текстури 26.2 у цьому етапі\n\n'
              'Шляхи від `assets/minecraft/textures/gui/`. `stretch` — спрайт без 9-slice metadata; '
              'прямий UV — окремий PNG, його `.mcmeta` не перетворює виклик blit на 9-slice.\n\n'
              '| Шлях | PNG px | Спосіб | Метадані scaling |\n|---|---|---|---|\n' + '\n'.join(
                  f"| `{r['path'].split('/gui/')[1]}` | {r['width']}×{r['height']} | {r['rendering']} | `{json.dumps(r['scaling'], separators=(',', ':'))}` |"
                  for r in relevant) + '\n')
        sources = []
        base = Path.home() / '.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-resource-loader-v1'
        for jar in base.glob('**/*sources.jar'):
            with zipfile.ZipFile(jar) as source:
                for name in source.namelist():
                    if name.endswith(('/ResourceLoader.java', '/PackActivationType.java')):
                        sources.append(f'// {jar.name}: {name}\n' + source.read(name).decode())
        write('fabric-pack-api.txt', '\n\n'.join(sources))
        write('provenance.json', json.dumps(dict(version=version, jar=str(JAR), sha256=sha(JAR),
            screens=len(classes), guiPng=len(resources), scopePng=len(relevant), effectIcons=len(effects) - 1,
            nineSlice=sum(r['rendering'] == 'sprite-nine_slice' for r in resources),
            scopeNineSlice=sum(r['rendering'] == 'sprite-nine_slice' for r in relevant)), indent=2))
        print(json.dumps(dict(screens=len(classes), guiPng=len(resources), scopePng=len(relevant))))
