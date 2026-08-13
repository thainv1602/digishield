/**
 * Pure helpers for the dashboard risk trend.
 *
 * They live outside AdminDashboardPage so they can be unit-tested without
 * rendering, and so the page file keeps exporting only its component.
 */

import type { DashboardTrendPoint } from './api';

const DAY_MS = 86_400_000;

/**
 * Keep the trailing `days` of a trend, measured by the points' own dates.
 *
 * `GET /analytics/dashboard` returns every ORG `risk_score` row the tenant has,
 * not a fixed 90-point window, so taking a proportion of the array (the old
 * `length * days / 90`) showed a span unrelated to the label on the control:
 * with 200 rows, "30D" returned 67 points covering 67 days. Anchoring on the
 * last point's date makes the selection mean what it says.
 *
 * Dates arrive as `YYYY-MM-DD`. If any of them fails to parse we fall back to a
 * plain tail slice rather than silently dropping the points we cannot place.
 */
export function sliceByDays(points: DashboardTrendPoint[], days: number): DashboardTrendPoint[] {
  if (points.length === 0 || days <= 0) return points;
  const times = points.map((p) => Date.parse(`${p.date}T00:00:00Z`));
  if (times.some((t) => Number.isNaN(t))) return points.slice(-days);
  const end = times[times.length - 1]!;
  const start = end - (days - 1) * DAY_MS;
  return points.filter((_, i) => times[i]! >= start);
}

/** `2026-08-12` -> `12/08`, the axis format used across the app. */
export function shortDate(iso: string): string {
  const [, month, day] = iso.split('-');
  return month && day ? `${day}/${month}` : iso;
}
