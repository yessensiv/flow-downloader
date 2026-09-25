"use client";
import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import type { MediaInfo } from '@/lib/media';

export function MediaResult({media,mode}:{media:MediaInfo;mode:'video'|'audio'}) {
  const [container,setContainer] = useState('');
  const [selected,setSelected] = useState('');
  const options = media.options.filter(o => o.kind === mode);
  const containers = [...new Set(options.map(o => o.container))];
  const currentContainer = containers.includes(container) ? container : containers[0];
  const choices = options.filter(o => o.container === currentContainer);
  const choice = choices.find(o => o.id === selected) ?? choices[0];
  const maxHeight = Math.max(0,...media.options.map(o => o.height ?? 0));
  const duration = media.duration ? `${Math.floor(media.duration/60)}:${String(Math.floor(media.duration%60)).padStart(2,'0')}` : 'Длительность неизвестна';
  return <div className="demo-panel" aria-label="Результат анализа">
    <div className="demo-label">ДАННЫЕ ВИДЕО · {maxHeight ? `ДО ${maxHeight}p` : 'АУДИО'}</div>
    <div className="media-info"><img className="result-thumbnail" src={media.thumbnail} alt="Обложка видео" referrerPolicy="no-referrer"/><div><span className="media-category">{media.channel}</span><h3>{media.title}</h3><p>{duration}{maxHeight > 0 ? ` · максимум ${maxHeight}p` : ''}</p></div></div>
    {!choice ? <p className="preview-note">Для этого ролика нет доступных вариантов {mode === 'video' ? 'видео со звуком' : 'аудио'}.</p> : <>
      <div className="options result-options"><label>Формат<div className="select-wrap"><select value={currentContainer} onChange={e => {setContainer(e.target.value);setSelected('');}}>{containers.map(c => <option key={c}>{c}</option>)}</select><ChevronDown size={16}/></div></label><label>Качество и кодек<div className="select-wrap"><select value={choice.id} onChange={e => setSelected(e.target.value)}>{choices.map(o => <option key={o.id} value={o.id}>{o.label}{mode === 'video' ? ` · ${o.codec}` : ''}</option>)}</select><ChevronDown size={16}/></div></label></div>
      <p className="result-details">Размер: {choice.size ? `${choice.approximate ? '≈' : ''}${(choice.size/1024/1024).toFixed(1)} МБ` : 'неизвестен'}{choice.needsMerge ? ' · Видео и звук будут объединены' : ''}{choice.conversion ? ' · MP3 через конвертацию, без улучшения исходного звука' : ''}</p>
      <p className="preview-note">Форматы получены. Создание и скачивание файла подключим на следующем этапе.</p>
    </>}
  </div>;
}
