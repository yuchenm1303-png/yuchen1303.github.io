const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const RES_ROOTS = [
  path.join(ROOT, 'android', 'app', 'src', 'main', 'res'),
].filter((dir) => fs.existsSync(dir));

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function write(resRoot, relativePath, content) {
  const file = path.join(resRoot, relativePath);
  ensureDir(path.dirname(file));
  fs.writeFileSync(file, content.trimStart(), 'utf8');
}

const foreground = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:fillColor="#061038" android:pathData="M0,0 L108,0 L108,108 L0,108 Z" />
    <path android:fillColor="#244CFF" android:fillAlpha="0.34" android:pathData="M0,0 L42,0 C18,14 5,32 0,62 Z" />
    <path android:fillColor="#FF936C" android:fillAlpha="0.32" android:pathData="M108,36 C100,46 95,55 93,67 C101,64 106,62 108,61 Z" />
    <path android:fillColor="#4F2E6E" android:fillAlpha="0.44" android:pathData="M60,0 L108,0 L108,108 L66,108 C71,72 70,34 60,0 Z" />

    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.92" android:pathData="M13,18 m-0.55,0 a0.55,0.55 0,1 0,1.1 0 a0.55,0.55 0,1 0,-1.1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.92" android:pathData="M23,11 m-0.45,0 a0.45,0.45 0,1 0,0.9 0 a0.45,0.45 0,1 0,-0.9 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M35,20 m-0.55,0 a0.55,0.55 0,1 0,1.1 0 a0.55,0.55 0,1 0,-1.1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M50,12 m-0.50,0 a0.50,0.50 0,1 0,1 0 a0.50,0.50 0,1 0,-1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.92" android:pathData="M67,19 m-0.55,0 a0.55,0.55 0,1 0,1.1 0 a0.55,0.55 0,1 0,-1.1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M83,14 m-0.50,0 a0.50,0.50 0,1 0,1 0 a0.50,0.50 0,1 0,-1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.92" android:pathData="M95,27 m-0.55,0 a0.55,0.55 0,1 0,1.1 0 a0.55,0.55 0,1 0,-1.1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.86" android:pathData="M17,41 m-0.45,0 a0.45,0.45 0,1 0,0.9 0 a0.45,0.45 0,1 0,-0.9 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M31,34 m-0.50,0 a0.50,0.50 0,1 0,1 0 a0.50,0.50 0,1 0,-1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.88" android:pathData="M48,37 m-0.48,0 a0.48,0.48 0,1 0,0.96 0 a0.48,0.48 0,1 0,-0.96 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M68,35 m-0.50,0 a0.50,0.50 0,1 0,1 0 a0.50,0.50 0,1 0,-1 0" />
    <path android:fillColor="#FFFFFFFF" android:fillAlpha="0.90" android:pathData="M86,42 m-0.50,0 a0.50,0.50 0,1 0,1 0 a0.50,0.50 0,1 0,-1 0" />

    <path android:strokeColor="#EEFFFFFF" android:strokeWidth="0.8" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M43,22 L43,28 M40,25 L46,25" />
    <path android:strokeColor="#DDFFFFFF" android:strokeWidth="0.65" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M26,49 L26,54 M23.5,51.5 L28.5,51.5" />
    <path android:strokeColor="#CCFFFFFF" android:strokeWidth="0.55" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M71,51 L71,55 M69,53 L73,53" />

    <path android:fillColor="#1C74FF" android:pathData="M-7,66 C13,56 31,53 52,64 C67,72 80,75 96,67 C103,64 107,62 115,59 L115,82 L-7,82 Z" />
    <path android:fillColor="#8B67F2" android:fillAlpha="0.88" android:pathData="M31,57 C39,57 46,59 52,64 C67,72 80,75 96,67 C103,64 107,62 115,59 L115,82 L31,82 Z" />
    <path android:fillColor="#FFAD6C" android:fillAlpha="0.82" android:pathData="M63,70 C75,75 86,72 96,67 C103,64 107,62 115,59 L115,82 L63,82 Z" />
    <path android:strokeColor="#DDFAD8C4" android:strokeWidth="0.9" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M-7,66 C13,56 31,53 52,64 C67,72 80,75 96,67 C103,64 107,62 115,59" />

    <path android:fillColor="#041455" android:pathData="M-7,84 C13,73 31,71 52,80 C67,87 78,93 96,84 C103,81 108,78 115,76 L115,116 L-7,116 Z" />
    <path android:fillColor="#5A345F" android:fillAlpha="0.78" android:pathData="M63,86 C75,93 87,90 96,84 C103,81 108,78 115,76 L115,116 L63,116 Z" />
    <path android:strokeColor="#668CC7FF" android:strokeWidth="0.45" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M-7,84 C13,73 31,71 52,80 C67,87 78,93 96,84 C103,81 108,78 115,76" />

    <path android:strokeColor="#88B8EAFF" android:strokeWidth="1.3" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M3,15 C3,7 7,3 15,3" />
    <path android:strokeColor="#66FFD0A0" android:strokeWidth="1.1" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M105,16 C105,8 100,3 92,3" />
    <path android:strokeColor="#66FFFFFF" android:strokeWidth="0.55" android:fillColor="#00000000" android:pathData="M16,2 L92,2 C101,2 106,7 106,16 L106,92 C106,101 101,106 92,106 L16,106 C7,106 2,101 2,92 L2,16 C2,7 7,2 16,2 Z" />
</vector>`;

const background = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#061038" />
</shape>`;

const backgroundColor = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#061038</color>
</resources>`;

const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>`;

for (const resRoot of RES_ROOTS) {
  write(resRoot, 'drawable/ic_launcher_foreground.xml', foreground);
  write(resRoot, 'drawable-v24/ic_launcher_foreground.xml', foreground);
  write(resRoot, 'drawable/ic_launcher_background.xml', background);
  write(resRoot, 'drawable/assistant_icon_foreground.xml', foreground);
  write(resRoot, 'drawable/assistant_icon_background.xml', background);
  write(resRoot, 'values/ic_launcher_background.xml', backgroundColor);
  write(resRoot, 'mipmap-anydpi-v26/ic_launcher.xml', adaptive);
  write(resRoot, 'mipmap-anydpi-v26/ic_launcher_round.xml', adaptive);
}

console.log('[launcher-icon] Installed full-bleed starry glass launcher icon without black frame.');
