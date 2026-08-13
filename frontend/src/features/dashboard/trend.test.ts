import { describe, expect, it } from 'vitest';
import { shortDate, sliceByDays } from './trend';
import type { DashboardTrendPoint } from './api';

/** `n` consecutive daily points ending on 2026-08-12. */
function daily(n: number): DashboardTrendPoint[] {
  const end = Date.UTC(2026, 7, 12);
  return Array.from({ length: n }, (_, i) => ({
    date: new Date(end - (n - 1 - i) * 86_400_000).toISOString().slice(0, 10),
    value: 50,
  }));
}

describe('sliceByDays', () => {
  it('returns a window of the requested length', () => {
    expect(sliceByDays(daily(200), 30)).toHaveLength(30);
    expect(sliceByDays(daily(200), 60)).toHaveLength(60);
    expect(sliceByDays(daily(200), 90)).toHaveLength(90);
  });

  it('anchors the window on the newest point', () => {
    const window = sliceByDays(daily(200), 30);
    expect(window[window.length - 1]!.date).toBe('2026-08-12');
    expect(window[0]!.date).toBe('2026-07-14'); // 29 days earlier
  });

  it('does not scale the window with how much history the backend sent', () => {
    // The bug this replaces took `length * days / 90`, so the same "30D" choice
    // returned 10 points against 30 rows and 67 against 200 — the label meant
    // nothing. A day window must not depend on the row count.
    expect(sliceByDays(daily(200), 30)).toHaveLength(30);
    expect(sliceByDays(daily(120), 30)).toHaveLength(30);
    expect(sliceByDays(daily(31), 30)).toHaveLength(30);
  });

  it('returns everything when the history is shorter than the window', () => {
    expect(sliceByDays(daily(7), 90)).toHaveLength(7);
  });

  it('keeps gaps honest — missing days shrink the count, not the span', () => {
    // Weekly samples over 30 days: five points, not thirty.
    const sparse: DashboardTrendPoint[] = [
      { date: '2026-06-01', value: 70 },
      { date: '2026-07-15', value: 66 },
      { date: '2026-07-22', value: 64 },
      { date: '2026-07-29', value: 61 },
      { date: '2026-08-05', value: 60 },
      { date: '2026-08-12', value: 62 },
    ];
    const window = sliceByDays(sparse, 30);
    expect(window.map((p) => p.date)).toEqual([
      '2026-07-15',
      '2026-07-22',
      '2026-07-29',
      '2026-08-05',
      '2026-08-12',
    ]);
  });

  it('handles an empty trend', () => {
    expect(sliceByDays([], 30)).toEqual([]);
  });

  it('falls back to a tail slice when a date cannot be parsed', () => {
    const broken: DashboardTrendPoint[] = [
      { date: 'not-a-date', value: 1 },
      { date: '2026-08-11', value: 2 },
      { date: '2026-08-12', value: 3 },
    ];
    expect(sliceByDays(broken, 2).map((p) => p.value)).toEqual([2, 3]);
  });
});

describe('shortDate', () => {
  it('renders day/month', () => {
    expect(shortDate('2026-08-12')).toBe('12/08');
    expect(shortDate('2026-01-05')).toBe('05/01');
  });

  it('passes through anything it cannot split', () => {
    expect(shortDate('unknown')).toBe('unknown');
  });
});
