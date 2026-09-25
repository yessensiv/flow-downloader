export function savedDownloadKey(mode: 'video' | 'audio') {
  return `flow.current-download.v2.${mode}`;
}
export function readSavedDownload(value: string | null): string | null {
  return value && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value) ? value : null;
}
