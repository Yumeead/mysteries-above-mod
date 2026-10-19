"""Затверджений піксельний кіт: чисті примітиви без Minecraft, шрифтів або макетів."""
from PIL import Image, ImageDraw, ImageColor

GOLD = {'light': '#DFC28B', 'mid': '#B99A61', 'dark': '#65533A'}


INK, BODY, LIGHT, DARK, TEXT = '#2C2014', '#DCC58D', '#E3CF9C', '#191611', '#F0E9DA'


MUTED, LABEL = '#B7B1A5', '#404040'


SLOT_FILL, SLOT_SHADOW, SLOT_EDGE, SLOT_OUTLINE = '#BA985B', '#896337', '#D1B071', '#70502D'


PAGE_COUNTER_FRAME = (54, 137, 38, 17)


NEAREST = Image.Resampling.NEAREST


def canvas(w, h):
    return Image.new('RGBA', (w, h))


def rect(im, x, y, w, h, color):
    if w > 0 and h > 0:
        ImageDraw.Draw(im).rectangle((x, y, x + w - 1, y + h - 1), fill=color)


def outline(im, x, y, w, h, color):
    ImageDraw.Draw(im).rectangle((x, y, x + w - 1, y + h - 1), outline=color)


def paste(im, source, x, y):
    im.alpha_composite(source, (x, y))


def nine(source, w, h, border=4):
    assert w >= border * 2 and h >= border * 2
    target = canvas(w, h)
    sw, sh = source.size
    xs, ys = (0, border, sw - border, sw), (0, border, sh - border, sh)
    tx, ty = (0, border, w - border, w), (0, border, h - border, h)
    for row in range(3):
        for col in range(3):
            part = source.crop((xs[col], ys[row], xs[col + 1], ys[row + 1]))
            part = part.resize((tx[col + 1] - tx[col], ty[row + 1] - ty[row]), NEAREST)
            paste(target, part, tx[col], ty[row])
    return target


def seed(light=False):
    im = Image.new('RGBA', (24, 24), BODY if light else DARK)
    metal_frame(im, 0, 0, 24, 24)
    return im


def metal_frame(im, x, y, w, h, highlighted=False, disabled=False):
    outline(im, x, y, w, h, GOLD['dark'])
    outline(im, x + 1, y + 1, w - 2, h - 2, GOLD['dark'] if disabled else GOLD['mid'])
    if not disabled:
        rect(im, x + 1, y + 1, w - 2, 1, GOLD['light'])
        rect(im, x + 1, y + 1, 1, h - 2, GOLD['light'])
    if highlighted:
        outline(im, x + 1, y + 1, w - 2, h - 2, GOLD['light'])


PANEL_LIGHT, PANEL_DARK = seed(True), seed(False)


def panel(im, x, y, w, h, light=True):
    p = nine(PANEL_LIGHT if light else PANEL_DARK, w, h)
    if light:
        for yy in range(2, h - 2):
            for xx in range(2, w - 2):
                tone = ((xx * 3 + yy * 5) % 17 == 0) - ((xx * 5 + yy * 3) % 19 == 0)
                rect(p, xx, yy, 1, 1, (*[c + tone for c in ImageColor.getrgb(BODY)], 255))
    paste(im, p, x, y)


def button(im, x, y, w, state='normal', h=20, selected=False):
    assert w >= 12 and h >= 12
    small = canvas(20, 20)
    rect(small, 0, 0, 20, 20, DARK)
    metal_frame(small, 0, 0, 20, 20, state in ('hover', 'focus'), state == 'disabled')
    if state == 'pressed':
        rect(small, 1, 1, 18, 1, GOLD['dark'])
        rect(small, 2, 2, 16, 1, '#080D12')
        rect(small, 2, 17, 16, 1, GOLD['mid'])
    paste(im, nine(small, w, h, 3), x, y)
    if state == 'hover':
        rect(im, x + 4, y + 3, w - 8, 1, '#585648')
    if selected and state != 'disabled':
        outline(im, x + 1, y + 1, w - 2, h - 2, GOLD['light'])
    if state == 'focus' or selected and state != 'disabled':
        for sx, sy, dx, dy in [(x + 3, y + 3, 1, 1), (x + w - 4, y + 3, -1, 1),
                               (x + 3, y + h - 4, 1, -1), (x + w - 4, y + h - 4, -1, -1)]:
            for n in range(3):
                rect(im, sx + n * dx, sy, 1, 1, GOLD['light'])
                rect(im, sx, sy + n * dy, 1, 1, GOLD['light'])


def slot(im, x, y, selected=False, size=18):
    rect(im, x, y, size, size, SLOT_FILL)
    outline(im, x, y, size, size, SLOT_OUTLINE)
    rect(im, x + 1, y + 1, size - 2, 1, SLOT_SHADOW)
    rect(im, x + 1, y + 1, 1, size - 2, SLOT_SHADOW)
    rect(im, x + 1, y + size - 2, size - 2, 1, SLOT_EDGE)
    rect(im, x + size - 2, y + 1, 1, size - 2, SLOT_EDGE)
    if selected:
        outline(im, x, y, size, size, GOLD['light'])


def arrow(im, x, y, w=22, h=15, highlighted=False):
    # Rasterize each native size directly; squeezing a larger arrow breaks its pixel steps.
    center, base, tip = h // 2, w - 9, w - 2
    points = {(xx, yy) for yy in range(h) for xx in range(1, w - 1)
              if (xx <= base and abs(yy - center) <= 2) or
              (xx >= base and abs(yy - center) <= ((center - 1) * (tip - xx) + (tip - base) // 2) // (tip - base))}
    for xx, yy in points:
        edge = any((xx + dx, yy + dy) not in points for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))
        color = GOLD['dark'] if edge else GOLD['light' if highlighted or (xx, yy - 2) not in points else 'mid']
        rect(im, x + xx, y + yy, 1, 1, color)


def page_arrow(backward=False, highlighted=False):
    im = canvas(12, 17)
    arrow(im, 0, 0, 12, 17, highlighted)
    return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if backward else im


def page_counter(im, x, y):
    # Native text is centered at x=73, y=141; the next-page button starts at x=93.
    dx, dy, w, h = PAGE_COUNTER_FRAME
    button(im, x + dx, y + dy, w, h=h)


def magnifier(im, x, y):
    # A round lens and a straight 45-degree handle, without a shifted drop shadow.
    draw = ImageDraw.Draw(im)
    draw.ellipse((x + 1, y + 1, x + 9, y + 9), fill=DARK, outline=GOLD['dark'], width=1)
    draw.ellipse((x + 2, y + 2, x + 8, y + 8), outline=GOLD['light'], width=1)
    draw.line((x + 8, y + 8, x + 11, y + 11), fill=GOLD['dark'], width=3)
    draw.line((x + 8, y + 8, x + 11, y + 11), fill=GOLD['mid'], width=1)


def slider_sprite(w, h, highlighted=False, handle=False):
    im = Image.new('RGBA', (w, h), GOLD['mid'] if handle else DARK)
    metal_frame(im, 0, 0, w, h, highlighted)
    return im


def scrollbar(im, x, y, height, thumb=24, offset=0, w=6):
    rect(im, x, y, w, height, DARK)
    outline(im, x, y, w, height, GOLD['dark'])
    rect(im, x + 1, y + offset + 1, w - 2, thumb - 2, GOLD['mid'])
    rect(im, x + 1, y + offset + 1, w - 2, 1, GOLD['light'])


def gold_icon(source):
    target = canvas(*source.size)
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.getpixel((x, y))
            if a:
                color = INK if max(r, g, b) < 55 else GOLD['dark'] if max(r, g, b) < 115 else GOLD['mid'] if max(r, g, b) < 205 else GOLD['light']
                target.putpixel((x, y), (*ImageColor.getrgb(color), a))
    return target


