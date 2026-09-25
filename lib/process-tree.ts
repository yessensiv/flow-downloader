import { execFile, type ChildProcess } from 'node:child_process';
export async function stopProcessTree(child: ChildProcess) {
  if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
  if (process.platform === 'win32') {
    await new Promise<void>((resolve, reject) => {
      execFile('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 10_000 }, error => {
        if (error && child.exitCode === null && child.signalCode === null) reject(new Error('Не удалось остановить обработку. Попробуйте отменить ещё раз.'));
        else resolve();
      });
    });
  } else {
    try { process.kill(-child.pid, 'SIGKILL'); } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ESRCH') throw error; }
  }
}
