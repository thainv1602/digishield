/**
 * Minimal client-side CSV export. Builds an RFC-4180 CSV string from headers +
 * rows and triggers a browser download (UTF-8 BOM so Excel reads Vietnamese
 * correctly). Used by the "Xuất CSV" actions across the app.
 */

/** Escape one CSV cell — quote when it contains a comma, quote or newline. */
function escapeCell(value: unknown): string {
  const s = value == null ? '' : String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** Build a CSV from headers + rows and download it as `filename`. */
export function downloadCsv(
  filename: string,
  headers: string[],
  rows: ReadonlyArray<ReadonlyArray<string | number | null | undefined>>,
): void {
  const lines = [headers, ...rows].map((row) => row.map(escapeCell).join(','));
  const bom = String.fromCharCode(0xfeff); // so Excel detects UTF-8
  const url = URL.createObjectURL(new Blob([bom + lines.join('\n')], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
