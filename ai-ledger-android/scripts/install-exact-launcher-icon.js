const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const RES = path.join(ROOT, 'android', 'app', 'src', 'main', 'res');
const ASSETS = path.join(ROOT, 'assets');
const DENSITIES = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'];
const PREFERRED_ICON = path.join(ASSETS, 'launcher-icon.png');
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

function rm(file) {
  try { fs.rmSync(file, { force: true }); } catch {}
}

function isValidPng(file) {
  try {
    const stat = fs.statSync(file);
    if (!stat.isFile() || stat.size < 1024) return false;
    const fd = fs.openSync(file, 'r');
    const header = Buffer.alloc(PNG_SIGNATURE.length);
    fs.readSync(fd, header, 0, PNG_SIGNATURE.length, 0);
    fs.closeSync(fd);
    return header.equals(PNG_SIGNATURE);
  } catch {
    return false;
  }
}

function pngFilesIn(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((name) => name.toLowerCase().endsWith('.png'))
    .map((name) => path.join(dir, name))
    .filter((file) => fs.statSync(file).isFile());
}

function findUploadedPng() {
  if (fs.existsSync(PREFERRED_ICON) && isValidPng(PREFERRED_ICON)) return PREFERRED_ICON;

  const candidates = [
    ...pngFilesIn(ASSETS),
    ...pngFilesIn(ROOT),
  ]
    .filter(isValidPng)
    .sort((a, b) => fs.statSync(b).size - fs.statSync(a).size);

  if (candidates.length) return candidates[0];

  throw new Error([
    '[launcher-icon] No valid PNG launcher image found.',
    'Upload the original icon as ai-ledger-android/assets/launcher-icon.png.',
    'The file must be a real PNG image, not an empty placeholder or failed GitHub upload.',
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