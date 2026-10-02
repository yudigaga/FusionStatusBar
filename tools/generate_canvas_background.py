"""Regenerate the bundled backdrop with Pillow; not run by the Android app."""

from pathlib import Path
from random import Random

from PIL import Image, ImageDraw, ImageFilter


def main():
    width, height, padding = 384, 832, 128
    source = Image.new("L", (width + padding * 2, height + padding * 2), 8)
    draw = ImageDraw.Draw(source)

    # Edge-to-edge charcoal folds, softened offline rather than at every frame.
    def band(points, shade):
        draw.polygon([(x + padding, y + padding) for x, y in points], fill=shade)

    band([(-128, 110), (512, 370), (512, 640), (-128, 380)], 32)
    band([(-128, 420), (512, 170), (512, 295), (-128, 545)], 19)
    band([(-128, 560), (512, 775), (512, 960), (-128, 960)], 14)
    blurred = source.filter(ImageFilter.GaussianBlur(48)).crop(
        (padding, padding, padding + width, padding + height)
    )
    # One-level deterministic dithering limits banding in very dark tones.
    random = Random(120)
    blurred.putdata([max(0, min(255, value + random.choice((-1, 0, 1))))
                     for value in blurred.tobytes()])
    output = (Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
              / "control_center_canvas_wallpaper.png")
    output.parent.mkdir(parents=True, exist_ok=True)
    blurred.convert("RGB").save(output, optimize=True)
    print(f"Generated {width}x{height} opaque backdrop: {output}")


if __name__ == "__main__":
    main()
