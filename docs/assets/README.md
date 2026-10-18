# Artwork pipeline

Every image used on the SpigotMC page and in the README is generated from code — no external
editor, no stock art — so it can be rebuilt or rebranded at any time.

| Script | Output |
| --- | --- |
| `generate_banner_icon.py` | `banner.png` (1280×420), `icon.png` (512×512) |
| `generate_infographics.py` | `features`, `versions`, `permissions`, `safety`, `setup`, `commands` (1280×720) |
| `generate_cta.py` | `discord.png`, `documentation.png` (1280×260) |
| `icons/rasterize.js` | Rasterises the official Discord/GitHub SVGs (see `icons/README.md`) |

Each script starts with a `BRAND` block holding the plugin name and the shared lines; for a new
plugin, edit that block first. The rest of the text is product-specific on purpose.

```bash
python docs/assets/generate_banner_icon.py
python docs/assets/generate_infographics.py
python docs/assets/generate_cta.py --discord-url "discord.gg/INVITE" --docs-url "user.github.io/repo"
```

Requirements: Python 3 with Pillow (`pip install pillow`) and the Segoe UI fonts shipped with
Windows — change the `BOLD`, `SEMI` and `REGULAR` paths at the top of each script for another OS.

Output is deterministic: the same text produces byte-identical PNGs, so a diff in git means the
artwork really changed. The full rebranding checklist is in
[../TEMPLATE.md](../TEMPLATE.md#3-regenerating-the-artwork).
