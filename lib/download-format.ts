import type { MediaOption } from './media';

export function validOptionId(id: unknown): id is string {
  return typeof id === 'string' && id.length <= 100 && /^[\w-]+(?:(?:\+[\w-]+)|(?::mp3:(?:128|192|256|320)))?$/.test(id);
}

// Only called with a format freshly matched against server-side analysis.
export function downloadFormat(option: MediaOption) {
  if (!validOptionId(option.id)) throw new Error('Некорректный вариант файла.');
  const ext = option.container.toLowerCase();
  if (option.kind === 'audio' && option.conversion) {
    const match = /^([\w-]+):mp3:(128|192|256|320)$/.exec(option.id);
    if (!match || ext !== 'mp3') throw new Error('Некорректный формат аудио.');
    return { ext, mime: 'audio/mpeg', args: ['-f', match[1], '--extract-audio', '--audio-format', 'mp3', '--audio-quality', `${match[2]}K`] };
  }
  if (option.kind === 'audio' && ['m4a', 'webm'].includes(ext) && /^[\w-]+$/.test(option.id))
    return { ext, mime: ext === 'm4a' ? 'audio/mp4' : 'audio/webm', args: ['-f', option.id] };
  if (option.kind === 'video' && ['mp4', 'webm'].includes(ext) && /^[\w-]+(?:\+[\w-]+)?$/.test(option.id))
    return { ext, mime: `video/${ext}`, args: ['-f', option.id, '--merge-output-format', ext] };
  throw new Error('Неподдерживаемый формат файла.');
}
