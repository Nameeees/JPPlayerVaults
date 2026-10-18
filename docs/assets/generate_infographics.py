"""Generate the SpigotMC infographics for JPPlayerVaults (1280x720, no code shown).

Template note: edit the BRAND block for the shared footer, then rewrite the
per-image text (features, versions, permissions, safety, setup, commands)
for the new plugin - that content is product-specific on purpose.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

# ------------------------------------------------------------------ BRAND
BRAND = "JPPlayerVaults"
FOOTLINE = "JPPlayerVaults  \u00b7  v1.0.0  \u00b7  MIT  \u00b7  Spigot & Paper"
SERIES = "1.8 \u2192 26.3"

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)

W, H = 1280, 720
GREEN = (34, 197, 94)
GREEN_SOFT = (24, 128, 66)
TEXT = (236, 243, 252)
MUTED = (150, 169, 192)
DIM = (108, 126, 150)
CARD = (26, 36, 54)
CARD_BORDER = (44, 59, 83)
ICON_BG = (19, 28, 43)
BG_TOP = (12, 19, 32)
BG_BOTTOM = (20, 32, 52)

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


def base(title, subtitle):
    image = Image.new("RGB", (W, H), BG_TOP)
    draw = ImageDraw.Draw(image)
    for y in range(H):
        ratio = y / (H - 1)
        color = tuple(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * ratio) for i in range(3))
        draw.line([(0, y), (W, y)], fill=color)

    texture = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tdraw = ImageDraw.Draw(texture)
    for x in range(-H, W, 52):
        tdraw.line([(x, H), (x + H, 0)], fill=(255, 255, 255, 6), width=1)
    image = Image.alpha_composite(image.convert("RGBA"), texture)

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([80, 74, 146, 82], radius=4, fill=GREEN)
    draw.text((80, 100), title, font=font(BOLD, 52), fill=TEXT)
    draw.text((82, 168), subtitle, font=font(REGULAR, 24), fill=MUTED)

    draw.line([(80, 664), (1200, 664)], fill=(38, 51, 72), width=2)
    draw.text((80, 682), FOOTLINE, font=font(REGULAR, 17), fill=DIM)
    draw.text((1200, 682), SERIES, font=font(SEMI, 17), fill=GREEN, anchor="ra")
    return image, draw


def wrap(draw, text, fnt, max_width):
    lines, current = [], ""
    for word in text.split():
        trial = (current + " " + word).strip()
        if draw.textlength(trial, font=fnt) <= max_width:
            current = trial
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def card(draw, x, y, w, h, icon_fn, title, body):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=18, fill=CARD, outline=CARD_BORDER, width=2)
    cx, cy, r = x + 62, y + 62, 30
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ICON_BG, outline=(38, 52, 74), width=2)
    icon_fn(draw, cx, cy, r - 6)
    draw.text((x + 108, y + 40), title, font=font(SEMI, 25), fill=TEXT)
    yy = y + 104
    body_font = font(REGULAR, 18)
    for line in wrap(draw, body, body_font, w - 72)[:3]:
        draw.text((x + 36, yy), line, font=body_font, fill=MUTED)
        yy += 27


def pill(draw, x, y, text, outline=GREEN, color=None, pad=22, height=42, fnt=None):
    fnt = fnt or font(SEMI, 18)
    width = draw.textlength(text, font=fnt) + pad * 2
    draw.rounded_rectangle([x, y, x + width, y + height], radius=height // 2,
                           fill=(20, 30, 45), outline=outline, width=2)
    draw.text((x + width / 2, y + height / 2), text, font=fnt,
              fill=color or TEXT, anchor="mm")
    return width


def check(draw, x, y, r=13):
    draw.ellipse([x - r, y - r, x + r, y + r], fill=(22, 45, 34), outline=GREEN, width=2)
    draw.line([(x - 5, y), (x - 1, y + 5), (x + 6, y - 5)], fill=GREEN, width=3, joint="curve")


# ----------------------------------------------------------------- icons


def ic_jar(draw, cx, cy, s):
    draw.rounded_rectangle([cx - s * 0.62, cy - s * 0.62, cx + s * 0.62, cy + s * 0.72],
                           radius=5, outline=GREEN, width=3)
    draw.line([(cx - s * 0.62, cy - s * 0.22), (cx + s * 0.62, cy - s * 0.22)], fill=GREEN, width=3)
    draw.rounded_rectangle([cx - s * 0.3, cy - s * 0.88, cx + s * 0.3, cy - s * 0.66],
                           radius=3, fill=GREEN)


def ic_no_deps(draw, cx, cy, s):
    draw.rounded_rectangle([cx - s * 0.6, cy - s * 0.6, cx + s * 0.6, cy + s * 0.6],
                           radius=4, outline=DIM, width=3)
    draw.line([(cx - s * 0.75, cy + s * 0.75), (cx + s * 0.75, cy - s * 0.75)], fill=GREEN, width=4)


def ic_rank(draw, cx, cy, s):
    bar_w = s * 0.34
    gap = s * 0.16
    heights = (0.55, 0.9, 1.3)
    total = bar_w * 3 + gap * 2
    x = cx - total / 2
    base = cy + s * 0.72
    for index, height in enumerate(heights):
        top = base - s * height
        draw.rounded_rectangle([x, top, x + bar_w, base], radius=3,
                               fill=GREEN if index == 2 else GREEN_SOFT)
        x += bar_w + gap


def ic_shield(draw, cx, cy, s):
    points = [(cx, cy - s * 0.85), (cx + s * 0.72, cy - s * 0.5), (cx + s * 0.72, cy + s * 0.15),
              (cx, cy + s * 0.85), (cx - s * 0.72, cy + s * 0.15), (cx - s * 0.72, cy - s * 0.5)]
    draw.polygon(points, fill=(20, 34, 28), outline=GREEN)
    draw.line(points + [points[0]], fill=GREEN, width=3, joint="curve")
    draw.ellipse([cx - s * 0.16, cy - s * 0.28, cx + s * 0.16, cy + s * 0.04], fill=GREEN)
    draw.rectangle([cx - s * 0.07, cy - s * 0.05, cx + s * 0.07, cy + s * 0.35], fill=GREEN)


def ic_doc(draw, cx, cy, s):
    draw.rounded_rectangle([cx - s * 0.58, cy - s * 0.78, cx + s * 0.58, cy + s * 0.78],
                           radius=4, outline=GREEN, width=3)
    for index in range(3):
        y = cy - s * 0.32 + index * s * 0.34
        draw.line([(cx - s * 0.32, y), (cx + s * 0.32, y)], fill=GREEN_SOFT if index else GREEN, width=3)


def ic_globe(draw, cx, cy, s):
    draw.ellipse([cx - s * 0.78, cy - s * 0.78, cx + s * 0.78, cy + s * 0.78], outline=GREEN, width=3)
    draw.ellipse([cx - s * 0.34, cy - s * 0.78, cx + s * 0.34, cy + s * 0.78], outline=GREEN_SOFT, width=2)
    draw.line([(cx - s * 0.78, cy), (cx + s * 0.78, cy)], fill=GREEN, width=3)


def ic_key(draw, cx, cy, s):
    draw.ellipse([cx - s * 0.8, cy - s * 0.55, cx - s * 0.1, cy + s * 0.15], outline=GREEN, width=3)
    draw.line([(cx - s * 0.1, cy - s * 0.2), (cx + s * 0.8, cy + s * 0.7)], fill=GREEN, width=4)
    draw.line([(cx + s * 0.42, cy + s * 0.32), (cx + s * 0.62, cy + s * 0.12)], fill=GREEN, width=4)
    draw.line([(cx + s * 0.66, cy + s * 0.56), (cx + s * 0.86, cy + s * 0.36)], fill=GREEN, width=4)


def ic_download(draw, cx, cy, s):
    draw.line([(cx, cy - s * 0.8), (cx, cy + s * 0.25)], fill=GREEN, width=4)
    draw.line([(cx - s * 0.42, cy - s * 0.15), (cx, cy + s * 0.3), (cx + s * 0.42, cy - s * 0.15)],
              fill=GREEN, width=4, joint="curve")
    draw.line([(cx - s * 0.8, cy + s * 0.62), (cx + s * 0.8, cy + s * 0.62)], fill=GREEN, width=4)


def ic_terminal(draw, cx, cy, s):
    draw.line([(cx - s * 0.6, cy - s * 0.45), (cx - s * 0.1, cy), (cx - s * 0.6, cy + s * 0.45)],
              fill=GREEN, width=4, joint="curve")
    draw.line([(cx + s * 0.1, cy + s * 0.45), (cx + s * 0.65, cy + s * 0.45)], fill=GREEN, width=4)


def ic_clock(draw, cx, cy, s):
    draw.ellipse([cx - s * 0.8, cy - s * 0.8, cx + s * 0.8, cy + s * 0.8], outline=GREEN, width=3)
    draw.line([(cx, cy), (cx, cy - s * 0.45)], fill=GREEN, width=3)
    draw.line([(cx, cy), (cx + s * 0.4, cy + s * 0.2)], fill=GREEN, width=3)


def ic_bolt(draw, cx, cy, s):
    draw.polygon([(cx + s * 0.15, cy - s * 0.85), (cx - s * 0.55, cy + s * 0.12),
                  (cx - s * 0.05, cy + s * 0.12), (cx - s * 0.2, cy + s * 0.85),
                  (cx + s * 0.55, cy - s * 0.15), (cx + s * 0.02, cy - s * 0.15)],
                 fill=GREEN)


def ic_ghost(draw, cx, cy, s):
    draw.polygon([(cx - s * 0.6, cy - s * 0.2), (cx - s * 0.6, cy + s * 0.7), (cx - s * 0.3, cy + s * 0.5),
                  (cx, cy + s * 0.75), (cx + s * 0.3, cy + s * 0.5), (cx + s * 0.6, cy + s * 0.7),
                  (cx + s * 0.6, cy - s * 0.2)], fill=(30, 42, 60), outline=DIM)
    draw.arc([cx - s * 0.6, cy - s * 0.85, cx + s * 0.6, cy + s * 0.35], 180, 360, fill=DIM, width=3)
    draw.line([(cx - s * 0.9, cy + s * 0.85), (cx + s * 0.9, cy - s * 0.85)], fill=(220, 90, 90), width=4)


# ----------------------------------------------------------------- 1. features


def features():
    image, draw = base("What you get",
                       "Private, rank-based vaults with no strings attached.")
    cards = [
        (ic_jar, "One jar, 1.8 \u2192 latest", "The same build runs on Spigot, Paper and their forks."),
        (ic_no_deps, "No dependencies", "No Vault, no database, no ProtocolLib, no NMS."),
        (ic_rank, "Ranks decide", "The highest jpvaults.vaults.N the player has wins."),
        (ic_shield, "Built to protect items", "Durable saves, atomic writes, Q and F guarded."),
        (ic_doc, "Readable YAML", "One file per player, easy to back up or repair."),
        (ic_globe, "English & Spanish", "Every message configurable, more languages by file."),
    ]
    x0, y0, cw, ch, gap = 80, 226, 360, 196, 40
    for index, (icon, title, body) in enumerate(cards):
        col, row = index % 3, index // 3
        card(draw, x0 + col * (cw + gap), y0 + row * (ch + gap), cw, ch, icon, title, body)
    image.convert("RGB").save(os.path.join(OUT, "features.png"), "PNG", optimize=True)
    print("features.png")


# ----------------------------------------------------------------- 2. versions


def versions():
    image, draw = base("One jar. Every version.",
                       "Compiled against the Spigot 1.8.8 API \u00b7 Java 8 bytecode \u00b7 no NMS.")

    track_y = 330
    draw.rounded_rectangle([140, track_y - 8, 1140, track_y + 8], radius=8,
                           fill=(28, 40, 60), outline=(46, 62, 86), width=2)
    draw.rounded_rectangle([140, track_y - 8, 1140, track_y + 8], radius=8, outline=GREEN_SOFT, width=2)

    marks = [("1.8", 0.0, True), ("1.12", 0.28, False), ("1.16", 0.52, False),
             ("1.21", 0.78, False), ("26.3", 1.0, True)]
    for label, position, tested in marks:
        x = 140 + (1140 - 140) * position
        color = GREEN if tested else (86, 106, 134)
        draw.ellipse([x - 11, track_y - 11, x + 11, track_y + 11], fill=(16, 24, 37), outline=color, width=4)
        draw.text((x, track_y + 42), label, font=font(SEMI, 24), fill=TEXT if tested else MUTED, anchor="mm")
        if tested:
            draw.text((x, track_y + 76), "tested", font=font(REGULAR, 17), fill=GREEN, anchor="mm")

    draw.text((140, 190), "1.8", font=font(BOLD, 64), fill=TEXT)
    draw.text((250, 214), "\u2192", font=font(REGULAR, 40), fill=GREEN)
    draw.text((320, 190), "26.3", font=font(BOLD, 64), fill=TEXT)
    draw.text((470, 214), "and everything in between", font=font(REGULAR, 26), fill=MUTED)

    x = 140
    for text in ("Spigot & Paper", "Java 8 runtime", "No dependencies", "No network calls"):
        x += pill(draw, x, 470, text) + 18

    draw.text((140, 566), "Minecraft 26.1+ needs Java 25 for the server itself; the plugin jar is still Java 8.",
              font=font(REGULAR, 19), fill=DIM)
    image.convert("RGB").save(os.path.join(OUT, "versions.png"), "PNG", optimize=True)
    print("versions.png")


# ----------------------------------------------------------------- 3. safety


def safety():
    image, draw = base("Your items stay yours.",
                       "Vaults hold the most valuable items on a server, so they are guarded.")
    ic_shield(draw, 250, 430, 150)

    lines = [
        "Durable save on close: the file is on disk before the vault is released.",
        "Q and Ctrl+Q are blocked in the vault GUI.",
        "Clicks outside the window never drop the cursor item.",
        "Off-hand swap (F) and the 1.16.x cursor desync are cancelled.",
        "Atomic writes: a .tmp file is moved over the real one, never half written.",
        "Unreadable player files are kept as a timestamped .bak before any overwrite.",
        "No cross-session cache, plus autosave while a vault stays open.",
    ]
    y = 268
    for text in lines:
        check(draw, 500, y + 9)
        draw.text((530, y), text, font=font(REGULAR, 21), fill=TEXT)
        y += 52
    image.convert("RGB").save(os.path.join(OUT, "safety.png"), "PNG", optimize=True)
    print("safety.png")


# ----------------------------------------------------------------- 4. permissions


def permissions():
    image, draw = base("Ranks decide, not the plugin.",
                       "Access comes from permissions only \u2014 no economy plugin, no API.")
    ranks = [("Default", "1", "vaults.default-amount", MUTED),
             ("VIP", "3", "jpvaults.vaults.3", GREEN),
             ("MVP", "6", "jpvaults.vaults.6", GREEN)]
    x0, y0, cw, ch, gap = 80, 244, 360, 250, 40
    for index, (name, amount, node, color) in enumerate(ranks):
        x = x0 + index * (cw + gap)
        draw.rounded_rectangle([x, y0, x + cw, y0 + ch], radius=18, fill=CARD,
                               outline=CARD_BORDER, width=2)
        draw.text((x + 34, y0 + 30), name, font=font(SEMI, 26), fill=TEXT)
        draw.ellipse([x + cw - 108, y0 + 24, x + cw - 24, y0 + 108], fill=ICON_BG,
                     outline=color, width=3)
        draw.text((x + cw - 66, y0 + 62), amount, font=font(BOLD, 44), fill=color, anchor="mm")
        draw.text((x + 34, y0 + 112), "vaults", font=font(REGULAR, 19), fill=MUTED)
        draw.rounded_rectangle([x + 26, y0 + 158, x + cw - 26, y0 + 206], radius=10,
                               fill=(17, 25, 38), outline=(40, 54, 76), width=2)
        draw.text((x + cw / 2, y0 + 182), node, font=font(REGULAR, 18), fill=GREEN, anchor="mm")

    draw.text((80, 540), "Example: a rank with jpvaults.vaults.3 can open /pv 1\u20133; /pv 4 answers \u201clocked\u201d.",
              font=font(REGULAR, 21), fill=MUTED)
    x = 80
    for text in ("jpvaults.use \u2014 default true", "jpvaults.reload \u2014 OP", "Up to 100 vaults per rank"):
        x += pill(draw, x, 592, text, outline=(52, 70, 96), color=MUTED) + 16
    image.convert("RGB").save(os.path.join(OUT, "permissions.png"), "PNG", optimize=True)
    print("permissions.png")


# ----------------------------------------------------------------- 5. setup


def setup():
    image, draw = base("Ready in three steps.",
                       "Drop the jar in, grant the ranks, and players can open their vaults.")
    steps = [
        (ic_download, "1", "Install", "Put JPPlayerVaults-1.0.0.jar in plugins/ and start the server."),
        (ic_key, "2", "Grant the ranks", "Set jpvaults.vaults.N on each group with LuckPerms or similar."),
        (ic_terminal, "3", "Play", "Players run /pv 1, /pv 2\u2026 and their vault opens like a chest."),
    ]
    x0, y0, cw, ch, gap = 80, 238, 360, 250, 40
    for index, (icon, number, title, body) in enumerate(steps):
        x = x0 + index * (cw + gap)
        draw.rounded_rectangle([x, y0, x + cw, y0 + ch], radius=18, fill=CARD,
                               outline=CARD_BORDER, width=2)
        cx, cy, r = x + 62, y0 + 62, 30
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ICON_BG, outline=(38, 52, 74), width=2)
        icon(draw, cx, cy, r - 6)
        draw.text((x + 108, y0 + 40), title, font=font(SEMI, 25), fill=TEXT)
        draw.text((x + cw - 36, y0 + 34), number, font=font(BOLD, 30), fill=(52, 70, 96), anchor="ra")
        yy = y0 + 116
        for line in wrap(draw, body, font(REGULAR, 19), cw - 72)[:4]:
            draw.text((x + 36, yy), line, font=font(REGULAR, 19), fill=MUTED)
            yy += 28

    draw.text((80, 546), "Configuration: vaults.size, default-amount, max-amount, autosave, sounds and messages.",
              font=font(REGULAR, 21), fill=MUTED)
    x = 80
    for text in ("config.yml", "messages_en.yml", "messages_es.yml", "/pv reload"):
        x += pill(draw, x, 596, text, outline=(52, 70, 96), color=MUTED) + 16
    image.convert("RGB").save(os.path.join(OUT, "setup.png"), "PNG", optimize=True)
    print("setup.png")


# ----------------------------------------------------------------- 6. commands


def commands():
    image, draw = base("Commands & messages",
                       "Three commands and one prefix; every visible text lives in a language file.")
    rows = [
        ("/pv <number>", "Opens your private vault with that number.", "jpvaults.use"),
        ("/pv  or  /pv help", "Shows the help and how many vaults you have.", "jpvaults.use"),
        ("/pv reload", "Reloads config.yml and the messages in place.", "jpvaults.reload"),
    ]
    y = 250
    for command, description, permission in rows:
        draw.rounded_rectangle([80, y, 700, y + 92], radius=14, fill=CARD,
                               outline=CARD_BORDER, width=2)
        draw.text((112, y + 20), command, font=font(SEMI, 26), fill=GREEN)
        draw.text((112, y + 58), description, font=font(REGULAR, 17), fill=MUTED)
        width = draw.textlength(permission, font=font(REGULAR, 16)) + 34
        draw.rounded_rectangle([700 - 26 - width, y + 28, 700 - 26, y + 62], radius=17,
                               fill=(17, 25, 38), outline=(46, 62, 86), width=2)
        draw.text((700 - 26 - width / 2, y + 45), permission, font=font(REGULAR, 16),
                  fill=MUTED, anchor="mm")
        y += 108

    draw.text((80, 578), "Aliases: /playervaults and /jpvaults.  Tab completion included.",
              font=font(REGULAR, 19), fill=DIM)

    x0, y0, cw, ch = 760, 250, 440, 320
    draw.rounded_rectangle([x0, y0, x0 + cw, y0 + ch], radius=14, fill=(15, 22, 34),
                           outline=CARD_BORDER, width=2)
    draw.rounded_rectangle([x0, y0, x0 + cw, y0 + 48], radius=14, fill=(21, 30, 45))
    draw.rectangle([x0, y0 + 34, x0 + cw, y0 + 48], fill=(21, 30, 45))
    draw.text((x0 + 22, y0 + 24), "chat preview", font=font(SEMI, 17), fill=MUTED, anchor="lm")

    chat_font = font(REGULAR, 17)
    lines = [
        ((150, 150, 150), "[Vaults] \u00bb ", (205, 213, 223), "Vault #1 opened."),
        ((150, 150, 150), "[Vaults] \u00bb ", (232, 96, 96), "Vault #4 is locked. Rank: 3."),
        ((150, 150, 150), "[Vaults] \u00bb ", (120, 220, 150), "Config and messages reloaded."),
    ]
    yy = y0 + 80
    for prefix_color, prefix, body_color, body in lines:
        draw.text((x0 + 22, yy), prefix, font=chat_font, fill=prefix_color)
        offset = draw.textlength(prefix, font=chat_font)
        draw.text((x0 + 22 + offset, yy), body, font=chat_font, fill=body_color)
        yy += 32

    notes = [
        "Every line comes from messages_<language>.yml:",
        "\u2022  legacy & colours, &#RRGGBB on 1.16+",
        "\u2022  leave one empty to stop sending it",
        "\u2022  English and Spanish included",
    ]
    yy += 10
    for index, note in enumerate(notes):
        draw.text((x0 + 22, yy), note, font=font(SEMI if index == 0 else REGULAR, 16),
                  fill=TEXT if index == 0 else MUTED)
        yy += 27

    image.convert("RGB").save(os.path.join(OUT, "commands.png"), "PNG", optimize=True)
    print("commands.png")


features()
versions()
safety()
permissions()
setup()
commands()