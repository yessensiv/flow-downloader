import { createDownload } from '@/lib/downloads';
import { AnalysisError } from '@/lib/analyzer';
import { validOptionId } from '@/lib/download-format';
import { rateLimit } from '@/lib/rate-limit';
export const runtime = 'nodejs';
export async function POST(request: Request) {
  const limited = rateLimit('download');
  if (limited) return limited;
  const headers = { 'Cache-Control': 'no-store' };
  const origin = request.headers.get('origin');
  let sameOrigin = false;
  try { const parsed = new URL(origin || ''); sameOrigin = ['http:', 'https:'].includes(parsed.protocol) && parsed.host === request.headers.get('host'); } catch { /* invalid origin */ }
  if (!sameOrigin || request.headers.get('content-type')?.split(';')[0] !== 'application/json')
    return Response.json({ error: 'Запрос с другого сайта отклонён.' }, { status: 403, headers });
  try {
    const reader = request.body?.getReader();
    if (!reader) throw new Error();
    let size = 0; const chunks: Uint8Array[] = [];
    while (true) { const { done, value } = await reader.read(); if (done) break; size += value.length; if (size > 4096) { await reader.cancel(); return Response.json({ error: 'Слишком большой запрос.' }, { status: 413, headers }); } chunks.push(value); }
    const body = JSON.parse(Buffer.concat(chunks).toString());
    if (typeof body?.videoId !== 'string' || !/^[\w-]{11}$/.test(body.videoId) || !validOptionId(body.optionId)) throw new Error();
    return Response.json(await createDownload(body.videoId, body.optionId), { status: 202, headers });
  } catch (error) { return Response.json({ error: error instanceof AnalysisError ? error.message : 'Некорректный запрос на скачивание.' }, { status: error instanceof AnalysisError ? error.status : 400, headers }); }
}
