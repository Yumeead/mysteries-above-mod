"""Build design-only PNGs and an offline review; never export into game resources."""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import hashlib
import io
import json
import re
import subprocess
import zipfile

OUT = Path(__file__).resolve().parent

import sys
sys.path.insert(0, str(OUT.parent))
from ui_kit import kit
from ui_kit.kit import (BODY, DARK, GOLD, INK, LABEL, LIGHT, MUTED, NEAREST, PAGE_COUNTER_FRAME, PANEL_DARK, PANEL_LIGHT, SLOT_EDGE, SLOT_FILL, SLOT_OUTLINE, SLOT_SHADOW, TEXT, arrow, canvas, magnifier, metal_frame, nine, outline, page_arrow, page_counter, panel, paste, rect, seed, slider_sprite)
ROOT = OUT.parents[2]
JAR = Path.home() / '.gradle/caches/fabric-loom/26.2/minecraft-client.jar'
PREVIOUS = ROOT / 'art/interface/bottom-panel-v4/review.html'
PAYLOAD = json.loads(re.search(r'<script id="payload" type="application/json">(.*?)</script>',
                             PREVIOUS.read_text(encoding='utf-8'), re.S)[1])
GLYPHS = PAYLOAD['glyphs']
MISSING, OVERFLOW = set(), []
ASSETS = {}
SLOTS = {}










def width(text):
    return sum(GLYPHS.get(c, GLYPHS[' '])['advance'] for c in text) - (1 if text else 0)


def text(im, s, x, y, color=TEXT, shadow=False, center=False, max_width=None):
    if max_width is not None and width(s) > max_width:
        OVERFLOW.append((s, width(s), max_width))
    if center:
        x -= width(s) // 2
    for c in s:
        if c not in GLYPHS:
            MISSING.add(c)
            continue
        glyph = GLYPHS[c]
        if shadow:
            for gx, gy in glyph['pixels']:
                rect(im, x + gx + 1, y + gy + glyph['offset'] + 1, 1, 1, '#0A0D10')
        for gx, gy in glyph['pixels']:
            rect(im, x + gx, y + gy + glyph['offset'], 1, 1, color)
        x += glyph['advance']












def button(im, x, y, w, label='', state='normal', h=20, selected=False):
    kit.button(im, x, y, w, state, h, selected)
    text(im, label, x + w // 2, y + (h - 8) // 2 + (state == 'pressed'),
         '#827D70' if state == 'disabled' else TEXT, shadow=state != 'disabled', center=True, max_width=w - 8)


def item(name):
    return ASSETS[name]


def gold_icon(source):
    target = canvas(*source.size)
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.getpixel((x, y))
            if a:
                color = INK if max(r, g, b) < 55 else GOLD['dark'] if max(r, g, b) < 115 else GOLD['mid'] if max(r, g, b) < 205 else GOLD['light']
                target.putpixel((x, y), tuple(int(color[k:k + 2], 16) for k in (1, 3, 5)) + (a,))
    return target


def slot(im, x, y, name=None, count=None, selected=False, size=18):
    kit.slot(im, x, y, selected, size)
    if name:
        offset = (size - 16) // 2
        paste(im, item(name), x + offset, y + offset)
    if count:
        s = str(count)
        text(im, s, x + size - width(s), y + size - 8, shadow=True)


GOLD_ITEMS = ['golden_sword', 'golden_pickaxe', 'golden_axe', 'golden_shovel', 'golden_hoe',
              'golden_apple', 'golden_carrot', 'gold_ingot', 'raw_gold']


def grid(im, x, y, cols, rows, names=(), selected=-1):
    for n in range(cols * rows):
        slot(im, x + n % cols * 18, y + n // cols * 18,
             names[n] if n < len(names) else None, selected=n == selected)


def player_inventory(im, x, y, top=83, hotbar=141, label=True, label_y=None):
    if label:
        text(im, 'Інвентар', x + 8, y + (top - 11 if label_y is None else label_y), LABEL)
    grid(im, x + 7, y + top, 9, 3, ['diamond', 'coal', 'iron_ingot', 'bread', 'ender_pearl'])
    grid(im, x + 7, y + hotbar, 9, 1, GOLD_ITEMS)


def book_button(im, x, y):
    button(im, x, y, 20, '')
    paste(im, ASSETS['book'], x + 2, y + 2)










def paper_doll(im, x, y):
    # shortcut: front skin projection stands in for the live 3D model; inspect model overlap in game after stage 2.
    skin = ASSETS['steve']
    for box, dx, dy in [((8, 8, 16, 16), 4, 0), ((20, 20, 28, 32), 4, 8),
                        ((44, 20, 48, 32), 0, 8), ((36, 52, 40, 64), 12, 8),
                        ((4, 20, 8, 32), 4, 20), ((20, 52, 24, 64), 8, 20)]:
        paste(im, skin.crop(box), x + dx, y + dy)


def effects(im, x, y):
    for n, (label, icon, duration) in enumerate([('Швидкість II', 'speed', '1:42'), ('Опір', 'resistance', '0:58')]):
        panel(im, x, y + n * 34, 116, 32, light=False)
        paste(im, ASSETS['effect-' + icon], x + 5, y + 7 + n * 34)
        text(im, label, x + 29, y + 6 + n * 34)
        text(im, duration, x + 29, y + 18 + n * 34, '#B5B1A8')


def scrollbar(im, x, y, height, thumb=24, offset=0, w=6):
    rect(im, x, y, w, height, '#222B31')
    outline(im, x, y, w, height, '#8D805E')
    rect(im, x + 1, y + offset + 1, w - 2, thumb - 2, GOLD['mid'])
    rect(im, x + 1, y + offset + 1, w - 2, 1, GOLD['light'])


def field(im, x, y, w, value, focused=False):
    rect(im, x, y, w, 20, INK)
    outline(im, x, y, w, 20, GOLD['light'] if focused else GOLD['dark'])
    rect(im, x + 1, y + 1, w - 2, 18, '#10171E')
    text(im, value, x + 5, y + 6)
    if focused:
        rect(im, x + 6 + width(value), y + 5, 1, 9, TEXT)




def slider(im, x, y, w, value, label):
    paste(im, slider_sprite(w, 20), x, y)
    bx = x + (w - 8) * value // 100
    paste(im, slider_sprite(8, 20, handle=True), bx, y)
    text(im, label, x + w // 2, y + 6, center=True, shadow=True)


def checkbox(im, x, y, checked=False, focused=False):
    button(im, x, y, 17, '', 'focus' if focused else 'normal', h=17)
    if checked:
        for dx, dy in [(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]:
            rect(im, x + dx, y + dy, 1, 2, TEXT)


def inventory(im, x, y):
    panel(im, x, y, 176, 166)
    text(im, 'Створення', x + 97, y + 6, LABEL)
    grid(im, x + 97, y + 17, 2, 2, ['gold_ingot'])
    slot(im, x + 153, y + 27, 'gold_nugget', count=9)
    arrow(im, x + 134, y + 27, w=18)
    for n, name in enumerate(['iron_helmet', 'iron_chestplate', 'iron_leggings', 'iron_boots']):
        slot(im, x + 7, y + 7 + n * 18, name)
    rect(im, x + 25, y + 7, 51, 72, DARK)
    outline(im, x + 25, y + 7, 51, 72, GOLD['dark'])
    paper_doll(im, x + 43, y + 33)
    slot(im, x + 76, y + 61, 'golden_apple')
    book_button(im, x + 104, y + 61)
    player_inventory(im, x, y, label=False)
    effects(im, x + 178, y + 2)


def chest(im, x, y, rows=3):
    h = 114 + 18 * rows
    panel(im, x, y, 176, h)
    text(im, 'Велика скриня' if rows == 6 else 'Скриня', x + 8, y + 6, LABEL)
    grid(im, x + 7, y + 17, 9, rows, GOLD_ITEMS + ['diamond', 'iron_ingot', 'coal'], selected=5)
    player_inventory(im, x, y, top=rows * 18 + 30, hotbar=rows * 18 + 88, label_y=rows * 18 + 20)


def crafting(im, x, y):
    panel(im, x, y, 176, 166)
    text(im, 'Створення', x + 28, y + 6, LABEL)
    grid(im, x + 29, y + 16, 3, 3, ['gold_ingot'] * 4 + ['apple'] + ['gold_ingot'] * 4)
    arrow(im, x + 91, y + 34)
    slot(im, x + 123, y + 34, 'golden_apple')
    book_button(im, x + 5, y + 34)
    player_inventory(im, x, y)


def furnace(im, x, y):
    panel(im, x, y, 176, 166)
    text(im, 'Піч', x + 88, y + 6, LABEL, center=True)
    slot(im, x + 55, y + 16, 'raw_gold')
    slot(im, x + 55, y + 52, 'coal')
    slot(im, x + 115, y + 34, 'gold_ingot')
    outline(im, x + 111, y + 30, 26, 26, GOLD['dark'])
    arrow(im, x + 79, y + 35)
    for xx, yy in [(2, 8), (3, 6), (4, 7), (5, 3), (6, 0), (7, 4), (8, 6), (9, 5), (10, 8)]:
        rect(im, x + 56 + xx, y + 37 + yy, 2, 12 - yy, '#C88643')
    book_button(im, x + 20, y + 34)
    player_inventory(im, x, y)


def recipe_book(im, x, y):
    panel(im, x, y, 147, 166)
    magnifier(im, x + 9, y + 13)
    field(im, x + 25, y + 12, 93, 'Пошук')
    button(im, x + 122, y + 12, 20, '', selected=True)
    paste(im, ASSETS['book'], x + 124, y + 14)
    for n in range(5):
        panel(im, x - 27 + (0 if n == 1 else 2), y + 3 + n * 29, 30 if n == 1 else 28, 27)
        if n == 1:
            rect(im, x + 1, y + 5 + n * 29, 2, 23, BODY)
        paste(im, item(GOLD_ITEMS[n]), x - 21, y + 8 + n * 29)
    for row in range(4):
        for col in range(5):
            xx, yy = x + 11 + col * 25, y + 39 + row * 25
            slot(im, xx, yy, GOLD_ITEMS[(col + row) % 9], size=24, selected=row == col == 0)
    page_counter(im, x, y)
    text(im, '1/3', x + 73, y + 141, TEXT, shadow=True, center=True)
    paste(im, page_arrow(backward=True), x + 38, y + 137)
    paste(im, page_arrow(), x + 93, y + 137)


def creative(im, x, y, search=False):
    for n in range(7):
        tab_x = x + (169 if n == 6 else n * 27)
        button(im, tab_x, y - 28, 26, '', h=32, selected=n == (6 if search else 1))
        paste(im, item(GOLD_ITEMS[n]), tab_x + 5, y - 19)
        button(im, tab_x, y + 132, 26, '', h=32)
        paste(im, item((GOLD_ITEMS + ['book'])[n + 2]), tab_x + 5, y + 139)
    panel(im, x, y, 195, 136)
    if search:
        text(im, 'Пошук', x + 8, y + 6, LABEL)
        rect(im, x + 81, y + 6, 89, 9, '#F0E6C9')
        text(im, 'золото', x + 83, y + 7, LABEL)
        rect(im, x + 84 + width('золото'), y + 6, 1, 9, LABEL)
    else:
        text(im, 'Природні блоки', x + 8, y + 6, LABEL)
    grid(im, x + 8, y + 17, 9, 5, (GOLD_ITEMS + ['coal', 'diamond', 'iron_ingot', 'bread', 'ender_pearl']) * 4)
    scrollbar(im, x + 175, y + 18, 89, thumb=15, offset=19, w=12)
    grid(im, x + 8, y + 111, 9, 1, GOLD_ITEMS)


def tooltip(im, x, y):
    panel(im, x, y, 190, 67, light=False)
    rect(im, x + 3, y + 3, 184, 61, (25, 22, 17, 237))
    text(im, 'Золотий меч', x + 7, y + 8, '#FFFFFF', shadow=True)
    text(im, 'Коли в основній руці:', x + 7, y + 26, '#AAAAAA', shadow=True)
    text(im, '4 шкоди від атаки', x + 7, y + 39, '#00AA00', shadow=True)
    text(im, '1,6 швидкості атаки', x + 7, y + 52, '#00AA00', shadow=True)


def settings(im, x, y):
    panel(im, x, y, 360, 290, light=False)
    text(im, 'Налаштування', x + 180, y + 14, GOLD['light'], center=True)
    for n, s in enumerate(['Загальні', 'Відео', 'Звук']):
        button(im, x + 12 + n * 112, y + 38, 108, s, selected=n == 0, h=24)
    field(im, x + 18, y + 73, 318, 'Пошук налаштувань', focused=True)
    text(im, 'Список', x + 18, y + 106, MUTED)
    for n, s in enumerate(['Мова: українська', 'Керування', 'Спеціальні можливості']):
        yy = y + 119 + n * 22
        rect(im, x + 18, yy, 310, 20, '#28323B' if n == 0 else '#192129')
        if n == 0:
            outline(im, x + 18, yy, 310, 20, GOLD['light'])
        text(im, s, x + 25, yy + 6)
    scrollbar(im, x + 332, y + 119, 64, 22, 9)
    slider(im, x + 18, y + 194, 154, 75, 'Гучність: 75%')
    button(im, x + 180, y + 194, 156, 'Субтитри: увімкнено')
    checkbox(im, x + 18, y + 225, True)
    text(im, 'Зменшення руху', x + 42, y + 230)
    button(im, x + 80, y + 258, 200, 'Готово')


def kit(im, x, y):
    panel(im, x, y, 520, 320, light=False)
    text(im, 'КІТ 01 / ІНВЕНТАРІ Й МЕНЮ / ЕСКІЗ 04', x + 14, y + 14, GOLD['light'])
    for n, (s, color) in enumerate([('Світлий', GOLD['light']), ('Середній', GOLD['mid']), ('Темний', GOLD['dark']), ('Бліде золото', BODY)]):
        rect(im, x + 14 + n * 126, y + 39, 112, 13, color)
        text(im, s, x + 14 + n * 126, y + 60, MUTED)
    panel(im, x + 14, y + 82, 230, 72)
    text(im, 'Інвентар', x + 22, y + 91, LABEL)
    text(im, 'Темні підписи лишаються читабельними', x + 22, y + 108, LABEL)
    grid(im, x + 22, y + 127, 9, 1, GOLD_ITEMS)
    text(im, 'Один слот на всіх екранах', x + 264, y + 91, TEXT)
    grid(im, x + 264, y + 108, 9, 1, GOLD_ITEMS, selected=5)
    text(im, 'Вибір: золота рамка', x + 264, y + 139, MUTED)
    button(im, x + 14, y + 174, 200, 'Широка 200×20')
    button(im, x + 224, y + 174, 98, 'Вузька')
    button(im, x + 332, y + 174, 20, '+')
    button(im, x + 14, y + 202, 204, 'Пауза 204×20')
    field(im, x + 230, y + 202, 178, 'Їжак, ґанок, Єдність')
    scrollbar(im, x + 430, y + 174, 48, 20, 7)
    checkbox(im, x + 458, y + 174, True)
    slider(im, x + 14, y + 237, 200, 50, 'Повзунок: 50%')
    button(im, x + 230, y + 237, 178, 'Перемикач: увімкнено')
    text(im, 'і ї є ґ І Ї Є Ґ / ванільний шрифт', x + 14, y + 281)
    text(im, '1 px контур / три тони металу / nearest', x + 14, y + 299, MUTED)


def states(im, x, y):
    panel(im, x, y, 520, 304, light=False)
    text(im, 'УСІ СТАНИ КНОПКИ', x + 14, y + 14, GOLD['light'])
    for n, (key, label) in enumerate([('normal', 'Спокійна'), ('hover', 'Наведення'), ('pressed', 'Натиснута'), ('focus', 'Фокус клавіатури'), ('disabled', 'Недоступна')]):
        yy = y + 42 + n * 45
        text(im, label, x + 14, yy + 6)
        button(im, x + 182, yy, 200, 'Готово', key)
        button(im, x + 392, yy, 98, 'Обрати', key)
        button(im, x + 494, yy, 20, '+', key)
    text(im, 'Розміри незмінні / focus — кутові дужки', x + 14, y + 278, MUTED)


SCREENS = [
    ('kit', 'Оновлений дизайн-кіт', 520, 320),
    ('inventory', 'Інвентар гравця E', 176, 166),
    ('chest', 'Одинарна скриня', 176, 168),
    ('double-chest', 'Подвійна скриня', 176, 222),
    ('crafting', 'Верстак', 176, 166),
    ('furnace', 'Піч', 176, 166),
    ('tooltip', 'Підказка предмета', 390, 210),
    ('recipe-book', 'Відкрита книга рецептів', 350, 166),
    ('creative', 'Творчий: вкладки', 195, 136),
    ('creative-search', 'Творчий: пошук', 195, 136),
    ('pause', 'Меню паузи 26.2', 204, 116),
    ('settings', 'Налаштування: список і віджети', 360, 290),
    ('button-states', 'Усі стани кнопки', 520, 304),
]


def layer(name, gw, gh):
    im = canvas(gw, gh)
    _, _, w, h = next(s for s in SCREENS if s[0] == name)
    x, y = (gw - w) // 2, (gh - h) // 2
    if name == 'kit': kit(im, x, y)
    elif name == 'button-states': states(im, x, y)
    elif name == 'inventory': inventory(im, x, y)
    elif name == 'chest': chest(im, x, y)
    elif name == 'double-chest': chest(im, x, y, 6)
    elif name == 'crafting': crafting(im, x, y)
    elif name == 'furnace': furnace(im, x, y)
    elif name == 'tooltip':
        chest(im, x, y + 20)
        tooltip(im, x + 188, y + 48)
    elif name == 'recipe-book':
        recipe_book(im, x + 27, y)
        crafting(im, x + 177, y)
    elif name.startswith('creative'): creative(im, x, y, name.endswith('search'))
    elif name == 'settings': settings(im, x, y)
    elif name == 'pause':
        # Vanilla GridLayout height 166 includes the first row's 50 px top padding.
        x, y = (gw - 204) // 2, round((gh - 166) * .25) + 50
        button(im, x, y - 28, 204, 'Меню гри')
        button(im, x, y, 204, 'Повернутися до гри')
        button(im, x, y + 24, 98, 'Досягнення')
        button(im, x + 106, y + 24, 98, 'Статистика')
        for n, icon in enumerate(['bug', 'social_interactions', 'friends', 'player_reporting']):
            bx = gw // 2 - 46 + n * 24
            button(im, bx, y + 48, 20, '', 'disabled' if n == 3 else 'normal')
            if 'pause-' + icon in ASSETS:
                paste(im, ASSETS['pause-' + icon], bx + 2, y + 50)
            else:
                text(im, '+', bx + 10, y + 54, center=True)
        button(im, x, y + 72, 98, 'Налаштування')
        button(im, x + 106, y + 72, 98, 'Мережева гра')
        button(im, x, y + 96, 204, 'Зберегти та вийти')
    return im


def encode(im):
    data = io.BytesIO()
    im.save(data, format='PNG')
    return 'data:image/png;base64,' + base64.b64encode(data.getvalue()).decode()


def luminance(color):
    rgb = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in rgb]
    return sum(c * v for c, v in zip((.2126, .7152, .0722), linear))


def contrast(a, b):
    hi, lo = sorted([luminance(a), luminance(b)], reverse=True)
    return (hi + .05) / (lo + .05)


def main():
    (OUT / 'layers').mkdir(exist_ok=True)
    (OUT / 'screens').mkdir(exist_ok=True)
    (OUT / 'details').mkdir(exist_ok=True)
    with zipfile.ZipFile(JAR) as archive:
        names = GOLD_ITEMS + ['diamond', 'coal', 'iron_ingot', 'bread', 'ender_pearl', 'book', 'apple', 'gold_nugget',
                             'iron_helmet', 'iron_chestplate', 'iron_leggings', 'iron_boots']
        for name in names:
            ASSETS[name] = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/item/' + name + '.png'))).convert('RGBA')
        ASSETS['steve'] = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/entity/player/wide/steve.png'))).convert('RGBA')
        for name in ['speed', 'resistance']:
            ASSETS['effect-' + name] = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/mob_effect/' + name + '.png'))).convert('RGBA')
        for name in ['bug', 'social_interactions', 'player_reporting']:
            ASSETS['pause-' + name] = gold_icon(Image.open(io.BytesIO(archive.read('assets/minecraft/textures/gui/sprites/pause_menu/' + name + '.png'))).convert('RGBA'))
        ASSETS['pause-friends'] = gold_icon(Image.open(io.BytesIO(archive.read('assets/minecraft/textures/gui/sprites/friends/friends.png'))).convert('RGBA'))
    backgrounds = {}
    for name, source in [('day', 'sky'), ('cave', 'cave')]:
        raw = base64.b64decode(PAYLOAD['images'][source].split(',')[1])
        backgrounds[name] = Image.open(io.BytesIO(raw)).convert('RGBA')
        assert backgrounds[name].size == (1920, 1080)
        backgrounds[name].save(OUT / ('background-' + name + '.png'))
    manifest = []
    for name, label, w, h in SCREENS:
        for scale in range(1, 5):
            # 1920x1080 has a real maximum GUI scale of 4 (480x270).
            # Large review boards at scale 4 are never labelled an in-game fit.
            gw, gh = 1920 // scale, 1080 // scale
            if name in ('kit', 'button-states', 'settings'):
                if gw < w + 12 or gh < h + 12:
                    continue
            native = layer(name, gw, gh)
            native.save(OUT / 'layers' / f'{name}-gs{scale}.png')
            manifest.append(dict(screen=name, scale=scale, width=native.width, height=native.height))
            if scale not in (2, 3):
                continue
            for bgname, bg in backgrounds.items():
                composed = bg.copy()
                paste(composed, Image.new('RGBA', bg.size, (0, 0, 0, 100 if name == 'pause' else 83)), 0, 0)
                paste(composed, native.resize(bg.size, NEAREST), 0, 0)
                composed.convert('RGB').save(OUT / 'screens' / f'{name}-{bgname}-gs{scale}.png')
                if scale == 3:
                    bbox = native.getbbox()
                    assert bbox
                    x0, y0, x1, y1 = bbox
                    box = (max(0, x0 - 5), max(0, y0 - 5), min(gw, x1 + 5), min(gh, y1 + 5))
                    detail = bg.resize((gw, gh), NEAREST).crop(box)
                    paste(detail, Image.new('RGBA', detail.size, (0, 0, 0, 83)), 0, 0)
                    paste(detail, native.crop(box), 0, 0)
                    detail.resize((detail.width * 4, detail.height * 4), NEAREST).convert('RGB').save(
                        OUT / 'details' / f'{name}-{bgname}-x4.png')
    # Shared nine-slice must preserve every corner at all requested button widths.
    for w in (20, 98, 200, 204):
        sample = nine(PANEL_DARK, w, 20)
        for x, y in [(0, 0), (w - 4, 0), (0, 16), (w - 4, 16)]:
            sx, sy = (0 if x == 0 else 20), (0 if y == 0 else 20)
            assert sample.crop((x, y, x + 4, y + 4)).tobytes() == PANEL_DARK.crop((sx, sy, sx + 4, sy + 4)).tobytes()
    selected_slot = canvas(18, 18)
    slot(selected_slot, 0, 0, selected=True)
    gold = (223, 194, 139, 255)
    assert all(selected_slot.getpixel((n, edge)) == selected_slot.getpixel((edge, n)) == gold
               for n in range(18) for edge in (0, 17))
    tooltip_sample = canvas(190, 67)
    tooltip(tooltip_sample, 0, 0)
    assert all(tooltip_sample.getpixel((x, y)) == tooltip_sample.getpixel((7, 19))
               for x in range(7, 183) for y in range(18, 23)), 'Tooltip title gap must have no underline'
    for size in (18, 24):
        sample = canvas(size, size)
        slot(sample, 0, 0, size=size)
        assert sample.getpixel((size // 2, size // 2)) == (186, 152, 91, 255)
        assert sample.getpixel((0, size // 2)) == sample.getpixel((size // 2, 0)) == (112, 80, 45, 255)
        assert sample.getpixel((size - 1, size // 2)) == sample.getpixel((size // 2, size - 1)) == (112, 80, 45, 255)
    inventory_sample = canvas(294, 166)
    inventory(inventory_sample, 0, 0)
    assert inventory_sample.getpixel((75, 61)) == inventory_sample.getpixel((75, 78)) == (101, 83, 58, 255)
    assert inventory_sample.getpixel((76, 61)) == inventory_sample.getpixel((76, 78)) == (112, 80, 45, 255)
    for arrow_width in (18, 22):
        sample = canvas(arrow_width, 15)
        arrow(sample, 0, 0, w=arrow_width)
        alpha = sample.getchannel('A')
        assert alpha.tobytes() == alpha.transpose(Image.Transpose.FLIP_TOP_BOTTOM).tobytes(), 'Arrow silhouette must be symmetric'
        assert alpha.getbbox() == (1, 1, arrow_width - 1, 14), 'Arrow must retain one-pixel padding'
    for highlighted in (False, True):
        assert page_arrow(True, highlighted).tobytes() == page_arrow(False, highlighted).transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes()
    assert not MISSING, f'Missing glyphs: {MISSING}'
    assert not OVERFLOW, f'Text overflow: {sorted(set(OVERFLOW))}'
    assert contrast(BODY, LABEL) >= 4.5 and contrast(TEXT, DARK) >= 4.5
    before = json.loads((OUT / 'protected-before.json').read_text(encoding='utf-8'))
    changed = [p for p, old in before.items() if not (ROOT / p).exists() or hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != old]
    assert not changed, f'Protected tracked files changed: {changed}'
    verification = dict(status='design-only; awaiting approval', screens=len(SCREENS), pngScreens=len(list((OUT / 'screens').glob('*.png'))),
        detailsX4=len(list((OUT / 'details').glob('*.png'))), guiScales=[2, 3], screenshotSize=[1920, 1080],
        sourceJarSha256=hashlib.sha256(JAR.read_bytes()).hexdigest(), protectedTrackedFiles=len(before), changedProtectedFiles=changed,
        missingGlyphs=sorted(MISSING), overflow=OVERFLOW, unicodeProof='і ї є ґ І Ї Є Ґ',
        panelLabelContrast=round(contrast(BODY, LABEL), 2), buttonTextContrast=round(contrast(TEXT, DARK), 2),
        nineSliceCornersCheckedWidths=[20, 98, 200, 204], itemSize=[16, 16], slotPitch=18,
        arrowNativeWidths=[18, 22], arrowResized=False, pageArrowMirrorsChecked=True,
        slotInteriors=dict(allSlots=SLOT_FILL, shadow=SLOT_SHADOW, edge=SLOT_EDGE, outline=SLOT_OUTLINE),
        inventoryGeometry=dict(modelRegion=[25, 7, 51, 72], offhandFrame=[76, 61, 18, 18],
            offhandItem=[77, 62], modelAndOffhandBottom=78, overlap=False),
        userReference=dict(path='art/my view.png', sha256=hashlib.sha256((ROOT / 'art/my view.png').read_bytes()).hexdigest()),
        mockupsAreGameCaptures=False, gameCaptures="in-game/captures.json", model='front skin projection; not live 3D',
        sources=dict(day='hud-concepts-v1 sky daytime F1 capture, 1920x1080 unchanged', cave='hud-concepts-v1 cave F1 capture, 1920x1080 unchanged', font='bottom-panel-v4 glyph data from Minecraft JAR bitmap font'),
        selection='kit gold frames and corner brackets only; no decorative underlines or pathway stripes',
        notVerified=['in-game input/focus/narration', 'GPU resource reload', 'recipe addon integration', 'custom tooltip components'])
    (OUT / 'verification.json').write_text(json.dumps(verification, ensure_ascii=False, indent=2), encoding='utf-8')
    (OUT / 'layers.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    data = dict(screens=[dict(id=n, label=l) for n, l, _, _ in SCREENS], layers=manifest)
    html = (OUT / 'review-template.html').read_text(encoding='utf-8').replace('__REVIEW_DATA__', json.dumps(data, ensure_ascii=False))
    (OUT / 'review.html').write_text(html, encoding='utf-8')
    print(json.dumps(verification, ensure_ascii=True))


if __name__ == '__main__':
    main()
