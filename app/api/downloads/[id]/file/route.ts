import { createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { getJob } from '@/lib/downloads';
export const runtime = 'nodejs';
const transfers = globalThis as typeof globalThis & { flowTransfers?: number };
export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const job = getJob((await context.params).id);
  if (!job || job.expires < Date.now() || job.state !== 'ready' || !job.file) return Response.json({ error: 'Файл недоступен. Подготовьте его заново.' }, { status: 404 });
  if ((transfers.flowTransfers || 0) >= 4 || job.readers >= 2) return Response.json({ error: 'Слишком много одновременных скачиваний. Повторите через несколько секунд.' }, { status: 429, headers: { 'Retry-After': '5', 'Cache-Control': 'no-store' } });
  const ext = job.ext || 'mp4';
  const name = (job.title || 'video').replace(/[\x00-\x1f<>:"/\\|?*]/g, '').slice(0, 150) + '.' + ext;
  const stream = createReadStream(job.file);
  job.readers++;
  transfers.flowTransfers = (transfers.flowTransfers || 0) + 1;
  stream.once('close', () => { job.readers--; transfers.flowTransfers!--; });
  return new Response(Readable.toWeb(stream) as ReadableStream, { headers: {
    'Content-Type': job.mime || `video/${ext}`, 'Content-Length': String(job.size),
    'Content-Disposition': `attachment; filename="media.${ext}"; filename*=UTF-8''${encodeURIComponent(name).replace(/['()*]/g, c => '%' + c.charCodeAt(0).toString(16))}`,
    'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
  } });
}
