"use client";
import { useEffect, useRef, useState } from 'react';
import { ArrowDownToLine, CheckCircle2, LoaderCircle } from 'lucide-react';
type Job = { id: string; state: 'checking' | 'downloading' | 'merging' | 'converting' | 'ready' | 'error'; progress: number | null; title?: string; kind?: 'video' | 'audio'; error?: string; expires: number };
const labels = { checking: 'Проверяем видео', downloading: 'Загружаем видео и звук', merging: 'Собираем готовый файл', converting: 'Преобразуем в MP3', ready: 'Можно сохранять', error: 'Не получилось подготовить файл' };
export function DownloadAction({ videoId, optionId, label, mode }: { videoId: string; optionId: string; label: string; mode: 'video' | 'audio' }) {
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState('');
  const [starting, setStarting] = useState(false);
  const [retry, setRetry] = useState(0);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!job || job.state === 'ready' || job.state === 'error') return;
    let stopped = false;
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const response = await fetch(`/api/downloads/${job!.id}`, { signal: abort.signal, cache: 'no-store' });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || 'Не удалось проверить состояние.');
        if (!stopped) { setError(''); setJob(data); if (!['ready', 'error'].includes(data.state)) timer = setTimeout(poll, 1500); }
      } catch (failure) { if (!stopped) setError(failure instanceof Error ? failure.message : 'Проверьте подключение.'); }
    }
    timer = setTimeout(poll, 500);
    return () => { stopped = true; clearTimeout(timer); abort.abort(); };
  }, [job?.id, retry]);
  async function start() {
    controller.current?.abort(); const abort = new AbortController(); controller.current = abort;
    setStarting(true); setError(''); setJob(null);
    try {
      const response = await fetch('/api/downloads', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ videoId, optionId }), signal: abort.signal });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Не удалось начать скачивание.');
      setJob(data);
    } catch (failure) { if (!abort.signal.aborted) setError(failure instanceof Error ? failure.message : 'Не удалось связаться с сервером.'); }
    finally { if (!abort.signal.aborted) setStarting(false); }
  }
  const audio = (job?.kind || mode) === 'audio';
  const active = starting || !!job && !['ready', 'error'].includes(job.state);
  return <div className="download-action">
    <button className={`${job?.state === 'ready' ? 'secondary-button' : 'primary-button'} download-start`} disabled={active} onClick={start}>{active ? <LoaderCircle className="flow-spinner" size={20}/> : <ArrowDownToLine size={20}/ >}{active ? 'Готовим файл…' : job?.state === 'error' ? 'Попробовать ещё раз' : job ? 'Подготовить выбранный вариант' : mode === 'audio' ? 'Скачать аудио' : 'Скачать видео со звуком'}</button>
    <p className="download-caption">{active || job?.state === 'ready' ? job?.title || label : label}</p>
    {job && <div className={`download-status status-${job.state}`} role="status" aria-live="polite"><div className="status-heading" key={job.state}>{job.state === 'ready' ? <CheckCircle2 size={24}/> : job.state !== 'error' ? <LoaderCircle className="flow-spinner" size={22}/> : null}<strong>{job.state === 'downloading' && audio ? 'Загружаем аудио' : labels[job.state]}</strong></div>
      {job.state === 'checking' && <span>Проверяем выбранное качество. Это займёт несколько секунд.</span>}
      {job.state === 'merging' && <span>{audio ? 'Завершаем подготовку аудио.' : 'Соединяем изображение и звук в один файл. Почти готово.'}</span>}
      {job.state === 'converting' && <span>Создаём MP3 с выбранным битрейтом. Это может занять немного времени.</span>}
      {job.state === 'downloading' && <><progress aria-label="Загрузка текущей дорожки" max="100" value={job.progress ?? undefined}/><span>{job.progress === null ? 'Начинаем загрузку…' : `${Math.round(job.progress)}% текущей части`}{!audio && ' · Видео и звук загружаются отдельно, затем мы объединим их.'}</span></>}
      {job.state === 'error' && <p>{job.error}</p>}
      {job.state === 'ready' && <><a className="primary-button download-start" href={`/api/downloads/${job.id}/file`} download>Сохранить на устройство <ArrowDownToLine size={20}/></a><span>{audio ? 'Аудиофайл готов.' : 'Видео уже со звуком.'} Сохраните его в течение часа — затем файл удалится с сервера.</span></>}
    </div>}
    {error && <p className="error" role="alert">{error} {active && <button type="button" onClick={() => setRetry(n => n+1)}>Проверить снова</button>}</p>}
    {!job && <p className="download-caption">До 2 ГБ · Готовый файл хранится 1 час</p>}
  </div>;
}
