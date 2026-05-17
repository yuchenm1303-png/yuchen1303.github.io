const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const RES = path.join(ROOT, 'android', 'app', 'src', 'main', 'res');
const DENSITIES = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'];
const PREFERRED_ICON = path.join(ROOT, 'assets', 'launcher-icon.png');

function rm(file) {
  try { fs.rmSync(file, { force: true }); } catch {}
}

function findUploadedPng() {
  if (fs.existsSync(PREFERRED_ICON)) return PREFERRED_ICON;

  const candidates = fs.readdirSync(ROOT)
    .filter((name) => name.toLowerCase().endsWith('.png'))
    .map((name) => path.join(ROOT, name))
    .filter((file) => fs.statSync(file).isFile())
    .sort((a, b) => fs.statSync(b).size - fs.statSync(a).size);

  if (candidates.length) return candidates[0];

  throw new Error([
    '[launcher-icon] Missing launcher icon image.',
    'Please upload the original PNG to ai-ledger-android/assets/launcher-icon.png',
    'or place a PNG directly under ai-ledger-android/.',
  ].join(' '));
}

const sourceIcon = findUploadedPng();
const iconBytes = fs.readFileSync(sourceIcon);

for (const dir of DENSITIES) {
  const full = path.join(RES, dir);
  fs.mkdirSync(full, { recursive: true });

  rm(path.join(full, 'ic_launcher.jpg'));
  rm(path.join(full, 'ic_launcher_round.jpg'));
  rm(path.join(full, 'ic_launcher.webp'));
  rm(path.join(full, 'ic_launcher_round.webp'));

  fs.writeFileSync(path.join(full, 'ic_launcher.png'), iconBytes);
  fs.writeFileSync(path.join(full, 'ic_launcher_round.png'), iconBytes);
}

rm(path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'));
rm(path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher_round.xml'));

console.log(`[launcher-icon] Installed exact launcher icon from ${path.relative(ROOT, sourceIcon)}`);
