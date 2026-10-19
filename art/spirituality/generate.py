"""Extract approved v4 artwork; repair only three damaged dark pendants."""
from pathlib import Path
import argparse
import colorsys
import json
import re

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mod/src/client/resources/assets/mysteries_above"
ART = Path(__file__).resolve().parent
REVIEW = ART / "mockups/v4"
SIZE = (64, 56)
# Flow and particle stamps are visual data, never pathway branches in the renderer.
FLOWS = {
    "Fool": "SPIRAL", "Error": "STEP", "Door": "RISE", "Visionary": "DRIFT",
    "Justiciar": "RISE", "BlackEmperor": "ORBIT", "WhiteTower": "STEP", "Paragon": "STEP",
    "Sun": "RISE", "Tyrant": "DRIFT", "TwilightGiant": "FALL", "RedPriest": "RISE",
    "Mother": "DRIFT", "Moon": "ORBIT", "Darkness": "DRIFT", "Death": "FALL",
    "Demoness": "SPIRAL", "HangedMan": "FALL", "Abyss": "STEP", "Hermit": "ORBIT",
    "Chained": "DRIFT", "WheelOfFortune": "ORBIT",
}
STAMPS = {
    "SPIRAL": [[0, 0], [1, 0]], "STEP": [[0, 0], [1, 1]], "RISE": [[0, -1], [0, 0]],
    "DRIFT": [[0, 0]], "ORBIT": [[0, 0], [1, 0]], "FALL": [[0, 0], [0, 1]],
}


def save(image, slug, layer):
    directory = ASSETS / "textures/gui/sprites/spirituality" / slug
    directory.mkdir(parents=True, exist_ok=True)
    image.save(directory / f"{layer}.png")


def ellipse(box):
    image = Image.new("L", SIZE)
    ImageDraw.Draw(image).ellipse(box, fill=255)
    return image


def common_layers(base, mask):
    frame, glass, backing = (Image.new("RGBA", SIZE) for _ in range(3))
    outer = ellipse((10, 16, 51, 51))
    light = ellipse((11, 17, 50, 50))
    rings = [ellipse(box) for box in ((7, 13, 54, 55), (8, 14, 53, 54), (9, 15, 52, 53))]
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            if y < 13 and 25 <= x < 39:
                frame.putpixel((x, y), base.getpixel((x, y)))
            if outer.getpixel((x, y)) and not mask.getpixel((x, y)):
                color = (53, 71, 86, 255)
                if light.getpixel((x, y)):
                    color = (204, 239, 250, 255) if x + y < 76 else (151, 205, 228, 255)
                frame.putpixel((x, y), color)
            if mask.getpixel((x, y)):
                glass.putpixel((x, y), (167, 197, 221, 9))
            # Dither only the outside transition; the three inner alpha levels stay coherent.
            if rings[0].getpixel((x, y)):
                alpha = 153 if rings[2].getpixel((x, y)) else 128 if rings[1].getpixel((x, y)) else 102
                if rings[1].getpixel((x, y)) or (x + y) % 2 == 0:
                    backing.putpixel((x, y), (36, 47, 65, alpha))
    draw = ImageDraw.Draw(glass)
    draw.line([(16, 25), (18, 22), (21, 20), (24, 19)], fill=(238, 253, 255, 255), width=2)
    draw.line([(15, 27), (15, 29)], fill=(214, 246, 255, 235))
    draw.line([(47, 35), (46, 38)], fill=(224, 248, 255, 245), width=2)
    for y in (26, 34, 42):
        draw.line((44, y, 45, y), fill=(215, 237, 246, 195))
    # Highlights must stay inside the same glass cavity as the liquid.
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            if not mask.getpixel((x, y)):
                glass.putpixel((x, y), (0, 0, 0, 0))
    return frame, glass, backing


def charm(empty, pathway):
    image = Image.new("RGBA", SIZE)
    for y in range(13, 32):
        for x in range(40, 61):
            r, g, b, a = empty.getpixel((x, y))
            if a and not (b > r * 1.08 and b > g * 1.02):
                image.putpixel((x, y), (r, g, b, a))
    # Keep the original extraction byte-for-byte for the other nineteen pendants.
    if pathway not in TAG_REPAIRS:
        return image
    polygon, repairs = TAG_REPAIRS[pathway]
    selection = Image.new("L", SIZE)
    ImageDraw.Draw(selection).polygon(polygon, fill=255)
    for y in range(18, 32):
        for x in range(40, 61):
            image.putpixel((x, y), empty.getpixel((x, y)) if selection.getpixel((x, y)) else (0, 0, 0, 0))
    # Only fill named one-pixel gaps using an adjacent original material colour.
    for x, y, source_x, source_y in repairs:
        if not image.getpixel((x, y))[3]:
            color = empty.getpixel((source_x, source_y))
            assert color[3] and selection.getpixel((x, y)), (pathway, x, y)
            image.putpixel((x, y), color)
    return image


TAG_REPAIRS = {
    "Darkness": (
        [(43, 17), (46, 18), (48, 21), (52, 25), (52, 27), (48, 26), (44, 23), (43, 20)],
        [(46, 19, 46, 18), (45, 20, 44, 21), (46, 20, 46, 21), (47, 22, 46, 21),
         (47, 23, 46, 23), (48, 23, 48, 24), (50, 25, 50, 24)],
    ),
    "Demoness": (
        [(40, 14), (44, 17), (47, 22), (47, 25), (45, 28), (42, 25), (41, 22), (41, 19), (40, 17)],
        [(43, 18, 42, 17), (43, 19, 42, 21), (43, 20, 42, 21),
         (44, 24, 43, 24), (44, 25, 43, 24), (44, 26, 43, 26)],
    ),
    "Abyss": (
        [(42, 15), (46, 17), (49, 21), (49, 24), (47, 27), (45, 26), (44, 23), (41, 18)],
        [(45, 19, 44, 19), (45, 20, 44, 21), (45, 21, 44, 21),
         (45, 22, 44, 22), (47, 24, 47, 23), (47, 25, 47, 23)],
    ),
}


def palette(image, mask, brand):
    hue = colorsys.rgb_to_hsv(*(int(brand[i:i + 2], 16) / 255 for i in (1, 3, 5)))[0]
    samples = []
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            r, g, b, a = image.getpixel((x, y))
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            distance = min(abs(h - hue), 1 - abs(h - hue))
            if mask.getpixel((x, y)) and a > 100 and s > .22 and v > .15 and distance < .17:
                samples.append((r, g, b))
    assert len(samples) >= 6, brand
    strip = Image.new("RGB", (len(samples), 1))
    strip.putdata(samples)
    colors = sorted((rgb for _, rgb in strip.quantize(colors=6).convert("RGB").getcolors()),
                    key=lambda c: c[0] * .2126 + c[1] * .7152 + c[2] * .0722)
    assert len(colors) == 6
    return ["#" + "".join(f"{c:02X}" for c in rgb) for rgb in colors]


def export_liquids():
    designs = json.loads((REVIEW / "designs.json").read_text(encoding="utf-8"))["pathways"]
    atlas = Image.open(REVIEW / "sources/native-atlas-110-states.png").convert("RGBA")
    board = Image.open(REVIEW / "01-all-22-pathways-x4.png").convert("RGB")
    background = board.getpixel((0, 0)) + (255,)
    mask = Image.open(ASSETS / "textures/gui/sprites/spirituality/common/mask.png").getchannel("A")
    compared = 0
    for index, design in enumerate(designs):
        slug = design["id"].lower()
        brand = design["hex"]
        hue = colorsys.rgb_to_hsv(*(int(brand[i:i + 2], 16) / 255 for i in (1, 3, 5)))[0]
        paintings = []
        for state, level, name in [(4, .005, "minimum"), (1, .1, "low"), (2, .33, "third"), (3, 1., "full")]:
            original = atlas.crop((state * 64, index * 64, state * 64 + 64, index * 64 + 56))
            flat = Image.new("RGBA", SIZE, background)
            flat.alpha_composite(original)
            # Verify the source is literally the sprite used by the user's reference board.
            if state in (2, 3):
                ox = 60 + index % 6 * 584 + (280 if state == 3 else 0)
                oy = 360 + index // 6 * 432
                for y in range(SIZE[1]):
                    for x in range(SIZE[0]):
                        assert flat.getpixel((x, y))[:3] == board.getpixel((ox + x * 4, oy + y * 4)), (slug, name, x, y)
                        compared += 1
            surface = 18 if state == 3 else next((y for y in range(23, 50)
                if sum(original.getpixel((x, y))[3] >= 200 for x in range(26, 39)) >= 7), 49)
            liquid = Image.new("RGBA", SIZE)
            for y in range(surface, 50):
                span = [x for x in range(64) if mask.getpixel((x, y)) and original.getpixel((x, y))[3]]
                if not span:
                    continue
                for x in range(span[0], span[-1] + 1):
                    if not mask.getpixel((x, y)) or (y <= 21 and 24 <= x <= 39):
                        continue
                    if y <= surface + 1 and not original.getpixel((x, y))[3]:
                        continue
                    liquid.putpixel((x, y), flat.getpixel((x, y)))
            # Preserve the original sparks and seeds just above the painted meniscus as well.
            for y in range(max(23, surface - 4), surface):
                for x in range(20, 44):
                    r, g, b, a = original.getpixel((x, y))
                    h, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                    distance = min(abs(h - hue), 1 - abs(h - hue))
                    if mask.getpixel((x, y)) and a and ((saturation > .2 and value > .15 and distance < .2) or min(r, g, b) > 205):
                        liquid.putpixel((x, y), flat.getpixel((x, y)))
            assert liquid.getbbox(), (slug, name)
            save(liquid, slug, f"liquid_{name}")
            paintings.append({"level": level, "surface": liquid.getbbox()[1],
                "texture": f"mysteries_above:textures/gui/sprites/spirituality/{slug}/liquid_{name}.png"})
        path = ASSETS / f"hud/spirituality/{slug}.json"
        descriptor = json.loads(path.read_text(encoding="utf-8"))
        descriptor["liquids"] = paintings
        path.write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")
    print(f"Copied original full-colour liquid artwork for 22 pathways; {compared} reference pixels verified exactly.")


def main():
    designs = json.loads((REVIEW / "designs.json").read_text(encoding="utf-8"))["pathways"]
    atlas_path = REVIEW / "sources/native-atlas-110-states.png"
    atlas = Image.open(atlas_path).convert("RGBA")
    branding = (ROOT / "core/src/main/kotlin/me/vangoo/domain/PathwayBranding.kt").read_text(encoding="utf-8")
    assert {d["id"] for d in designs} == set(re.findall(r'put\("([^"]+)", \d', branding))
    mask = Image.new("L", SIZE)
    ImageDraw.Draw(mask).ellipse((13, 18, 48, 49), fill=255)
    base = atlas.crop((0, 0, *SIZE))
    frame, glass, backing = common_layers(base, mask)
    save(frame, "common", "frame")
    save(glass, "common", "glass")
    save(backing, "common", "backing")
    rgba_mask = Image.new("RGBA", SIZE, (255, 255, 255, 0))
    rgba_mask.putalpha(mask)
    save(rgba_mask, "common", "mask")
    descriptors = ASSETS / "hud/spirituality"
    descriptors.mkdir(parents=True, exist_ok=True)
    for index, design in enumerate(designs):
        slug = design["id"].lower()
        empty = atlas.crop((0, index * 64, 64, index * 64 + SIZE[1]))
        full = atlas.crop((192, index * 64, 256, index * 64 + SIZE[1]))
        neck, engraving = (Image.new("RGBA", SIZE) for _ in range(2))
        tag = charm(empty, design["id"])
        for y in range(13, 35):
            for x in range(10, 61):
                color = empty.getpixel((x, y))
                if not color[3]:
                    continue
                if x >= 40 and y <= 31:
                    continue
                elif 19 <= x < 44 and y <= 21:
                    neck.putpixel((x, y), color)
                elif x <= 31 and y >= 23 and mask.getpixel((x, y)) and max(color[:3]) > 70:
                    engraving.putpixel((x, y), color)
        for layer, image in [("neck", neck), ("tag", tag), ("engraving", engraving)]:
            save(image, slug, layer)
        flow = FLOWS[design["id"]]
        colors = palette(full, mask, design["hex"])
        rgb_colors = [tuple(int(color[i:i + 2], 16) for i in (1, 3, 5)) for color in colors]
        tones = Image.new("RGBA", SIZE)
        for y in range(SIZE[1]):
            for x in range(SIZE[0]):
                if mask.getpixel((x, y)):
                    pixel = full.getpixel((x, y))[:3]
                    index = min(range(6), key=lambda i: sum((pixel[c] - rgb_colors[i][c]) ** 2 for c in range(3)))
                    tones.putpixel((x, y), (index, index, index, 255))
        save(tones, slug, "tones")
        descriptor = {
            "pathway": design["id"],
            "frame": "mysteries_above:spirituality/common/frame",
            "glass": "mysteries_above:spirituality/common/glass",
            "backing": "mysteries_above:spirituality/common/backing",
            "mask": "mysteries_above:textures/gui/sprites/spirituality/common/mask.png",
            "tones": f"mysteries_above:textures/gui/sprites/spirituality/{slug}/tones.png",
            **{layer: f"mysteries_above:spirituality/{slug}/{layer}" for layer in ("neck", "tag", "engraving")},
            "palette": colors,
            "particle": STAMPS[flow], "particleCount": 3,
            "flow": flow, "periodTicks": design["period"] * 20,
            "tagSwing": .025 if flow in ("RISE", "STEP") else .04,
        }
        (descriptors / f"{slug}.json").write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")
    fallback = json.loads((descriptors / "fool.json").read_text(encoding="utf-8"))
    fallback.update(pathway="default", palette=["#263844", "#3C5260", "#597584", "#7E9AA5", "#ABC2C9", "#DBE7E9"])
    (descriptors / "default.json").write_text(json.dumps(fallback, indent=2) + "\n", encoding="utf-8")
    assert frame.getbbox() and glass.getbbox() and mask.getbbox()
    for image in (frame, glass, rgba_mask):
        assert not image.getchannel("A").crop((0, 52, 64, 56)).getbbox()
    print("Restored v4 pendants; 19 unchanged, three dark pendants receive local pixel repairs.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--liquids-only", action="store_true", help="Preserve manually edited bottle and charm layers")
    if not parser.parse_args().liquids_only:
        main()
    export_liquids()
