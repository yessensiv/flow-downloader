"use client";
import { useState } from 'react';
import { ChevronDown, Settings2 } from 'lucide-react';
import type { MediaInfo, MediaOption } from '@/lib/media';
import { DownloadAction } from './download-action';

function codecName(codec:string) {
  if (/^(avc1|avc3|h264)/i.test(codec)) return 'H.264 — лучше совместимость';
  if (/^vp0?9/i.test(codec)) return 'VP9 — хорошее качество';
  if (/^av01/i.test(codec)) return 'AV1 — файл меньше, поддерживается не везде';
  if (/^mp4a/i.test(codec)) return 'AAC';
  if (/^opus/i.test(codec)) return 'Opus';
  return codec;
}
const variantRank = (o:MediaOption) => /^(avc1|avc3|h264)/i.test(o.codec) ? 0 : /^vp0?9/i.test(o.codec) ? 1 : /^av01/i.test(o.codec) ? 2 : 3;
const sizeLabel = (o:MediaOption) => o.size ? `${o.approximate?'≈':''}${(o.size/1024/1024).toFixed(1)} МБ` : 'неизвестен';

export function MediaResult({media,mode}:{media:MediaInfo;mode:'video'|'audio'}) {
  const [container,setContainer] = useState('');
  const [quality,setQuality] = useState('');
  const [variant,setVariant] = useState('');
  const options = media.options.filter(o => o.kind === mode);
  const containers = [...new Set(options.map(o => o.container))];
  const currentContainer = containers.includes(container) ? container : containers[0];
  const containerOptions = options.filter(o => o.container === currentContainer);
  const groups = [...new Map(containerOptions.map(o => {
    const key = mode === 'video' ? `${o.height}:${o.fps ?? 0}` : o.label;
    const label = mode === 'video' ? `${o.height}p${o.height === 2160 ? ' · 4K' : o.height === 1440 ? ' · 2K' : o.height === 1080 ? ' · Full HD' : ''}${o.fps ? ` · ${o.fps} fps` : ''}` : o.label;
    return [key,{key,label,options:containerOptions.filter(x => (mode === 'video' ? `${x.height}:${x.fps ?? 0}` : x.label) === key)}];
  }))].map(([,g]) => g);
  const group = groups.find(g => g.key === quality) ?? groups[0];
  const variants = group ? [...group.options].sort((a,b) => variantRank(a)-variantRank(b) || (b.size ?? 0)-(a.size ?? 0)) : [];
  const choice = variants.find(o => o.id === variant) ?? variants[0];
  const maxHeight = Math.max(0,...media.options.map(o => o.height ?? 0));
  const duration = media.duration ? `${Math.floor(media.duration/60)}:${String(Math.floor(media.duration%60)).padStart(2,'0')}` : 'Длительность неизвестна';
  return <div className="demo-panel" aria-label="Результат анализа">
    <div className="demo-label">Варианты {maxHeight ? `видео · до ${maxHeight}p` : 'аудио'}</div>
    <div className="media-info"><img className="result-thumbnail" src={media.thumbnail} alt="Обложка видео" referrerPolicy="no-referrer"/><div><span className="media-category">{media.channel}</span><h3>{media.title}</h3><p>{duration}{maxHeight > 0 ? ` · максимум ${maxHeight}p` : ''}</p></div></div>
    {!choice ? <p className="preview-note">Для этого ролика нет доступных вариантов {mode === 'video' ? 'видео со звуком' : 'аудио'}.</p> : <>
      <div className="options result-options"><label>Формат<div className="select-wrap"><select value={currentContainer} onChange={e => {setContainer(e.target.value);setQuality('');setVariant('');}}>{containers.map(c => <option key={c}>{c}</option>)}</select><ChevronDown size={16}/></div></label><label>{mode === 'video' ? 'Качество и плавность' : 'Качество звука'}<div className="select-wrap"><select value={group.key} onChange={e => {setQuality(e.target.value);setVariant('');}}>{groups.map(g => <option key={g.key} value={g.key}>{g.label}{mode === 'audio' ? ` · ${g.options[0]?.codec}` : ''}</option>)}</select><ChevronDown size={16}/></div></label></div>
      {mode === 'video' && variants.length > 1 && <details className="codec-details"><summary><Settings2 size={14}/> Другие варианты кодека <ChevronDown size={14}/></summary><div className="codec-options">{variants.map(o => <label key={o.id}><input type="radio" name={`codec-${media.id}-${group.key}`} checked={choice.id === o.id} onChange={() => setVariant(o.id)}/><span>{codecName(o.codec)}{o.needsMerge ? ' · объединить звук' : ''}</span></label>)}</div></details>}
      <p className="result-details">Размер: {sizeLabel(choice)}{choice.needsMerge ? ' · видео и звук будут объединены' : ''}{choice.conversion ? ' · MP3 через конвертацию' : ''}</p>
      {mode === 'video' && !media.options.some(o => o.fps === 60) && <p className="fps-hint">Для этого видео доступны только показанные значения fps.</p>}
      {mode === 'video' ? <DownloadAction videoId={media.id} optionId={choice.id} label={`${choice.container} · ${group.label}`}/> : <p className="preview-note">Скачивание аудио подключим следующим этапом.</p>}
    </>}
  </div>;
}
