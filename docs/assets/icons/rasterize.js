// Rasterise the official Discord and GitHub marks to transparent PNGs.
//
// Needs @resvg/resvg-js:  npm install @resvg/resvg-js
// Run from this folder:   node rasterize.js
const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const DIR = __dirname;

const jobs = [
  ['discord.svg', 'discord-white.png', '#ffffff'],
  ['github.svg', 'github-white.png', '#ffffff'],
  ['github.svg', 'github-dark.png', '#0d1117'],
];

for (const [input, output, color] of jobs) {
  let svg = fs.readFileSync(path.join(DIR, input), 'utf8');
  svg = svg.replace('<svg ', `<svg fill="${color}" `);
  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: 512 },
    background: 'rgba(0,0,0,0)',
  });
  const png = resvg.render().asPng();
  fs.writeFileSync(path.join(DIR, output), png);
  console.log(output, png.length, 'bytes');
}
