#!/usr/bin/env python3
"""Rebuild captioned Play screenshots and feature graphics from the raw captures.

Usage: python3 build_assets.py   (needs Pillow; reads listing/variants.json)
Raw captures live in screenshots/original (960x2142, demo-mode status bar). Their
aspect ratio (2.23:1) is outside Play's limit, so they are never uploaded as-is:
each is cropped (status bar + nav pill removed) and framed on a 1080x1920 canvas.
"""
import json, os, hashlib
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
VIOLET, GRAPE, INK, SUN = (0x6C,0x4C,0xF1), (0x9B,0x5D,0xE5), (0x23,0x1B,0x4A), (0xFF,0xB0,0x20)  # app theme (ui/theme/Color.kt)
FONT = "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf"
W, H = 1080, 1920
CAP_H = 306

def gradient(w, h, c1=VIOLET, c2=GRAPE):
    g = Image.new("RGB", (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.35 + y / h * 0.65)
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return g

def rounded(img, r, bottom=False):
    m = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, img.width - 1, img.height - 1 + (r * 2 if bottom else 0)], r, fill=255)
    out = img.convert("RGBA"); out.putalpha(m); return out

def fit_text(d, text, maxw, maxlines=2, start=76, lo=44):
    for size in range(start, lo - 1, -2):
        f = ImageFont.truetype(FONT, size)
        words, lines, cur = text.split(), [], ""
        for w in words:
            t = (cur + " " + w).strip()
            if d.textlength(t, font=f) <= maxw: cur = t
            else: lines.append(cur); cur = w
        lines.append(cur)
        if len(lines) <= maxlines and all(d.textlength(l, font=f) <= maxw for l in lines):
            return f, lines
    return f, lines

def framed_shot(raw_path, out_w=800, out_h=None):
    raw = Image.open(raw_path).convert("RGB")
    crop = raw.crop((0, 150, raw.width, raw.height - 55))   # drop status bar and gesture pill
    s = out_w / crop.width
    return crop.resize((out_w, int(crop.height * s)), Image.LANCZOS)

def compose(raw_path, caption, out_path):
    bg = gradient(W, H)
    d = ImageDraw.Draw(bg)
    f, lines = fit_text(d, caption, W - 120)
    lh = int(f.size * 1.18)
    y = (CAP_H - lh * len(lines)) // 2 + 6
    for ln in lines:
        d.text((W // 2, y), ln, font=f, fill="white", anchor="mt"); y += lh
    shot = framed_shot(raw_path)
    top = CAP_H
    avail = H - top
    shot = shot.crop((0, 0, shot.width, min(shot.height, avail)))
    sh = rounded(shot, 44)
    # soft shadow
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([(W - shot.width)//2, top + 10, (W + shot.width)//2, H + 80], 44, fill=(20, 10, 60, 120))
    bg = Image.alpha_composite(bg.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(18)))
    bg.alpha_composite(sh, ((W - shot.width) // 2, top))
    bg.convert("RGB").save(out_path, "PNG", optimize=True)

def feature_graphic(icon_path, tagline, raw_path, out_path):
    fw, fh = 1024, 500
    bg = gradient(fw, fh)
    d = ImageDraw.Draw(bg)
    icon = Image.open(icon_path).convert("RGB")
    # the stored icon has white margins: crop to the artwork before rounding
    from PIL import ImageChops
    bbox = ImageChops.difference(icon, Image.new("RGB", icon.size, (255, 255, 255))).point(lambda v: 255 if v > 24 else 0).getbbox()
    if bbox: icon = icon.crop(bbox)
    icon = icon.resize((170, 170), Image.LANCZOS)
    m = rounded(icon, 38)
    bg.paste(m, (64, 60), m)
    ft = ImageFont.truetype(FONT, 72)
    d.text((64, 310), "RiddleVerse", font=ft, fill="white", anchor="ls")
    f2, lines = fit_text(d, tagline, 600, maxlines=2, start=40, lo=28)
    y = 340
    for ln in lines:
        d.text((64, y), ln, font=f2, fill=(255, 232, 170), anchor="lt"); y += int(f2.size * 1.25)
    shot = framed_shot(raw_path, out_w=270)
    shot = shot.crop((0, 0, shot.width, 400))
    bg_rgba = bg.convert("RGBA")
    bg_rgba.alpha_composite(rounded(shot, 30), (fw - 270 - 70, 100))
    bg_rgba.convert("RGB").save(out_path, "PNG", optimize=True)

def main():
    data = json.load(open(os.path.join(HERE, "listing/variants.json")))
    orig = os.path.join(HERE, "screenshots/original")
    for vid, v in data["variants"].items():
        od = os.path.join(HERE, f"screenshots/variant_{vid}"); os.makedirs(od, exist_ok=True)
        for i, (name, cap) in enumerate(v["screenshots"], 1):
            compose(os.path.join(orig, name + ".png"), cap, os.path.join(od, f"{i:02d}_{name.split('_', 1)[1]}.png"))
        feature_graphic(os.path.join(HERE, "graphics/icon_512_current.png"), v["feature_tagline"],
                        os.path.join(orig, "01_create_prompt.png"),
                        os.path.join(HERE, f"graphics/feature_graphic_variant_{vid}_1024x500.png"))
    print("built")

if __name__ == "__main__":
    main()
