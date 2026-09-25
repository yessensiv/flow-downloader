import { execFile } from 'node:child_process';
import path from 'node:path';
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
export async function analyzeVideo(id: string, signal?: AbortSignal) {
  if (!/^[a-zA-Z0-9_-]{11}$/.test(id)) throw new AnalysisError('INVALID_URL', 'Некорректный идентификатор видео.',400);
  const binary = process.env.YTDLP_PATH || path.join(process.cwd(), '.tools', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');
  const raw = await new Promise<RawInfo>((resolve,reject) => {
    execFile(binary, ['--ignore-config','--no-playlist','--skip-download','--dump-single-json','--no-progress','--no-warnings','--no-cache-dir','--no-js-runtimes','--js-runtimes',`node:${process.execPath}`,'--socket-timeout','15','--retries','1','--extractor-retries','1','--',`https://www.youtube.com/watch?v=${id}`],
      {timeout:60000,maxBuffer:8*1024*1024,windowsHide:true,signal}, (error,stdout,stderr) => {
        if (error) {
          if ('code' in error && error.code === 'ENOENT') return reject(new AnalysisError('NOT_CONFIGURED','Обработчик видео ещё не установлен на сервере.',503));
          if (error.killed || error.name === 'AbortError') return reject(new AnalysisError('TIMEOUT','Анализ занял слишком много времени. Попробуйте ещё раз.',504));
          return reject(classifyError(stderr));
        }
        try { resolve(JSON.parse(stdout)); } catch { reject(new AnalysisError('INVALID_RESPONSE','YouTube вернул некорректные данные. Попробуйте позже.')); }
      });
  });
  if (raw.is_live || raw.live_status === 'is_upcoming' || raw.live_status === 'post_live') throw new AnalysisError('LIVE','Дождитесь завершения трансляции и обработки записи.',422);
  const media = normalizeMedia(raw,id);
  if (!media.options.length) throw new AnalysisError('NO_FORMATS','Подходящие видео- или аудиоформаты не найдены.',422);
  return media;
}
