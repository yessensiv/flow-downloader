import { mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';

const asset = process.platform === 'win32' ? 'yt-dlp.exe' : process.platform === 'darwin' ? 'yt-dlp_macos' : 'yt-dlp_linux';
const release = await fetch('https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest').then(r => { if (!r.ok) throw new Error(`GitHub: ${r.status}`); return r.json(); });
async function download(name) {
  const item = release.assets.find(a => a.name === name);
  if (!item) throw new Error(`Release asset missing: ${name}`);
  const response = await fetch(item.browser_download_url);
  if (!response.ok) throw new Error(`Download failed: ${response.status}`);
  return Buffer.from(await response.arrayBuffer());
}
const [binary, checksums] = await Promise.all([download(asset), download('SHA2-256SUMS')]);
const expected = checksums.toString().split('\n').find(line => line.trim().endsWith(` ${asset}`))?.split(/\s+/)[0];
if (!expected || createHash('sha256').update(binary).digest('hex') !== expected) throw new Error('SHA256 verification failed');
await mkdir('.tools', { recursive: true });
await writeFile(path.join('.tools', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp'), binary, { mode: 0o755 });
console.log(`Installed yt-dlp ${release.tag_name}; SHA256 verified. Node.js is used for YouTube JavaScript challenges.`);
