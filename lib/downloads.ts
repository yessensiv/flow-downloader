import { spawn, execFile } from 'node:child_process';
import { mkdir, readdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { analyzeVideo, AnalysisError, classifyError } from './analyzer';
import { downloadFormat } from './download-format';
import { stopProcessTree } from './process-tree';
import { trackProgress } from './download-progress';
import { storageUsage, storageFits, jobReservation, storageLimit, GiB } from './storage-budget';

const root = path.join(process.cwd(), '.downloads');
const ttl = 60 * 60 * 1000;
const limit = 2 * 1024 ** 3;
type Job = { id: string; state: 'cancelled' | 'cancelling' | 'checking' | 'downloading' | 'merging' | 'converting' | 'ready' | 'error'; progress: number | null; title?: string; error?: string; file?: string; mime?: string; ext?: string; kind?: 'video' | 'audio'; size?: number; expires: number; readers: number; cancelRequested?: boolean; stop?: () => Promise<void>; done?: Promise<void> };
const shared = globalThis as typeof globalThis & { flowDownloads?: Map<string, Job>; flowCleanup?: ReturnType<typeof setInterval>; flowAdmission?: Promise<void>; flowReservations?: Set<string> };
const jobs = shared.flowDownloads ??= new Map<string, Job>();
const reservations = shared.flowReservations ??= new Set<string>();
export function getJob(id: string) { return jobs.get(id); }
export function jobView(job: Job) { return { id: job.id, state: job.state, progress: job.progress, title: job.title, error: job.error, kind: job.kind, size: job.size, expires: job.expires }; }
async function cleanup() {
  await mkdir(root, { recursive: true });
  for (const [id, job] of jobs) {
    if (['ready', 'error', 'cancelled'].includes(job.state) && !job.readers && job.expires < Date.now()) {
      await rm(path.join(root, id), { recursive: true, force: true });
      jobs.delete(id);
    }
  }
  for (const entry of await readdir(root, { withFileTypes: true })) {
    if (!entry.isDirectory() || !/^[a-f0-9-]{36}$/.test(entry.name)) continue;
    const job = jobs.get(entry.name);
    if (job && (job.readers || !['ready', 'error', 'cancelled'].includes(job.state) || job.expires > Date.now())) continue;
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
export async function createDownload(videoId: string, optionId: string) {
  const previous = shared.flowAdmission ?? Promise.resolve();
  let release!: () => void;
  shared.flowAdmission = new Promise<void>(resolve => { release = resolve; });
  await previous;
  try {
    await cleanup();
    if ([...jobs.values()].filter(j => !['ready', 'error', 'cancelled'].includes(j.state)).length >= 2)
      throw new AnalysisError('BUSY', 'Уже готовим два файла. Попробуйте немного позже.', 429);
    const usage = await storageUsage(root).catch(() => { throw new AnalysisError('STORAGE_UNAVAILABLE', 'Не удалось проверить свободное место. Попробуйте позже.', 503); });
    const remaining = [...reservations].reduce((sum, id) => sum + Math.max(0, jobReservation - (usage.sizes.get(id) || 0)), 0);
    if (!storageFits(usage.used, remaining, usage.free)) throw new AnalysisError('STORAGE_FULL', 'Недостаточно свободного места для нового файла. Попробуйте позже.', 503);
    const job: Job = { id: randomUUID(), state: 'checking', progress: null, expires: Date.now() + ttl, readers: 0 };
    jobs.set(job.id, job);
    reservations.add(job.id);
    job.done = run(job, videoId, optionId);
    return jobView(job);
  } finally { release(); }
}
async function run(job: Job, videoId: string, optionId: string) {
  const dir = path.join(root, job.id);
  try {
    await cleanup();
    if (job.cancelRequested) throw new Error('Отменено');
    const media = await analyzeVideo(videoId, undefined, child => { job.stop = () => stopProcessTree(child); });
    job.stop = undefined;
    if (job.cancelRequested) throw new Error('Отменено');
    const choice = media.options.find(o => o.id === optionId);
    if (!choice) throw new Error('Этот вариант уже недоступен. Найдите видео заново.');
    const output = downloadFormat(choice);
    job.kind = choice.kind; job.mime = output.mime; job.ext = output.ext;
    if (choice.size && choice.size > limit) throw new Error('Файл больше 2 ГБ. Выберите качество ниже.');
    const ffmpeg = process.env.FFMPEG_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'ffmpeg.exe' : 'ffmpeg');
    if (choice.needsMerge || choice.conversion) await new Promise<void>((resolve, reject) => execFile(ffmpeg, ['-version'], { windowsHide: true, timeout: 5000 }, error => error ? reject(new Error('На сервере не установлен FFmpeg для обработки звука.')) : resolve()));
    const qualityLabel = choice.kind === 'audio' ? choice.label.replace(/^(mp4a[^·]*|opus)\s*·\s*/i, '').replace(' · конвертация', '') : choice.label;
    job.title = `${media.title} · ${qualityLabel} · ${choice.container}`;
    await mkdir(dir, { recursive: true });
    if (job.cancelRequested) throw new Error('Отменено');
    job.state = 'downloading';
    const binary = process.env.YTDLP_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');
    await new Promise<void>((resolve, reject) => {
      const child = spawn(binary, ['--ignore-config', '--no-playlist', '--no-cache-dir', '--no-js-runtimes', '--js-runtimes', `node:${process.execPath}`, '--no-simulate', '--newline', '--progress', '--progress-template', 'download:FLOW:%(info.format_id)s:%(progress._percent_str)s', '--progress-template', 'postprocess:FLOW_MERGE', '--socket-timeout', '20', '--retries', '2', '--fragment-retries', '2', '--max-filesize', String(limit), '--ffmpeg-location', ffmpeg, ...output.args, '-o', path.join(dir, 'media.%(ext)s'), '--', `https://www.youtube.com/watch?v=${videoId}`], { windowsHide: true, detached: process.platform !== 'win32' });
      job.stop = () => stopProcessTree(child);
      let failure: Error | undefined;
      let stderr = '';
      let pending = '';
      const parts = new Map<string, number>();
      const timeout = setTimeout(() => { failure = new Error('Подготовка заняла больше 30 минут. Попробуйте качество ниже.'); void stopProcessTree(child).catch(() => {}); }, 30 * 60_000);
      let checkingQuota = false;
      const quota = setInterval(() => {
        if (checkingQuota) return;
        checkingQuota = true;
        void (async () => {
          const usage = await storageUsage(root);
          if ((usage.sizes.get(job.id) || 0) > jobReservation || usage.used > storageLimit || usage.free < GiB / 2) {
            failure = new Error('Недостаточно места для продолжения. Выберите качество ниже или попробуйте позже.');
            await stopProcessTree(child);
          }
        })().catch(() => { failure = new Error('Не удалось проверить свободное место. Повторите попытку позже.'); void stopProcessTree(child).catch(() => {}); }).finally(() => { checkingQuota = false; });
      }, 2000);
      child.stdout.on('data', chunk => {
        if (job.cancelRequested) return;
        pending += chunk.toString();
        const lines = pending.split(/\r?\n/); pending = lines.pop()!.slice(-4096);
        for (const line of lines) {
          if (line.includes('FLOW_MERGE') || line.includes('[Merger]') || line.includes('[ExtractAudio]')) { job.state = choice.conversion ? 'converting' : 'merging'; job.progress = null; }
          const match = /FLOW:([^:]+):\s*([\d.]+)%/.exec(line);
          if (match && job.state === 'downloading') job.progress = trackProgress(parts, match[1], Number(match[2]), choice.needsMerge ? 2 : 1);
        }
      });
      child.stderr.on('data', chunk => { stderr = (stderr + chunk.toString()).slice(-8192); });
      child.on('error', () => { failure = new Error('Не удалось запустить обработчик скачивания.'); });
      child.on('close', code => { clearTimeout(timeout); clearInterval(quota); if (failure) reject(failure); else if (code !== 0) reject(classifyError(stderr)); else resolve(); });
    });
    job.stop = undefined;
    if (job.cancelRequested) throw new Error('Отменено');
    const file = path.join(dir, `media.${output.ext}`);
    const info = await stat(file).catch(() => { throw new Error('Файл не создан. Возможно, превышен лимит размера или формат недоступен.'); });
    if (!info.size || info.size > limit) throw new Error('Не удалось подготовить файл в пределах 2 ГБ. Выберите качество ниже.');
    if (job.cancelRequested) throw new Error('Отменено');
    job.file = file; job.size = info.size; job.state = 'ready'; job.progress = 100;
  } catch (error) {
    job.state = job.cancelRequested ? 'cancelled' : 'error'; job.progress = null;
    job.error = job.cancelRequested ? undefined : error instanceof Error ? error.message : 'Не удалось подготовить файл.';
    await rm(dir, { recursive: true, force: true }).catch(() => {});
  } finally { reservations.delete(job.id); job.stop = undefined; job.expires = Date.now() + ttl; }
}
export async function cancelDownload(id: string) {
  const job = jobs.get(id);
  if (!job || job.expires < Date.now()) return null;
  if (['ready', 'error', 'cancelled'].includes(job.state)) return jobView(job);
  job.cancelRequested = true; job.state = 'cancelling'; job.progress = null;
  await job.stop?.();
  await job.done;
  return jobView(job);
}
