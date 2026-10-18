# Platform icons

Official Discord and GitHub marks used by `generate_cta.py` to build `discord.png` and `wiki.png`.

| File | What it is | Used for |
| --- | --- | --- |
| `discord.svg` / `discord-white.png` | Discord mark (Clyde), white | Badge of the Discord banner |
| `github.svg` / `github-dark.png` | GitHub mark (Octocat), dark `#0d1117` | Badge of the wiki banner |
| `github-white.png` | GitHub mark, white | Spare, for a dark badge |

## Source and licence

- The vector files come from [simple-icons](https://github.com/simple-icons/simple-icons), which
  packages the official brand glyphs and releases the files under **CC0-1.0**.
- The marks themselves are trademarks of **Discord Inc.** and **GitHub Inc.** They are used here
  only to identify the platform a link points to, in the monochrome variants both brands allow for
  this purpose, and their use does not imply any endorsement or affiliation.

## Regenerating the PNGs

The SVGs are rasterised with [`@resvg/resvg-js`](https://github.com/thx/resvg-js) at 512 px with a
transparent background:

```bash
mkdir -p /tmp/jpv-icons && cd /tmp/jpv-icons
npm install @resvg/resvg-js
# rasterize.js from this folder, adjusted to the absolute path of docs/assets/icons
node rasterize.js
```

```javascript
const svg = fs.readFileSync(path.join(DIR, 'discord.svg'), 'utf8')
  .replace('<svg ', '<svg fill="#ffffff" ');      // white variant
const png = new Resvg(svg, {
  fitTo: { mode: 'width', value: 512 },
  background: 'rgba(0,0,0,0)',
}).render().asPng();
```

Then rebuild the banners:

```bash
python docs/assets/generate_cta.py
```

If the PNGs are ever missing, `generate_cta.py` falls back to the hand-drawn glyphs, so the script
never fails on a clean checkout.
