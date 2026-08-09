import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadCsv } from './csv';

/** UTF-8 byte-order mark the export prepends so Excel detects the encoding. */
const BOM = '﻿';

/**
 * Every "Xuất CSV" action in the app goes through this. The exported rows carry
 * user-supplied text — report subjects, names, department labels — so the
 * escaping is what stands between a comma in someone's job title and a file
 * whose columns are shifted by one from that row onwards.
 */
describe('downloadCsv', () => {
  let payloads: string[] = [];
  let clicks: { href: string; download: string }[] = [];
  const revoke = vi.fn();
  const RealBlob = globalThis.Blob;

  /** The raw payload handed to the browser, BOM included. */
  const raw = (): string => {
    const last = payloads[payloads.length - 1];
    if (last === undefined) throw new Error('the export wrote nothing');
    return last;
  };

  /** The CSV itself, with the BOM stripped. */
  const written = (): string => raw().slice(BOM.length);

  beforeEach(() => {
    payloads = [];
    clicks = [];
    revoke.mockClear();

    // jsdom's Blob has no text(), and it implements neither URL method, so the
    // payload is captured at construction rather than read back off the blob.
    class CapturingBlob {
      constructor(parts: unknown[]) {
        payloads.push(parts.map(String).join(''));
      }
    }
    globalThis.Blob = CapturingBlob as unknown as typeof Blob;

    Object.defineProperty(URL, 'createObjectURL', {
      value: () => `blob:mock/${payloads.length}`,
      configurable: true,
    });
    Object.defineProperty(URL, 'revokeObjectURL', { value: revoke, configurable: true });

    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicks.push({ href: this.href, download: this.download });
    });
  });

  afterEach(() => {
    globalThis.Blob = RealBlob;
    vi.restoreAllMocks();
  });

  it('writes the header row followed by the data rows', () => {
    downloadCsv('x.csv', ['name', 'score'], [
      ['Alice', 80],
      ['Bob', 40],
    ]);

    expect(written()).toBe('name,score\nAlice,80\nBob,40');
  });

  it('quotes a cell containing a comma', () => {
    downloadCsv('x.csv', ['name'], [['Nguyễn, Văn A']]);

    // Unquoted, this one value would become two columns.
    expect(written()).toBe('name\n"Nguyễn, Văn A"');
  });

  it('doubles embedded quotes and wraps the cell', () => {
    downloadCsv('x.csv', ['title'], [['He said "hi"']]);

    expect(written()).toBe('title\n"He said ""hi"""');
  });

  it('quotes a cell containing a newline', () => {
    downloadCsv('x.csv', ['body'], [['line one\nline two']]);

    expect(written()).toBe('body\n"line one\nline two"');
  });

  it('leaves ordinary cells unquoted', () => {
    downloadCsv('x.csv', ['a'], [['plain text']]);

    expect(written()).toBe('a\nplain text');
  });

  it('renders null and undefined as empty cells', () => {
    downloadCsv('x.csv', ['a', 'b', 'c'], [[null, undefined, 'x']]);

    // Not "null"/"undefined" — an empty column is what a reader expects.
    expect(written()).toBe('a,b,c\n,,x');
  });

  it('keeps a zero rather than blanking it', () => {
    downloadCsv('x.csv', ['score'], [[0]]);

    expect(written()).toBe('score\n0');
  });

  it('escapes the header row too', () => {
    downloadCsv('x.csv', ['name, full'], [['Alice']]);

    expect(written()).toBe('"name, full"\nAlice');
  });

  it('exports a header-only file when there are no rows', () => {
    downloadCsv('x.csv', ['a', 'b'], []);

    expect(written()).toBe('a,b');
  });

  it('prepends a BOM so Excel reads Vietnamese correctly', () => {
    downloadCsv('x.csv', ['tên'], [['Nguyễn Văn A']]);

    expect(raw().startsWith(BOM)).toBe(true);
  });

  it('hands the browser the requested filename and releases the object URL', () => {
    downloadCsv('bao-cao.csv', ['a'], [['1']]);

    expect(clicks).toHaveLength(1);
    const [click] = clicks;
    if (!click) throw new Error('the export did not trigger a download');
    expect(click.download).toBe('bao-cao.csv');
    // Not revoking leaks the blob for the lifetime of the document.
    expect(revoke).toHaveBeenCalledWith(click.href);
  });

});

/**
 * CSV quoting is not formula escaping — a quoted cell is still evaluated when
 * the file is opened. These rows carry user-supplied text, so a leading `=` in
 * a display name would run on the machine of whoever opens the export.
 */
describe('downloadCsv · formula injection', () => {
  let payloads: string[] = [];
  const RealBlob = globalThis.Blob;

  const written = (): string => {
    const last = payloads[payloads.length - 1];
    if (last === undefined) throw new Error('the export wrote nothing');
    return last.slice(BOM.length);
  };

  /** The single data cell of a one-column, one-row export. */
  const cell = (value: string | number): string => {
    downloadCsv('x.csv', ['h'], [[value]]);
    return written().split('\n')[1] ?? '';
  };

  beforeEach(() => {
    payloads = [];
    class CapturingBlob {
      constructor(parts: unknown[]) {
        payloads.push(parts.map(String).join(''));
      }
    }
    globalThis.Blob = CapturingBlob as unknown as typeof Blob;
    Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:mock', configurable: true });
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    globalThis.Blob = RealBlob;
    vi.restoreAllMocks();
  });

  it.each(['=', '+', '@', '\t', '\r'])('neutralises a cell starting with %j', (lead) => {
    expect(cell(`${lead}cmd|'/c calc'!A0`)).toContain(`'${lead}`);
  });

  it('neutralises a formula that also needs CSV quoting', () => {
    // The apostrophe goes inside the quotes, or the quoting would strip it.
    expect(cell('=HYPERLINK("http://evil","click")')).toBe(
      '"\'=HYPERLINK(""http://evil"",""click"")"',
    );
  });

  it('neutralises a leading dash that is not a number', () => {
    expect(cell('-1+cmd')).toBe("'-1+cmd");
  });

  it('leaves a negative number alone', () => {
    // Guarding this would turn a score into text and break sorting downstream.
    expect(cell('-5')).toBe('-5');
    expect(cell('-12.5')).toBe('-12.5');
  });

  it('never guards a numeric value', () => {
    expect(cell(-5)).toBe('-5');
    expect(cell(0)).toBe('0');
  });

  it('guards a leading + even though it parses as a number', () => {
    // Vietnamese phone numbers arrive as "+8490…". Excel would otherwise treat
    // the cell as a formula and display it with the + silently dropped.
    expect(cell('+84901234567')).toBe("'+84901234567");
  });

  it('leaves ordinary text untouched', () => {
    expect(cell('Nguyễn Văn A')).toBe('Nguyễn Văn A');
    expect(cell('a=b')).toBe('a=b');
    expect(cell('')).toBe('');
  });

  it('guards a dangerous header too', () => {
    downloadCsv('x.csv', ['=cmd'], [['ok']]);

    expect(written().split('\n')[0]).toBe("'=cmd");
  });
});
