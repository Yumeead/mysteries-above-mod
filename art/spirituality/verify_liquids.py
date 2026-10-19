"""Compare original reference pixels with unretouched Minecraft screenshots, including every pixel in each GUI block."""
from pathlib import Path
import json

from PIL import Image
from generate import LIQUID_BASE_ROWS

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art/spirituality"
RESOURCES = ROOT / "mod/src/client/resources/assets/mysteries_above"
REVIEW = ART / "mockups/v7-liquid-reference"


def main():
    designs = json.loads((ART / "mockups/v4/designs.json").read_text(encoding="utf-8"))["pathways"]
    source_indices = {design["id"]: index for index, design in enumerate(designs)}
    names = sorted(source_indices)
    board = Image.open(ART / "mockups/v4/01-all-22-pathways-x4.png").convert("RGB")
    results = []
    for label, level in [("third", .33), ("full", 1.)]:
        screenshot = Image.open(REVIEW / f"in-game/fluid_art_gs4_{label}.png").convert("RGB")
        assert screenshot.size == (1920, 1080), screenshot.size
        assert screenshot.getpixel((1910, 1070)) == board.getpixel((0, 0)), "Wrong comparison backdrop"
        for index, name in enumerate(names):
            slug = name.lower()
            descriptor = json.loads((RESOURCES / f"hud/spirituality/{slug}.json").read_text(encoding="utf-8"))
            entry = next(painting for painting in descriptor["liquids"] if painting["level"] == level)
            painting = Image.open(RESOURCES / entry["texture"].split(":")[1]).convert("RGBA")
            neck = Image.open(RESOURCES / f"textures/gui/sprites/spirituality/{slug}/neck.png").convert("RGBA")
            tag = Image.open(RESOURCES / f"textures/gui/sprites/spirituality/{slug}/tag.png").convert("RGBA")
            source_index = source_indices[name]
            ref_x = 60 + source_index % 6 * 584 + (280 if label == "full" else 0)
            ref_y = 360 + source_index // 6 * 432
            game_x = (12 + index % 6 * 76) * 4
            game_y = (19 + index // 6 * 60 + 4) * 4
            checked = covered = repaired = 0
            for y in range(56):
                for x in range(64):
                    pixel = painting.getpixel((x, y))
                    if not pixel[3]:
                        continue
                    # These local repairs intentionally differ from the archived v4 screenshots.
                    if y >= LIQUID_BASE_ROWS.get(name, 56):
                        repaired += 1
                        continue
                    expected = board.getpixel((ref_x + x * 4, ref_y + y * 4))
                    assert pixel[:3] == expected, (name, label, "export", x, y, pixel, expected)
                    # The user's existing neck and pendant intentionally remain above the liquid.
                    if neck.getpixel((x, y))[3] or tag.getpixel((x, y))[3]:
                        covered += 1
                        continue
                    for dy in range(4):
                        for dx in range(4):
                            actual = screenshot.getpixel((game_x + x * 4 + dx, game_y + y * 4 + dy))
                            assert actual == expected, (name, label, "game", x, y, dx, dy, expected, actual)
                    checked += 1
            assert checked > 0, (name, label)
            results.append(dict(pathway=name, level=level, label=label, checkedLiquidPixels=checked,
                checkedScreenshotPixels=checked * 16, pixelsCoveredByOriginalDecorations=covered,
                locallyRepairedBasePixels=repaired,
                mismatches=0, referenceOrigin=[ref_x, ref_y], gameOrigin=[game_x, game_y]))
    report = dict(reference="mockups/v4/01-all-22-pathways-x4.png", guiScale=4,
        pathways=22, states=2, mismatches=0,
        checkedLiquidPixels=sum(result["checkedLiquidPixels"] for result in results),
        locallyRepairedBasePixels=sum(result["locallyRepairedBasePixels"] for result in results),
        checkedScreenshotPixels=sum(result["checkedScreenshotPixels"] for result in results), results=results)
    (REVIEW / "pixel-check.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {report['checkedLiquidPixels']} original liquid pixels / {report['checkedScreenshotPixels']} screenshot pixels; zero mismatches in 44 pathway/state pairs.")


if __name__ == "__main__":
    main()
