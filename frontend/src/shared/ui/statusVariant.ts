import type { StatusVariant } from './StatusPill';

/**
 * Kept out of `StatusPill.tsx` so that file exports a component only — mixing
 * the two costs Vite fast refresh for every screen that renders a pill.
 */
/**
 * Map a numeric risk score (0-100) to a pill variant per the UI/UX spec:
 * 0-39 -> safe (green), 40-69 -> warning (amber), 70-100 -> threat (red).
 */
export function riskToVariant(score: number): StatusVariant {
  if (score >= 70) return 'threat';
  if (score >= 40) return 'warning';
  return 'safe';
}
