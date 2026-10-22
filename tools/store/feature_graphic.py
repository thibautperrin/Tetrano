"""Builds the Play Store feature graphic (1024x500) for one language.

    python tools/store/feature_graphic.py "Qubic with rings" fastlane/metadata/android/en-US/images/featureGraphic.png

Needs Pillow. The background is the home screen's collage, darkened.
"""
import sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFont

tagline, out = sys.argv[1], sys.argv[2]
root = __file__.replace("\\", "/").rsplit("/tools/", 1)[0]
bg = Image.open(f"{root}/app/src/main/res/drawable-nodpi/fond_inverse_landscape.webp").convert("RGB")
w, h = bg.size
scale = 1024 / w
bg = bg.resize((1024, int(h * scale)), Image.LANCZOS)
top = (bg.height - 500) // 2
bg = bg.crop((0, top, 1024, top + 500))
bg = ImageEnhance.Brightness(bg).enhance(0.45)
icon = Image.open(f"{root}/fastlane/metadata/android/en-US/images/icon.png").convert("RGB").crop((100, 100, 414, 414)).resize((300, 300), Image.LANCZOS)
bg.paste(icon, (70, 100))
d = ImageDraw.Draw(bg)
fonts = "C:/Windows/Fonts/"
title = ImageFont.truetype(fonts + "arialbi.ttf", 110)
sub = ImageFont.truetype(fonts + "ariali.ttf", 38)
d.text((410, 120), "Tetrano", font=title, fill=(255, 255, 50))
# wrap the tagline on two lines at most
words, lines, line = tagline.split(), [], ""
for wd in words:
    if d.textlength((line + " " + wd).strip(), font=sub) > 560 and line:
        lines.append(line)
        line = wd
    else:
        line = (line + " " + wd).strip()
lines.append(line)
for i, l in enumerate(lines[:2]):
    d.text((414, 262 + i * 52), l, font=sub, fill=(255, 255, 255))
bg.save(out)
