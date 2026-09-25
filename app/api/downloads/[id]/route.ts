import { getJob, jobView, cancelDownload } from '@/lib/downloads';
export const runtime = 'nodejs';
export async function DELETE(request: Request, context: { params: Promise<{ id: string }> }) {
  const headers = { 'Cache-Control': 'no-store' };
  let sameOrigin = false;
  try { const origin = new URL(request.headers.get('origin') || ''); sameOrigin = ['http:', 'https:'].includes(origin.protocol) && origin.host === request.headers.get('host'); } catch {}
  if (!sameOrigin) return Response.json({ error: 'Запрос с другого сайта отклонён.' }, { status: 403, headers });
  try {
    const job = await cancelDownload((await context.params).id);
    return job ? Response.json(job, { headers }) : Response.json({ error: 'Задача больше недоступна.' }, { status: 404, headers });
  } catch (error) { return Response.json({ error: error instanceof Error ? error.message : 'Не удалось отменить задачу.' }, { status: 500, headers }); }
}
export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const job = getJob((await context.params).id);
  const headers = { 'Cache-Control': 'no-store' };
  if (!job || job.expires < Date.now()) return Response.json({ error: 'Срок хранения истёк. Подготовьте файл заново.' }, { status: 404, headers });
  return Response.json(jobView(job), { headers });
}
