/**
 * How a campaign's counts become bars.
 *
 * Kept apart from the page so the shape of the funnel can be tested without
 * rendering, and so the page file keeps exporting only its component.
 */

import type { CampaignDetail } from './api';

export type FunnelBar = {
  label: string;
  value: string;
  pct: number;
  /** What to print for {@link pct} — see {@link pctLabel}. */
  pctText: string;
  width: number;
  color: string;
  pctColor: string;
};

/**
 * A share, rounded for display without rounding a real result away.
 *
 * Two people out of five thousand is 0.04%, which rounds to "0%" — a figure
 * that says nobody submitted when somebody did. Anything above zero that
 * rounds to zero is shown as "<0,1%" instead.
 */
export function pctLabel(count: number, pct: number): string {
  if (count > 0 && pct === 0) return '<0,1%';
  return `${pct}%`;
}

/**
 * Bars narrower than this cannot hold their own value legibly, so the number
 * moves outside rather than the bar being padded out to fit it.
 */
export const VALUE_FITS_INSIDE_PCT = 12;

export const FUNNEL_FALLBACK: FunnelBar[] = [
  { label: 'Gửi', value: '0', pct: 0, pctText: '0%', width: 0, color: 'var(--color-blue)', pctColor: 'var(--color-muted)' },
  { label: 'Mở', value: '0', pct: 0, pctText: '0%', width: 0, color: '#4D86F7', pctColor: 'var(--color-muted)' },
  { label: 'Bấm', value: '0', pct: 0, pctText: '0%', width: 0, color: 'var(--color-amber)', pctColor: 'var(--color-amber)' },
  { label: 'Nhập liệu', value: '0', pct: 0, pctText: '0%', width: 0, color: 'var(--color-red)', pctColor: 'var(--color-red)' },
];

const pctOf = (n: number, base: number) => Math.round((n / base) * 1000) / 10;
const fmtCount = (n: number) => n.toLocaleString('en-US');
/** Guard the denominator: a campaign that delivered nothing still renders. */
const funnelBase = (delivered: number) => (delivered > 0 ? delivered : 1);

/**
 * The failure path, as a real funnel: every stage is a subset of the one above
 * it, so the bars only ever shrink.
 *
 * Reporting is deliberately not here — see {@link toReported}.
 */
export function toFunnel(detail: CampaignDetail | undefined): FunnelBar[] {
  const f = detail?.funnel;
  if (!f) return FUNNEL_FALLBACK;
  const base = funnelBase(f.delivered);
  const pct = (n: number) => pctOf(n, base);
  return [
    { label: 'Gửi', value: fmtCount(f.delivered), pct: pct(f.delivered), pctText: pctLabel(f.delivered, pct(f.delivered)), width: pct(f.delivered), color: 'var(--color-blue)', pctColor: 'var(--color-muted)' },
    { label: 'Mở', value: fmtCount(f.open), pct: pct(f.open), pctText: pctLabel(f.open, pct(f.open)), width: pct(f.open), color: '#4D86F7', pctColor: 'var(--color-muted)' },
    { label: 'Bấm', value: fmtCount(f.click), pct: pct(f.click), pctText: pctLabel(f.click, pct(f.click)), width: pct(f.click), color: 'var(--color-amber)', pctColor: 'var(--color-amber)' },
    { label: 'Nhập liệu', value: fmtCount(f.submit), pct: pct(f.submit), pctText: pctLabel(f.submit, pct(f.submit)), width: pct(f.submit), color: 'var(--color-red)', pctColor: 'var(--color-red)' },
  ];
}

/**
 * Reporting, shown apart from the funnel above.
 *
 * It is not a later stage of the same path: somebody can report a simulation
 * without ever opening it, so "reported" is not a subset of "submitted" and the
 * two do not belong on one descending scale. Drawn as the funnel's last bar it
 * also made the chart contradict itself whenever reports outnumbered
 * submissions — the shape the UI/UX spec's own example has (submitted 41,
 * reported 88), where a funnel would have to widen at the bottom.
 *
 * Still measured against delivered, which is a real denominator: you can only
 * report what reached you.
 */
export function toReported(detail: CampaignDetail | undefined): FunnelBar {
  const f = detail?.funnel;
  const report = f?.report ?? 0;
  const pct = f ? pctOf(report, funnelBase(f.delivered)) : 0;
  return {
    label: 'Báo cáo',
    value: fmtCount(report),
    pct,
    pctText: pctLabel(report, pct),
    width: pct,
    color: 'var(--color-teal)',
    pctColor: 'var(--color-teal)',
  };
}
