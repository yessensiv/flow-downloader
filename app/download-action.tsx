"use client";
import { useEffect, useRef, useState } from 'react';
import { readSavedDownload, savedDownloadKey } from '@/lib/saved-download';
import { formatSpeed, formatWait, type TransferMetrics } from '@/lib/download-metrics';
import { ArrowDownToLine, CheckCircle2, LoaderCircle } from 'lucide-react';
type Job = { transfer?: TransferMetrics | null; trackCount?: number; timings?: { checking: number; downloading: number; processing: number }; id: string; state: 'cancelled' | 'cancelling' | 'checking' | 'downloading' | 'merging' | 'converting' | 'ready' | 'error'; progress: number | null; title?: string; kind?: 'video' | 'audio'; error?: string; expires: number };
const labels = { cancelled: 'Подготовка отменена', cancelling: 'Останавливаем подготовку…', checking: 'Проверяем видео', downloading: 'Загружаем видео и звук', merging: 'Собираем готовый файл', converting: 'Преобразуем в MP3', ready: 'Можно сохранять', error: 'Не получилось подготовить файл' };
export function DownloadAction({ videoId = '', optionId = '', label = '', mode = 'video', restoreOnly = false }: { videoId?: string; optionId?: string; label?: string; mode?: 'video' | 'audio'; restoreOnly?: boolean }) {
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState('');
  const [starting, setStarting] = useState(false);
  const [retry, setRetry] = useState(0);
  const [restoring, setRestoring] = useState(true);
  const [cancelling, setCancelling] = useState(false);
  useEffect(() => {
    try {
      const id = readSavedDownload(sessionStorage.getItem(savedDownloadKey));
      if (id) setJob({ id, state: 'checking', progress: null, expires: 0 });
    } catch { /* Storage can be unavailable in private browsing. */ }
    setRestoring(false);
  }, []);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!job || ['ready', 'error', 'cancelled'].includes(job.state)) return;
    let stopped = false;
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const response = await fetch(`/api/downloads/${job!.id}`, { signal: abort.signal, cache: 'no-store' });
        const data = await response.json();
        if (response.status === 404) {
          if (!stopped) { setJob(null); setError('Предыдущая задача больше недоступна: срок хранения истёк или сервер перезапущен. Подготовьте файл заново.'); try { sessionStorage.removeItem(savedDownloadKey); } catch {} }
          return;
        }
        if (!response.ok) throw new Error(data.error || 'Не удалось проверить состояние.');
        if (!stopped) { setError(''); setJob(data); if (!['ready', 'error', 'cancelled'].includes(data.state)) timer = setTimeout(poll, 1500); }
      } catch (failure) { if (!stopped) { setError(failure instanceof Error ? failure.message : 'Проверьте подключение.'); setJob(current => current ? { ...current, transfer: null } : current); } }
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
      try { sessionStorage.setItem(savedDownloadKey, data.id); } catch {}
      setJob(data);
    } catch (failure) { if (!abort.signal.aborted) setError(failure instanceof Error ? failure.message : 'Не удалось связаться с сервером.'); }
    finally { if (!abort.signal.aborted) setStarting(false); }
  }
  async function cancel() {
    if (!job || cancelling) return;
    setCancelling(true); setError('');
    try {
      const response = await fetch(`/api/downloads/${job.id}`, { method: 'DELETE' });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Не удалось отменить подготовку.');
      setJob(data);
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Нет связи с сервером.'); }
    finally { setCancelling(false); }
  }
  const audio = (job?.kind || mode) === 'audio';
  const active = starting || !!job && !['ready', 'error', 'cancelled'].includes(job.state);
  const step = !job || job.state === 'checking' ? 0 : job.state === 'downloading' ? 1 : ['merging', 'converting'].includes(job.state) ? 2 : job.state === 'ready' ? 3 : -1;
  if (restoreOnly && !job && !error) return null;
  return <div className="download-action">
    {restoreOnly && <h3>Последняя загрузка</h3>}
    {!restoreOnly && <>
    <button className={`${job?.state === 'ready' ? 'secondary-button' : 'primary-button'} download-start`} disabled={active || restoring} onClick={start}>{active ? <LoaderCircle className="flow-spinner" size={20}/> : <ArrowDownToLine size={20}/ >}{active ? 'Готовим файл…' : job?.state === 'error' ? 'Попробовать ещё раз' : job ? 'Подготовить выбранный вариант' : mode === 'audio' ? 'Скачать аудио' : 'Скачать видео со звуком'}</button>
    </>}
    <p className="download-caption">{job?.title || label}</p>
    {job && !['error', 'cancelled', 'cancelling'].includes(job.state) && <ol className="download-steps" aria-label="Этапы подготовки">{['Проверка', 'Загрузка', 'Обработка', 'Готово'].map((text, index) => <li key={text} className={index < step ? 'complete' : index === step ? 'current' : ''} aria-current={index === step ? 'step' : undefined}><span>{index < step ? '✓' : index + 1}</span>{text}</li>)}</ol>}
    {job && <div className={`download-status status-${job.state}`} role="status" aria-live="polite"><div className="status-heading" key={job.state}>{job.state === 'ready' ? <CheckCircle2 size={24}/> : !['error', 'cancelled'].includes(job.state) ? <LoaderCircle className="flow-spinner" size={22}/> : null}<strong>{job.state === 'downloading' && audio ? 'Загружаем аудио' : labels[job.state]}</strong></div>
      {job.state === 'cancelled' && <span>Загрузка остановлена. Можно выбрать другой формат и начать заново.</span>}
      {job.state === 'checking' && <span>Проверяем выбранное качество. Это займёт несколько секунд.</span>}
      {job.state === 'merging' && <span>{audio ? 'Завершаем подготовку аудио.' : 'Соединяем изображение и звук в один файл. Почти готово.'}</span>}
      {job.state === 'converting' && <span>Создаём MP3 с выбранным битрейтом. Это может занять немного времени.</span>}
      {job.state === 'downloading' && <><progress aria-label="Прогресс этапа загрузки" max="100" value={job.progress ?? undefined}/><span>{job.progress === null ? 'Начинаем загрузку…' : `Загрузка: ≈${Math.round(job.progress)}%`}{!audio && ' · Видео и звук'}</span>
        <dl className="transfer-metrics"><div><dt>Скорость с YouTube</dt><dd>{formatSpeed(job.transfer?.speed ?? null)}</dd></div><div><dt>До конца текущей дорожки</dt><dd>{formatWait(job.transfer?.eta ?? null)}</dd></div></dl>
        <span>{job.transfer && (job.trackCount || 1) > 1 ? `Дорожка ${job.transfer.track} из ${job.trackCount}. ` : ''}Время приблизительное, без учёта следующих дорожек и обработки. Сохранение на устройство — после подготовки.</span>
      </>}
      {job.state === 'error' && <p>{job.error}</p>}
      {job.state === 'ready' && <><a className="primary-button download-start" href={`/api/downloads/${job.id}/file`} download>Сохранить на устройство <ArrowDownToLine size={20}/></a><span>{audio ? 'Аудиофайл готов.' : 'Видео уже со звуком.'} Сохраните его в течение часа — затем файл удалится с сервера.</span></>}
    </div>}
    {job && active && <button type="button" className="secondary-button cancel-download" disabled={cancelling} onClick={cancel}>{cancelling ? 'Отменяем…' : 'Отменить подготовку'}</button>}
    {job?.timings && <details className="download-timings"><summary>Время этапов</summary><p>Проверка: {Math.ceil(job.timings.checking / 1000)} сек. · Загрузка: {Math.ceil(job.timings.downloading / 1000)} сек. · Обработка: {Math.ceil(job.timings.processing / 1000)} сек.</p></details>}
    {job && active && <p className="download-caption">Можно обновить страницу — задача останется в этой вкладке, пока работает сервер.</p>}
    {error && <p className="error" role="alert">{error} {active && <button type="button" onClick={() => setRetry(n => n+1)}>Проверить снова</button>}</p>}
    {!restoreOnly && !job && <p className="download-caption">До 2 ГБ · Готовый файл хранится 1 час</p>}
  </div>;
}
