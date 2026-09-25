"use client";
import { useEffect, useRef, useState } from 'react';
import { ArrowDownToLine } from 'lucide-react';
type Job = { id: string; state: 'checking' | 'downloading' | 'merging' | 'ready' | 'error'; progress: number | null; title?: string; error?: string; expires: number };
const labels = { checking: 'Проверяем доступность…', downloading: 'Скачиваем дорожки…', merging: 'Объединяем видео и звук…', ready: 'Видео готово', error: 'Не удалось подготовить видео' };
export function DownloadAction({ videoId, optionId, label }: { videoId: string; optionId: string; label: string }) {
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
  const active = starting || !!job && !['ready', 'error'].includes(job.state);
  return <div className="download-action">
    <button className="primary-button download-start" disabled={active} onClick={start}><ArrowDownToLine size={20}/>{active ? 'Готовим файл…' : job ? 'Подготовить ещё раз' : 'Скачать видео со звуком'}</button>
    <p className="download-caption">{active || job?.state === 'ready' ? job?.title || label : label}</p>
    {job && <div className="download-status" role="status" aria-live="polite"><strong>{labels[job.state]}</strong>
      {job.state === 'downloading' && <><progress max="100" value={job.progress ?? undefined}/><span>{job.progress === null ? 'Получаем данные…' : `${Math.round(job.progress)}% текущей дорожки`}</span></>}
      {job.state === 'error' && <p>{job.error}</p>}
      {job.state === 'ready' && <><a className="primary-button download-start" href={`/api/downloads/${job.id}/file`} download>Сохранить файл <ArrowDownToLine size={20}/></a><span>Ссылка доступна один час. Видео и звук уже в одном файле.</span></>}
    </div>}
    {error && <p className="error" role="alert">{error} {active && <button type="button" onClick={() => setRetry(n => n+1)}>Проверить снова</button>}</p>}
    {!job && <p className="download-caption">До 2 ГБ · Готовый файл хранится 1 час</p>}
  </div>;
}
