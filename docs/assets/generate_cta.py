"""Generate the Discord and documentation call-to-action banners for JPPlayerVaults.

Usage (from docs/assets):
    python generate_cta.py
    python generate_cta.py --discord-url "discord.gg/abc123" --docs-url "user.github.io/jpvaults-docs"

Without --discord-url / --docs-url the banners stay generic, which is the safe
default: the real link lives in the [URL] tag of the SpigotMC description, so the
image never goes stale. Passing the value prints it inside the banner.
"""

import argparse
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
LOGO_DIR = os.path.join(OUT, "icons")
W, H = 1280, 260

TEXT = (236, 243, 252)
MUTED = (150, 169, 192)
DIM = (110, 128, 152)
GREEN = (34, 197, 94)
DISCORD = (88, 101, 242)
GITHUB_BLUE = (31, 111, 235)
GITHUB_ACCENT = (88, 166, 255)

BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
SEMI = r"C:\Windows\Fonts\seguisb.ttf"
REGULAR = r"C:\Windows\Fonts\segoeui.ttf"

_cache = {}


def font(path, size):
    key = (path, size)
    if key not in _cache:
        try:
            _cache[key] = ImageFont.truetype(path, size)
        except OSError:
            _cache[key] = ImageFont.load_default()
    return _cache[key]


def base(top, bottom):
    image = Image.new("RGB", (W, H), top)
    draw = ImageDraw.Draw(image)
    for y in range(H):
        ratio = y / (H - 1)
        color = tuple(round(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3))
        draw.line([(0, y), (W, y)], fill=color)

    texture = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tdraw = ImageDraw.Draw(texture)
    for x in range(-H, W, 46):
        tdraw.line([(x, H), (x + H, 0)], fill=(255, 255, 255, 6), width=1)
    return Image.alpha_composite(image.convert("RGBA"), texture)


# ----------------------------------------------------------------- icons


def ic_discord(draw, cx, cy, s, color=(255, 255, 255)):
    """Stylised chat bubble with two eyes (not the trademarked logo)."""
    body = [cx - s * 0.86, cy - s * 0.66, cx + s * 0.86, cy + s * 0.42]
    draw.rounded_rectangle(body, radius=int(s * 0.42), fill=color)
    draw.polygon([(cx - s * 0.42, cy + s * 0.24), (cx - s * 0.18, cy + s * 0.24),
                  (cx - s * 0.46, cy + s * 0.82)], fill=color)
    eye = max(3, int(s * 0.15))
    for side in (-1, 1):
        ex = cx + side * s * 0.34
        draw.ellipse([ex - eye, cy - s * 0.16 - eye, ex + eye, cy - s * 0.16 + eye],
                     fill=(20, 22, 34))


def ic_wiki(draw, cx, cy, s, color=(255, 255, 255)):
    """Open book."""
    page_w = s * 0.74
    page_h = s * 0.92
    left = [cx - page_w - s * 0.06, cy - page_h / 2, cx - s * 0.06, cy - page_h / 2 + s * 0.12,
            cx - s * 0.06, cy + page_h / 2 - s * 0.06, cx - page_w - s * 0.06, cy + page_h / 2 - s * 0.18]
    right = [cx + page_w + s * 0.06, cy - page_h / 2, cx + s * 0.06, cy - page_h / 2 + s * 0.12,
             cx + s * 0.06, cy + page_h / 2 - s * 0.06, cx + page_w + s * 0.06, cy + page_h / 2 - s * 0.18]
    draw.polygon(left, fill=color)
    draw.polygon(right, fill=color)
    draw.line([(cx - s * 0.06, cy - page_h / 2 + s * 0.1), (cx - s * 0.06, cy + page_h / 2 - s * 0.08)],
              fill=(20, 26, 38), width=max(3, int(s * 0.09)))


def paste_logo(image, name, cx, cy, size):
    """Centre a transparent PNG logo on (cx, cy). False if the file is missing."""
    path = os.path.join(LOGO_DIR, name)
    if not os.path.exists(path):
        return False
    logo = Image.open(path).convert("RGBA")
    ratio = size / max(logo.width, logo.height)
    logo = logo.resize(
        (max(1, round(logo.width * ratio)), max(1, round(logo.height * ratio))),
        Image.LANCZOS,
    )
    image.alpha_composite(logo, (round(cx - logo.width / 2), round(cy - logo.height / 2)))
    return True


# ----------------------------------------------------------------- banners


def banner(kind, url_text):
    if kind == "discord":
        image = base((18, 20, 34), (30, 34, 66))
        brand = DISCORD
        badge_fill = DISCORD
        logo_file, logo_size = "discord-white.png", 54
        title = "Join the community Discord"
        subtitle = "Support, bug reports, feature requests and release updates."
        button = "JOIN THE DISCORD"
    else:
        image = base((9, 13, 19), (20, 28, 40))
        brand = GITHUB_BLUE
        badge_fill = (255, 255, 255)
        logo_file, logo_size = "github-dark.png", 58
        title = "Documentation, guides and FAQ"
        subtitle = "Installation, configuration, permissions, commands and changelog."
        button = "OPEN THE DOCS"

    draw = ImageDraw.Draw(image)

    # Icon badge with the official platform mark (falls back to a drawn glyph).
    bx, by, bs = 64, H / 2 - 48, 96
    draw.rounded_rectangle([bx, by, bx + bs, by + bs], radius=24, fill=badge_fill)
    if not paste_logo(image, logo_file, bx + bs / 2, by + bs / 2, logo_size):
        if kind == "discord":
            ic_discord(draw, bx + bs / 2, by + bs / 2, 34)
        else:
            ic_wiki(draw, bx + bs / 2, by + bs / 2, 34, (13, 17, 23))

    # Texts.
    tx = bx + bs + 40
    draw.text((tx, 60), title, font=font(BOLD, 36), fill=TEXT)
    draw.text((tx, 116), subtitle, font=font(REGULAR, 20), fill=MUTED)
    if url_text:
        draw.text((tx + 2, 150), url_text, font=font(SEMI, 18),
                  fill=GITHUB_ACCENT if kind == "docs" else (168, 178, 255))

    # CTA button, right aligned.
    bfnt = font(BOLD, 19)
    padding = 30
    bw = draw.textlength(button, font=bfnt) + padding * 2
    bh = 56
    bx2 = W - 64 - bw
    by2 = H / 2 - bh / 2
    draw.rounded_rectangle([bx2, by2, bx2 + bw, by2 + bh], radius=bh // 2, fill=brand)
    draw.text((bx2 + bw / 2, by2 + bh / 2), button, font=bfnt, fill=(255, 255, 255), anchor="mm")

    # Brand anchor.
    draw.rounded_rectangle([64, 226, 96, 232], radius=3, fill=GREEN)
    draw.text((108, 221), "JPPlayerVaults", font=font(SEMI, 16), fill=DIM)

    name = "discord.png" if kind == "discord" else "documentation.png"
    image.convert("RGB").save(os.path.join(OUT, name), "PNG", optimize=True)
    print(name)


parser = argparse.ArgumentParser()
parser.add_argument("--discord-url", default=None)
parser.add_argument("--docs-url", default=None)
args = parser.parse_args()

banner("discord", args.discord_url)
banner("docs", args.docs_url)
