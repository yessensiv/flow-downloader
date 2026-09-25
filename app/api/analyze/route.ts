import { parseYouTubeUrl } from '@/lib/youtube';
import { analyzeVideo, AnalysisError } from '@/lib/analyzer';
import { rateLimit } from '@/lib/rate-limit';
export const runtime = 'nodejs';
let active = 0;
const headers = { 'Cache-Control':'no-store' };
export async function POST(request: Request) {
  const limited = rateLimit('analyze');
  if (limited) return limited;
  const origin = request.headers.get('origin');
  if (origin) {
    let sameOrigin = false;
    try { const parsed = new URL(origin); sameOrigin = ['http:','https:'].includes(parsed.protocol) && parsed.host === request.headers.get('host'); } catch { /* invalid origin */ }
    if (!sameOrigin) return Response.json({error:'Запрос с другого сайта отклонён.'},{status:403,headers});
  }
  let body: unknown;
  try {
    // Bound actual bytes, including requests with no Content-Length.
    const reader = request.body?.getReader();
    if (!reader) throw new Error();
    const chunks: Uint8Array[] = []; let size = 0;
    while (true) {
      const {done,value} = await reader.read(); if (done) break;
      size += value.length; if (size > 4096) { await reader.cancel(); return Response.json({error:'Ссылка слишком длинная.'},{status:413,headers}); }
      chunks.push(value);
    }
    body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch { return Response.json({error:'Ожидается JSON с полем url.'},{status:400,headers}); }
  const url = body && typeof body === 'object' && 'url' in body ? body.url : null;
  const id = typeof url === 'string' ? parseYouTubeUrl(url) : null;
  if (!id) return Response.json({error:'Введите корректную ссылку на видео YouTube.',code:'INVALID_URL'},{status:400,headers});
  if (active >= 2) return Response.json({error:'Сервис занят. Повторите запрос чуть позже.',code:'BUSY'},{status:429,headers:{...headers,'Retry-After':'10'}});
  active++;
  try { return Response.json(await analyzeVideo(id,request.signal),{headers}); }
  catch (error) {
    const failure = error instanceof AnalysisError ? error : new AnalysisError('INTERNAL','Ошибка анализа видео.',500);
    return Response.json({error:failure.message,code:failure.code},{status:failure.status,headers});
  } finally { active--; }
}
