#!/usr/bin/env python3
"""Builds the RavenOS bootanimation.zip.

part0 (plays once): the raven mark and the RavenOS wordmark fade in.
part1 (loops until boot finishes): a soft light sweeps along a line under the name.

Usage: python3 make_bootanimation.py <aosp-root> <output-zip>
Needs Pillow and rsvg-convert (librsvg).
"""
import io
import math
import os
import subprocess
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 800, 480, 30
# Pure black: Android draws the boot animation over black, so any other
# colour would show as a faint box around the frames.
BG = (0, 0, 0)
FG = (242, 242, 242)
ACCENT = (143, 168, 200)
# Same path as the launcher icon (24x24 viewport).
RAVEN = ("M2,8.2C3.5,7.2 5,6.4 6.6,5.9C8,5.2 9.8,5 11,5.9C12.6,7.2 15,9 17.5,10.9C19.3,12.3 21.4,14 23,15.6L22.1,17C20.4,16.3 18.8,15.6 17.3,15.1C15.8,16 13.6,16.4 11.6,16C9.4,15.5 7.9,13.9 7.3,11.9"
         "L6.6,10.6L6.9,10.2L6.2,9.8C5,9.3 3.5,8.8 2,8.2Z M11.2,15.6h0.6v3h-0.6z M12.9,15.7h0.6v2.9h-0.6z")


def raven_mark(size):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
           f'viewBox="0 0 24 24"><path fill="rgb{FG}" d="{RAVEN}"/>'
           f'<circle cx="8.6" cy="7.1" r="0.45" fill="rgb{BG}"/></svg>')
    png = subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size)],
                         input=svg.encode(), capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(png)).convert("RGBA")


def base_layer(font):
    """The finished still: mark above the wordmark, centred."""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    mark = raven_mark(170)
    layer.alpha_composite(mark, ((W - mark.width) // 2, 70))
    d = ImageDraw.Draw(layer)
    text = "RavenOS"
    box = d.textbbox((0, 0), text, font=font)
    d.text(((W - (box[2] - box[0])) // 2 - box[0], 262), text, font=font, fill=FG + (255,))
    return layer


def frame(still, alpha, sweep=None):
    img = Image.new("RGB", (W, H), BG)
    if alpha < 1:
        faded = still.copy()
        faded.putalpha(still.getchannel("A").point(lambda a: int(a * alpha)))
        img.paste(faded, (0, 0), faded)
    else:
        img.paste(still, (0, 0), still)
    d = ImageDraw.Draw(img)
    x0, x1, y = 300, 500, 360
    # Faint track under the name.
    track = tuple(int(BG[i] + (ACCENT[i] - BG[i]) * 0.25 * alpha) for i in range(3))
    d.line([(x0, y), (x1, y)], fill=track, width=2)
    if sweep is not None:
        # A soft highlight centred at `sweep` (0..1 along the track).
        cx = x0 + (x1 - x0) * sweep
        for x in range(x0, x1 + 1):
            k = math.exp(-((x - cx) / 28.0) ** 2)
            if k < 0.02:
                continue
            c = tuple(int(track[i] + (ACCENT[i] - track[i]) * k) for i in range(3))
            d.line([(x, y - 1), (x, y + 1)], fill=c)
    return img


def png_bytes(img):
    out = io.BytesIO()
    img.save(out, "PNG", optimize=True)
    return out.getvalue()


def main():
    root, out_zip = sys.argv[1], sys.argv[2]
    font = ImageFont.truetype(os.path.join(root, "external/roboto-fonts/Roboto-Light.ttf"), 64)
    still = base_layer(font)

    parts = {"part0": [], "part1": []}
    intro = int(FPS * 1.2)
    for i in range(intro):
        t = (i + 1) / intro
        parts["part0"].append(frame(still, 1 - (1 - t) ** 3))
    loop = int(FPS * 1.6)
    for i in range(loop):
        # Ease in and out across the track, then rest briefly off the ends.
        t = i / loop
        sweep = -0.2 + 1.4 * (0.5 - 0.5 * math.cos(math.pi * t * 2)) if t < 0.5 else None
        parts["part1"].append(frame(still, 1.0, sweep))

    desc = f"{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n"
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_STORED) as z:
        z.writestr("desc.txt", desc)
        for name, frames in parts.items():
            for i, img in enumerate(frames):
                z.writestr(f"{name}/{i:03d}.png", png_bytes(img))


if __name__ == "__main__":
    main()
