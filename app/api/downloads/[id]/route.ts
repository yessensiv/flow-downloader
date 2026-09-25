import { getJob, jobView } from '@/lib/downloads';
export const runtime = 'nodejs';
export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const job = getJob((await context.params).id);
  const headers = { 'Cache-Control': 'no-store' };
  if (!job || job.expires < Date.now()) return Response.json({ error: 'Срок хранения истёк. Подготовьте файл заново.' }, { status: 404, headers });
  return Response.json(jobView(job), { headers });
}
