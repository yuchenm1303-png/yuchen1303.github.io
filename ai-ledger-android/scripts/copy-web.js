const fs = require('fs');
const path = require('path');

const sourceDir = path.resolve(__dirname, '../../ai-ledger');
const targetDir = path.resolve(__dirname, '../www');

function copyDir(src, dest) {
  if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

if (!fs.existsSync(sourceDir)) {
  console.error(`Web source directory not found: ${sourceDir}`);
  process.exit(1);
}

fs.rmSync(targetDir, { recursive: true, force: true });
copyDir(sourceDir, targetDir);
console.log(`Copied web assets from ${sourceDir} to ${targetDir}`);
