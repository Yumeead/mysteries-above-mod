"""Prepare the wiki's existing official symbols for Minecraft's nearest-pixel GUI atlas.

Requires Pillow. Source silhouettes are cropped and rasterized, never redrawn.
"""
from collections import deque
from hashlib import md5
from pathlib import Path
import shutil

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(__file__).parent / "official"
TARGET = ROOT / "mod/src/client/resources/assets/mysteries_above/textures/gui/sprites/grimoire/emblem/official"
NAMES = "Abyss,Black Emperor,Chained,Darkness,Death,Demoness,Door,Error,Fool,Hanged Man,Hermit,Justiciar,Moon,Mother,Paragon,Red Priest,Sun,Twilight Giant,Tyrant,Visionary,Wheel of Fortune,White Tower".split(",")


def symbol_bounds(alpha):
    """Keep substantial central parts, including disconnected pieces of the same emblem."""
    width, height = alpha.size
    pixels = bytearray(alpha.point(lambda a: 1 if a >= 128 else 0).tobytes())
    parts = []
    for start in range(len(pixels)):
        if not pixels[start]:
            continue
        queue = deque([start])
        pixels[start] = 0
        count = 0
        left = right = start % width
        top = bottom = start // width
        while queue:
            index = queue.popleft()
            x, y = index % width, index // width
            left, right = min(left, x), max(right, x)
            top, bottom = min(top, y), max(bottom, y)
            count += 1
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < width and 0 <= ny < height:
                    neighbour = ny * width + nx
                    if pixels[neighbour]:
                        pixels[neighbour] = 0
                        queue.append(neighbour)
        if left >= width * .15 and top >= height * .15 and right < width * .85 and bottom < height * .85:
            parts.append((count, (left, top, right + 1, bottom + 1)))
    assert parts
    minimum = max(count for count, _ in parts) / 20
    boxes = [box for count, box in parts if count >= minimum]
    return min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes)


if __name__ == "__main__":
    SOURCES.mkdir(parents=True, exist_ok=True)
    TARGET.mkdir(parents=True, exist_ok=True)
    manifest = []
    for name in NAMES:
        filename = name.replace(" ", "_") + "_Symbol3.png"
        source = SOURCES / filename
        if not source.exists():
            shutil.copyfile(ROOT / "mod/run/ui-art" / filename, source)
        digest = md5(filename.encode()).hexdigest()
        url = f"https://static.wikia.nocookie.net/lord-of-the-mystery/images/{digest[0]}/{digest[:2]}/{filename}"
        original = Image.open(source).convert("RGBA")
        alpha = original.getchannel("A")
        bounds = symbol_bounds(alpha)
        cropped = alpha.crop(bounds)
        slug = name.replace(" ", "").lower()
        for size in (24, 48, 64):
            ratio = (size - 4) / max(cropped.size)
            dimensions = tuple(max(1, round(side * ratio)) for side in cropped.size)
            mask = cropped.resize(dimensions, Image.Resampling.BOX).point(lambda a: 255 if a >= 96 else 0)
            mask = mask.crop(mask.getbbox())
            icon = Image.new("RGBA", (size, size))
            ink = Image.new("RGBA", mask.size, "white")
            ink.putalpha(mask)
            icon.paste(ink, ((size - mask.width) // 2, (size - mask.height) // 2))
            assert icon.getchannel("A").getextrema() == (0, 255)
            box = icon.getbbox()
            assert abs(box[0] + box[2] - size) <= 1
            assert abs(box[1] + box[3] - size) <= 1
            icon.save(TARGET / f"{slug}_{size}.png", optimize=True)
        manifest.append(f"| {name} | [{filename}](https://lordofthemysteries.fandom.com/wiki/File:{filename}) | [original PNG]({url}) | `{bounds}` |")
        print(name, bounds)
    (SOURCES.parent / "SOURCES.md").write_text(
        "# Official LOTM pathway emblems\n\n"
        "Source: [Official Pathway Symbols](https://lordofthemysteries.fandom.com/wiki/Category:Official_Pathway_Symbols), "
        "the wiki's Colorless Symbols series. The original artwork belongs to its respective rights holders; it was not created by this mod.\n\n"
        "Unmodified downloaded sources are in `official/`. `prepare_symbols.py` crops the central silhouette, "
        "preserves its internal details, converts the ink to a tintable white mask and produces centered 24, 48 and 64 pixel sprites. "
        "The resulting alpha is binary; the game does not resample these sizes. No AI redraws are used.\n\n"
        "Run `python art/grimoire/prepare_symbols.py` with Pillow installed to reproduce the sprites.\n\n"
        "| Pathway | Source page | Download | Crop in original pixels |\n| --- | --- | --- | --- |\n"
        + "\n".join(manifest) + "\n", encoding="utf-8")
