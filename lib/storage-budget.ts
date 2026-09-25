import { readdir, stat, statfs } from 'node:fs/promises';
import path from 'node:path';
export const GiB = 1024 ** 3;
export const storageLimit = 10 * GiB;
export const jobReservation = 4 * GiB;
export function storageFits(used: number, reservedRemaining: number, free: number, reservation = jobReservation) {
  return used + reservedRemaining + reservation <= storageLimit && free >= reservedRemaining + reservation + GiB;
}
export async function storageUsage(root: string) {
  const sizes = new Map<string, number>();
  for (const dir of await readdir(root, { withFileTypes: true })) {
    if (!dir.isDirectory() || !/^[a-f0-9-]{36}$/.test(dir.name)) continue;
    let size = 0;
    for (const file of await readdir(path.join(root, dir.name), { withFileTypes: true }).catch(error => { if (error.code === 'ENOENT') return []; throw error; })) {
      if (!file.isFile()) continue;
      size += await stat(path.join(root, dir.name, file.name)).then(s => s.size).catch(error => { if (error.code === 'ENOENT') return 0; throw error; });
    }
    sizes.set(dir.name, size);
  }
  const fs = await statfs(root);
  return { sizes, used: [...sizes.values()].reduce((sum, size) => sum + size, 0), free: fs.bavail * fs.bsize };
}
