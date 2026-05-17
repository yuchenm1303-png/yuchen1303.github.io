const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const RES = path.join(ROOT, 'android', 'app', 'src', 'main', 'res');
const ASSETS = path.join(ROOT, 'assets');
const DENSITY_SIZES = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
};
const PREFERRED_ICON = path.join(ASSETS, 'launcher-icon.png');
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const CROP_PERCENT = Math.max(60, Math.min(100, Number(process.env.LAUNCHER_ICON_CROP_PERCENT || 78)));

function rm(file) {
  try { fs.rmSync(file, { force: true }); } catch {}
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', stdio: options.stdio || 'pipe' });
  if (result.status !== 0) {
    const msg = [result.stderr, result.stdout].filter(Boolean).join('\n').trim();
    throw new Error(msg || `${command} failed`);
  }
  return result.stdout || '';
}

function commandExists(command) {
  const result = spawnSync(command, ['-version'], { encoding: 'utf8', stdio: 'pipe' });
  return result.status === 0;
}

function findImageMagick() {
  if (commandExists('magick')) return { command: 'magick', prefix: [] };
  if (commandExists('convert')) return { command: 'convert', prefix: [] };
  throw new Error('[launcher-icon] ImageMagick is required to crop and resize launcher icons. Install imagemagick before building.');
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

function readImageSize(sourceIcon, imageTool) {
  const identifyCommand = imageTool.command === 'magick' ? 'magick' : 'identify';
  const identifyArgs = imageTool.command === 'magick'
    ? ['identify', '-format', '%w %h', sourceIcon]
    : ['-format', '%w %h', sourceIcon];
  const output = run(identifyCommand, identifyArgs).trim();
  const [width, height] = output.split(/\s+/).map(Number);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    throw new Error(`[launcher-icon] Cannot read image size from ${sourceIcon}`);
  }
  return { width, height };
}

function renderIcon(sourceIcon, outputFile, size, cropSize, imageTool) {
  const commonArgs = [
    sourceIcon,
    '-auto-orient',
    '-gravity', 'center',
    '-crop', `${cropSize}x${cropSize}+0+0`,
    '+repage',
    '-resize', `${size}x${size}`,
    '-strip',
    outputFile,
  ];
  const args = imageTool.command === 'magick' ? commonArgs : commonArgs;
  run(imageTool.command, args, { stdio: 'pipe' });
}

const sourceIcon = findUploadedPng();
const imageTool = findImageMagick();
const { width, height } = readImageSize(sourceIcon, imageTool);
const base = Math.min(width, height);
const cropSize = Math.max(1, Math.round(base * CROP_PERCENT / 100));

for (const [dir, size] of Object.entries(DENSITY_SIZES)) {
  const full = path.join(RES, dir);
  fs.mkdirSync(full, { recursive: true });

  rm(path.join(full, 'ic_launcher.jpg'));
  rm(path.join(full, 'ic_launcher_round.jpg'));
  rm(path.join(full, 'ic_launcher.webp'));
  rm(path.join(full, 'ic_launcher_round.webp'));

  renderIcon(sourceIcon, path.join(full, 'ic_launcher.png'), size, cropSize, imageTool);
  renderIcon(sourceIcon, path.join(full, 'ic_launcher_round.png'), size, cropSize, imageTool);
}

rm(path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'));
rm(path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher_round.xml'));

console.log(`[launcher-icon] Installed cropped launcher icon from ${path.relative(ROOT, sourceIcon)} at ${CROP_PERCENT}% center crop.`);
