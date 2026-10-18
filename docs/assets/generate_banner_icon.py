"""Generate the SpigotMC banner and icon for JPPlayerVaults."""

import math
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)

GREEN = (34, 197, 94)
BG_TOP = (12, 19, 32)
BG_BOTTOM = (22, 35, 56)
TEXT = (236, 243, 252)
MUTED = (150, 169, 192)

BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
SEMI = r"C:\Windows\Fonts\seguisb.ttf"
REGULAR = r"C:\Windows\Fonts\segoeui.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def vertical_gradient(width, height, top, bottom):
    image = Image.new("RGB", (width, height), top)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        ratio = y / max(1, height - 1)
        color = tuple(round(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3))
        draw.line([(0, y), (width, y)], fill=color)
    return image


def draw_vault(draw, cx, cy, size, glow_draw=None):
    r = size // 2

    if glow_draw is not None:
        for step in range(10, 0, -1):
            alpha = int(3 + step * 1.4)
            pad = int(size * 0.10) + step * 6
            glow_draw.ellipse(
                [cx - r - pad, cy - r - pad, cx + r + pad, cy + r + pad],
                fill=(34, 197, 94, alpha),
            )

    # Outer steel plate.
    draw.rounded_rectangle(
        [cx - r, cy - r, cx + r, cy + r],
        radius=size // 7,
        fill=(35, 46, 63),
        outline=(83, 104, 131),
        width=max(3, size // 90),
    )
    # Door inset.
    inset = int(size * 0.11)
    draw.rounded_rectangle(
        [cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset],
        radius=size // 9,
        fill=(24, 32, 47),
        outline=(58, 76, 101),
        width=max(2, size // 140),
    )
    # Dial.
    dial = int(size * 0.20)
    draw.ellipse(
        [cx - dial, cy - dial, cx + dial, cy + dial],
        fill=(17, 23, 34),
        outline=GREEN,
        width=max(3, size // 75),
    )
    for tick in range(12):
        angle = math.radians(tick * 30)
        inner = dial * 0.74
        outer = dial * 0.88
        draw.line(
            [
                (cx + math.cos(angle) * inner, cy + math.sin(angle) * inner),
                (cx + math.cos(angle) * outer, cy + math.sin(angle) * outer),
            ],
            fill=(110, 132, 160),
            width=max(2, size // 240),
        )
    # Safe handle to the right of the dial.
    handle_h = max(9, int(size * 0.055))
    handle_w = int(size * 0.24)
    handle_x = cx + dial + int(size * 0.03)
    handle_x = min(handle_x, cx + r - inset - handle_w - int(size * 0.02))
    draw.rounded_rectangle(
        [handle_x, cy - handle_h // 2, handle_x + handle_w, cy + handle_h // 2],
        radius=handle_h // 2,
        fill=(126, 148, 174),
    )
    draw.rounded_rectangle(
        [handle_x + handle_w - handle_h, cy - handle_h, handle_x + handle_w, cy + handle_h],
        radius=handle_h // 2,
        fill=(96, 118, 146),
    )
    # Green center.
    knob = max(5, size // 42)
    draw.ellipse([cx - knob, cy - knob, cx + knob, cy + knob], fill=GREEN)
    # Hinges.
    hinge_w = max(6, size // 55)
    hinge_h = max(18, size // 8)
    for side in (-1, 1):
        x = cx + side * (r - inset // 2)
        draw.rounded_rectangle(
            [x - hinge_w // 2, cy - hinge_h, x + hinge_w // 2, cy + hinge_h],
            radius=hinge_w // 2,
            fill=(90, 110, 136),
        )


def banner():
    width, height = 1280, 420
    image = vertical_gradient(width, height, BG_TOP, BG_BOTTOM).convert("RGBA")

    texture = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    tdraw = ImageDraw.Draw(texture)
    for x in range(-height, width, 46):
        tdraw.line([(x, height), (x + height, 0)], fill=(255, 255, 255, 7), width=1)
    image = Image.alpha_composite(image, texture)

    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_vault(draw, 1040, 210, 250, ImageDraw.Draw(glow))
    image = Image.alpha_composite(image, glow)
    draw = ImageDraw.Draw(image)

    draw.rounded_rectangle([80, 96, 152, 104], radius=4, fill=GREEN)
    draw.text((80, 120), "JPPlayerVaults", font=font(BOLD, 68), fill=TEXT)
    draw.text((84, 212), "Private player vaults for Minecraft 1.8 \u2192 latest",
              font=font(REGULAR, 27), fill=MUTED)
    draw.text((84, 262), "One jar  \u00b7  No dependencies  \u00b7  Rank-based  \u00b7  Anti-dupe",
              font=font(SEMI, 21), fill=GREEN)
    draw.text((84, 330), "v1.0.0  \u00b7  MIT  \u00b7  Spigot & Paper",
              font=font(REGULAR, 17), fill=(107, 125, 149))

    image.convert("RGB").save(os.path.join(OUT, "banner.png"), "PNG", optimize=True)
    print("banner.png", image.size)


def icon():
    size = 512
    image = vertical_gradient(size, size, (14, 22, 37), (27, 42, 66)).convert("RGBA")
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_vault(draw, size // 2, size // 2, 366, ImageDraw.Draw(glow))
    image = Image.alpha_composite(image, glow)

    image.convert("RGB").save(os.path.join(OUT, "icon.png"), "PNG", optimize=True)
    print("icon.png", image.size)


banner()
icon()
