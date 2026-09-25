export type MediaOption = {
  id: string; kind: 'video' | 'audio'; container: string; label: string;
  codec: string; fps: number | null; size: number | null; approximate: boolean;
  height: number | null; needsMerge: boolean; conversion: boolean;
};
export type MediaInfo = {
  id: string; title: string; channel: string; duration: number | null;
  thumbnail: string; options: MediaOption[];
};
type RawFormat = {
  format_id?: string; ext?: string; vcodec?: string; acodec?: string; height?: number;
  width?: number; fps?: number; abr?: number; tbr?: number; filesize?: number;
  filesize_approx?: number; has_drm?: boolean; url?: string;
};
export type RawInfo = {
  id?: string; title?: string; channel?: string; uploader?: string; duration?: number;
  is_live?: boolean; live_status?: string; formats?: RawFormat[];
};
const number = (n: unknown): number | null => typeof n === 'number' && Number.isFinite(n) && n > 0 ? n : null;
const hasCodec = (c?: string) => !!c && c !== 'none';
export function normalizeMedia(raw: RawInfo, id: string): MediaInfo {
  const formats = (raw.formats ?? []).filter(f => !f.has_drm && f.url && f.format_id);
  const audio = formats.filter(f => !hasCodec(f.vcodec) && hasCodec(f.acodec));
  const options: MediaOption[] = [];
  for (const f of formats) {
    const video = hasCodec(f.vcodec);
    const height = number(f.height);
    if (video && (!height || height > 2160 || !['mp4', 'webm'].includes(f.ext ?? ''))) continue;
    if (!video && (!hasCodec(f.acodec) || !['m4a', 'webm'].includes(f.ext ?? ''))) continue;
    const needsMerge = video && !hasCodec(f.acodec);
    const companion = needsMerge ? audio.filter(a => f.ext === 'mp4' ? a.ext === 'm4a' : a.ext === 'webm').sort((a,b) => (b.abr ?? b.tbr ?? 0) - (a.abr ?? a.tbr ?? 0))[0] : undefined;
    if (needsMerge && !companion) continue;
    const bytes = number(f.filesize) ?? number(f.filesize_approx);
    const audioBytes = companion ? number(companion.filesize) ?? number(companion.filesize_approx) : 0;
    const size = bytes && audioBytes !== null ? bytes + audioBytes : null;
    const badge = height === 2160 ? ' · 4K' : height === 1440 ? ' · 2K' : height === 1080 ? ' · Full HD' : '';
    const fps = number(f.fps);
    options.push({
      id: companion ? `${f.format_id}+${companion.format_id}` : f.format_id!,
      kind: video ? 'video' : 'audio', container: f.ext!.toUpperCase(),
      label: video ? `${height}p${badge}${fps ? ` · ${fps} fps` : ''}` : `${f.acodec}${number(f.abr) ? ` · ≈${Math.round(f.abr!)} kbps` : ' · исходное качество'}`,
      codec: video ? f.vcodec! : f.acodec!, fps: video ? fps : null, height: video ? height : null,
      size, approximate: !f.filesize || !!companion && !companion.filesize, needsMerge, conversion: false,
    });
  }
  const bestAudio = audio.sort((a,b) => (b.abr ?? b.tbr ?? 0) - (a.abr ?? a.tbr ?? 0))[0];
  if (bestAudio) for (const bitrate of [320,256,192,128]) options.push({id:`${bestAudio.format_id}:mp3:${bitrate}`,kind:'audio',container:'MP3',label:`${bitrate} kbps · конвертация`,codec:'MP3',fps:null,height:null,size:null,approximate:true,needsMerge:false,conversion:true});
  options.sort((a,b) => (b.height ?? 0) - (a.height ?? 0) || (b.fps ?? 0) - (a.fps ?? 0));
  return {id,title:raw.title || 'Видео YouTube',channel:raw.channel || raw.uploader || 'YouTube',duration:number(raw.duration),thumbnail:`https://i.ytimg.com/vi/${id}/hqdefault.jpg`,options};
}
