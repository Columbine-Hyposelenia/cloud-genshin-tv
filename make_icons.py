#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
LOGO = "/home/user/Doubao/chats/38445428397619970/research/logo/logo_text.png"
RES = os.path.join(ROOT, "app", "src", "main", "res")


def make_background(size):
    top = (16, 26, 52)
    bottom = (5, 9, 20)
    img = Image.new("RGB", (size, size), bottom)
    px = img.load()
    for y in range(size):
        t = y / float(size - 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        for x in range(size):
            px[x, y] = (r, g, b)

    glow = Image.new("L", (size, size), 0)
    gd = ImageDraw.Draw(glow)
    cx, cy = int(size * 0.5), int(size * 0.42)
    radius = int(size * 0.62)
    gd.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.12))
    blue = Image.new("RGB", (size, size), (46, 86, 168))
    img = Image.composite(blue, img, glow)
    return img


def place_logo(canvas, logo, fraction):
    size = canvas.size[0]
    target_w = int(size * fraction)
    ratio = logo.size[1] / float(logo.size[0])
    target_h = int(target_w * ratio)
    resized = logo.resize((target_w, target_h), Image.LANCZOS)
    x = (size - target_w) // 2
    y = int((size - target_h) * 0.52)
    if resized.mode == "RGBA":
        canvas.paste(resized, (x, y), resized)
    else:
        canvas.paste(resized, (x, y))
    return canvas


def make_icon(size):
    bg = make_background(size).convert("RGBA")
    logo = Image.open(LOGO).convert("RGBA")
    return place_logo(bg, logo, 0.80)


def make_banner(width, height):
    base = make_background(max(width, height)).convert("RGBA")
    base = base.crop((0, 0, width, height)).resize((width, height))
    logo = Image.open(LOGO).convert("RGBA")
    target_h = int(height * 0.62)
    ratio = logo.size[0] / float(logo.size[1])
    target_w = int(target_h * ratio)
    resized = logo.resize((target_w, target_h), Image.LANCZOS)
    x = (width - target_w) // 2
    y = (height - target_h) // 2
    base.paste(resized, (x, y), resized)
    return base


ICONS = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in ICONS.items():
    out_dir = os.path.join(RES, folder)
    os.makedirs(out_dir, exist_ok=True)
    icon = make_icon(size)
    icon.save(os.path.join(out_dir, "ic_launcher.png"))

BANNERS = {
    "drawable-mdpi": (160, 90),
    "drawable-hdpi": (240, 135),
    "drawable-xhdpi": (320, 180),
    "drawable-xxhdpi": (480, 270),
}

for folder, (w, h) in BANNERS.items():
    out_dir = os.path.join(RES, folder)
    os.makedirs(out_dir, exist_ok=True)
    banner = make_banner(w, h)
    banner.save(os.path.join(out_dir, "tv_banner.png"))

print("icons and banners generated")
