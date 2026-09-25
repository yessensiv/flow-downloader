import { spawn, execFile } from 'node:child_process';
import { mkdir, readdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { analyzeVideo, AnalysisError, classifyError } from './analyzer';

const root = path.join(process.cwd(), '.downloads');
const ttl = 60 * 60 * 1000;
const limit = 2 * 1024 ** 3;
type Job = { id: string; state: 'checking' | 'downloading' | 'merging' | 'ready' | 'error'; progress: number | null; title?: string; error?: string; file?: string; size?: number; expires: number; readers: number };
const shared = globalThis as typeof globalThis & { flowDownloads?: Map<string, Job>; flowCleanup?: ReturnType<typeof setInterval> };
const jobs = shared.flowDownloads ??= new Map<string, Job>();
export function getJob(id: string) { return jobs.get(id); }
export function jobView(job: Job) { return { id: job.id, state: job.state, progress: job.progress, title: job.title, error: job.error, size: job.size, expires: job.expires }; }
async function cleanup() {
  await mkdir(root, { recursive: true });
  for (const [id, job] of jobs) {
    if (['ready', 'error'].includes(job.state) && !job.readers && job.expires < Date.now()) {
      await rm(path.join(root, id), { recursive: true, force: true });
      jobs.delete(id);
    }
  }
  for (const entry of await readdir(root, { withFileTypes: true })) {
    if (!entry.isDirectory() || !/^[a-f0-9-]{36}$/.test(entry.name)) continue;
    const job = jobs.get(entry.name);
    if (job && (job.readers || !['ready', 'error'].includes(job.state) || job.expires > Date.now())) continue;
    const dir = path.join(root, entry.name);
    if (!job && Date.now() - (await stat(dir)).mtimeMs < ttl) continue;
    await rm(dir, { recursive: true, force: true });
    jobs.delete(entry.name);
  }
}
if (!shared.flowCleanup) {
  shared.flowCleanup = setInterval(() => { void cleanup().catch(() => {}); }, 60_000);
  shared.flowCleanup.unref();
}
export function createDownload(videoId: string, optionId: string) {
  if ([...jobs.values()].filter(j => !['ready', 'error'].includes(j.state)).length >= 2)
    throw new AnalysisError('BUSY', 'Уже готовим два файла. Попробуйте немного позже.', 429);
  const job: Job = { id: randomUUID(), state: 'checking', progress: null, expires: Date.now() + ttl, readers: 0 };
  jobs.set(job.id, job);
  void run(job, videoId, optionId);
  return jobView(job);
}
async function run(job: Job, videoId: string, optionId: string) {
  const dir = path.join(root, job.id);
  try {
    await cleanup();
    const media = await analyzeVideo(videoId);
    const choice = media.options.find(o => o.id === optionId && o.kind === 'video');
    if (!choice || !/^[\w-]+(?:\+[\w-]+)?$/.test(choice.id)) throw new Error('Этот вариант уже недоступен. Найдите видео заново.');
    if (choice.size && choice.size > limit) throw new Error('Файл больше 2 ГБ. Выберите качество ниже.');
    const ffmpeg = process.env.FFMPEG_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'ffmpeg.exe' : 'ffmpeg');
    if (choice.needsMerge) await new Promise<void>((resolve, reject) => execFile(ffmpeg, ['-version'], { windowsHide: true, timeout: 5000 }, error => error ? reject(new Error('На сервере не установлен FFmpeg для объединения звука.')) : resolve()));
    job.title = `${media.title} · ${choice.label} · ${choice.container}`;
    await mkdir(dir, { recursive: true });
    job.state = 'downloading';
    const binary = process.env.YTDLP_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');
    await new Promise<void>((resolve, reject) => {
      const child = spawn(binary, ['--ignore-config', '--no-playlist', '--no-cache-dir', '--no-js-runtimes', '--js-runtimes', `node:${process.execPath}`, '--no-simulate', '--newline', '--progress', '--progress-template', 'download:FLOW:%(progress._percent_str)s', '--progress-template', 'postprocess:FLOW_MERGE', '--socket-timeout', '20', '--retries', '2', '--fragment-retries', '2', '--max-filesize', String(limit), '--ffmpeg-location', ffmpeg, '--merge-output-format', choice.container.toLowerCase(), '-f', choice.id, '-o', path.join(dir, 'video.%(ext)s'), '--', `https://www.youtube.com/watch?v=${videoId}`], { windowsHide: true });
      let failure: Error | undefined;
      let stderr = '';
      let pending = '';
      const timeout = setTimeout(() => { failure = new Error('Подготовка заняла больше 30 минут. Попробуйте качество ниже.'); child.kill(); }, 30 * 60_000);
      const quota = setInterval(() => { void (async () => {
        const files = await readdir(dir);
        const sizes = await Promise.all(files.map(f => stat(path.join(dir, f)).then(s => s.size).catch(() => 0)));
        if (sizes.reduce((a,b) => a+b, 0) > limit * 2) { failure = new Error('Превышен лимит временных файлов. Выберите качество ниже.'); child.kill(); }
      })().catch(() => {}); }, 2000);
      child.stdout.on('data', chunk => {
        pending += chunk.toString();
        const lines = pending.split(/\r?\n/); pending = lines.pop()!.slice(-4096);
        for (const line of lines) {
          if (line.includes('FLOW_MERGE') || line.includes('[Merger]')) { job.state = 'merging'; job.progress = null; }
          const match = /FLOW:\s*([\d.]+)%/.exec(line);
          if (match && job.state !== 'merging') job.progress = Math.min(100, Number(match[1]));
        }
      });
      child.stderr.on('data', chunk => { stderr = (stderr + chunk.toString()).slice(-8192); });
      child.on('error', () => { failure = new Error('Не удалось запустить обработчик скачивания.'); });
      child.on('close', code => { clearTimeout(timeout); clearInterval(quota); if (failure) reject(failure); else if (code !== 0) reject(classifyError(stderr)); else resolve(); });
    });
    const file = path.join(dir, `video.${choice.container.toLowerCase()}`);
    const info = await stat(file).catch(() => { throw new Error('Файл не создан. Возможно, превышен лимит размера или формат недоступен.'); });
    if (!info.size || info.size > limit) throw new Error('Не удалось подготовить файл в пределах 2 ГБ. Выберите качество ниже.');
    job.file = file; job.size = info.size; job.state = 'ready'; job.progress = 100;
  } catch (error) {
    job.state = 'error'; job.error = error instanceof Error ? error.message : 'Не удалось подготовить файл.';
    await rm(dir, { recursive: true, force: true }).catch(() => {});
  } finally { job.expires = Date.now() + ttl; }
}
