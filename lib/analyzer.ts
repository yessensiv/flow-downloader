import { spawn, type ChildProcess } from 'node:child_process';
import path from 'node:path';
import { stopProcessTree } from './process-tree';
import { normalizeMedia, type RawInfo } from './media';

export class AnalysisError extends Error {
  constructor(public code: string, message: string, public status = 502) { super(message); }
}
export function classifyError(stderr: string): AnalysisError {
  if (/private video|video is private/i.test(stderr)) return new AnalysisError('PRIVATE', 'Это приватное видео. Нужен ролик, доступный без входа в аккаунт.', 422);
  if (/sign in|confirm your age|age.restricted|not a bot|login required/i.test(stderr)) return new AnalysisError('AUTH_REQUIRED', 'YouTube требует вход или проверку доступа. Этот ролик сейчас не удалось проанализировать.', 422);
  if (/unavailable|removed|not available|copyright|does not exist/i.test(stderr)) return new AnalysisError('UNAVAILABLE', 'Видео удалено или недоступно из региона сервера.', 422);
  if (/429|too many requests/i.test(stderr)) return new AnalysisError('UPSTREAM_LIMIT', 'YouTube временно ограничил запросы. Попробуйте позже.', 503);
  return new AnalysisError('UPSTREAM_ERROR', 'Не удалось получить форматы с YouTube. Попробуйте позже или другую ссылку.');
}
// Isolated child process for local MVP. Move behind a queue for multi-instance deployment.
const analyzerState = globalThis as typeof globalThis & { flowAnalyzers?: number };
export async function analyzeVideo(id: string, signal?: AbortSignal, onProcess?: (child: ChildProcess) => void) {
  if (!/^[a-zA-Z0-9_-]{11}$/.test(id)) throw new AnalysisError('INVALID_URL', 'Некорректный идентификатор видео.',400);
  const binary = process.env.YTDLP_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');
  if ((analyzerState.flowAnalyzers || 0) >= 2) throw new AnalysisError('BUSY', 'Сервис проверяет другие ссылки. Попробуйте чуть позже.', 429);
  analyzerState.flowAnalyzers = (analyzerState.flowAnalyzers || 0) + 1;
  const raw = await new Promise<RawInfo>((resolve, reject) => {
    if (signal?.aborted) { reject(new AnalysisError('TIMEOUT', 'Запрос отменён.', 499)); return; }
    const child = spawn(binary, ['--ignore-config','--no-playlist','--skip-download','--dump-single-json','--no-progress','--no-warnings','--no-cache-dir','--no-js-runtimes','--js-runtimes',`node:${process.execPath}`,'--socket-timeout','15','--retries','1','--extractor-retries','1','--',`https://www.youtube.com/watch?v=${id}`], { windowsHide: true, detached: process.platform !== 'win32' });
    let output = ''; let stderr = ''; let bytes = 0; let failure: Error | undefined;
    const stop = () => { void stopProcessTree(child).catch(() => {}); };
    const abort = () => { failure = new AnalysisError('TIMEOUT', 'Запрос отменён.', 499); stop(); };
    const timeout = setTimeout(() => { failure = new AnalysisError('TIMEOUT', 'Анализ занял слишком много времени. Попробуйте ещё раз.', 504); stop(); }, 60_000);
    signal?.addEventListener('abort', abort, { once: true });
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', chunk => {
      bytes += Buffer.byteLength(chunk);
      if (bytes > 8 * 1024 * 1024) { failure = new AnalysisError('INVALID_RESPONSE', 'Ответ источника слишком большой.'); stop(); return; }
      output += chunk.toString();
    });
    child.stderr.on('data', chunk => { stderr = (stderr + chunk.toString()).slice(-8192); });
    child.on('error', error => {
      console.error('[analyze] failed to start yt-dlp', { id, error: error.message });
      failure = new AnalysisError('NOT_CONFIGURED', 'Обработчик видео ещё не установлен на сервере.', 503);
    });
    child.on('close', code => {
      clearTimeout(timeout); signal?.removeEventListener('abort', abort);
      if (failure) return reject(failure);
      if (code !== 0) {
        // Keep the provider details in Render logs so deployment issues can be
        // diagnosed without exposing yt-dlp output to the browser.
        console.error('[analyze] yt-dlp failed', { id, code, stderr: stderr.slice(-4000) });
        return reject(classifyError(stderr));
      }
      try { resolve(JSON.parse(output)); } catch { reject(new AnalysisError('INVALID_RESPONSE', 'YouTube вернул некорректные данные. Попробуйте позже.')); }
    });
    onProcess?.(child);
  }).finally(() => { analyzerState.flowAnalyzers!--; });
  if (raw.is_live || raw.live_status === 'is_upcoming' || raw.live_status === 'post_live') throw new AnalysisError('LIVE','Дождитесь завершения трансляции и обработки записи.',422);
  const media = normalizeMedia(raw,id);
  if (!media.options.length) throw new AnalysisError('NO_FORMATS','Подходящие видео- или аудиоформаты не найдены.',422);
  return media;
}
