import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';
import { stopProcessTree } from '../lib/process-tree.ts';

test('cancellation stops the worker and its subprocess', { timeout: 15000 }, async () => {
  const source = `const {spawn}=require('node:child_process'); const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{windowsHide:true,stdio:'ignore'}); console.log(child.pid); setInterval(()=>{},1000);`;
  const parent = spawn(process.execPath, ['-e', source], { detached: process.platform !== 'win32', windowsHide: true });
  const closed = once(parent, 'close');
  let pid;
  try {
    const [chunk] = await once(parent.stdout, 'data'); pid = Number(chunk.toString().trim());
    assert.ok(pid > 0);
    await stopProcessTree(parent); await closed;
    let alive = true;
    for (let attempt = 0; attempt < 20; attempt++) {
      try { process.kill(pid, 0); } catch { alive = false; break; }
      await delay(50);
    }
    assert.equal(alive, false, 'worker subprocess must not survive cancellation');
  } finally {
    await stopProcessTree(parent);
    if (pid) { try { process.kill(pid, 'SIGKILL'); } catch {} }
  }
});
