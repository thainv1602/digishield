/**
 * Minimal client-side CSV export. Builds an RFC-4180 CSV string from headers +
 * rows and triggers a browser download (UTF-8 BOM so Excel reads Vietnamese
 * correctly). Used by the "Xuất CSV" actions across the app.
 */

/**
 * Characters that make a spreadsheet read the cell as a formula rather than as
 * text. Tab and carriage return are included because Excel strips them and then
 * looks at whatever follows.
 */
const FORMULA_LEADS = ['=', '+', '@', '\t', '\r'];

/**
 * Neutralise spreadsheet formula injection.
 *
 * CSV quoting is not formula escaping: `"=HYPERLINK(...)"` is valid RFC-4180
 * *and* still a live formula when the file is opened. Exported cells carry
 * user-supplied text — report subjects, names, department labels — so a leading
 * `=` in someone's display name would execute on the machine of whoever opens
 * the export. Prefixing with an apostrophe forces the cell to text; Excel and
 * LibreOffice both hide the apostrophe when displaying it.
 *
 * `-` is deliberately treated differently. It begins a formula, but it also
 * begins every negative number, and this export is used for score and delta
 * columns. Guarding `-5` would turn a number into text and break sorting and
 * charts downstream, so a value that parses cleanly as a number is left alone
 * and only something like `-1+cmd` is guarded.
 */
function guardFormula(s: string): string {
  const first = s.charAt(0);
  if (FORMULA_LEADS.includes(first)) return `'${s}`;
  if (first === '-' && !Number.isFinite(Number(s))) return `'${s}`;
  return s;
}

/** Escape one CSV cell — neutralise formulas, then quote when RFC-4180 needs it. */
function escapeCell(value: unknown): string {
  if (value == null) return '';
  // No separate branch for numbers: nothing JavaScript stringifies a number to
  // can begin with a formula character, and a negative one is covered by the
  // numeric exemption in guardFormula.
  const s = guardFormula(String(value));
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
