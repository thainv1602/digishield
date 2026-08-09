import { describe, expect, it } from 'vitest';
import { riskColor, riskLabel } from './risk';

/**
 * The risk band drives what a manager sees at a glance across the gauge, the
 * pills and the bars. The interesting cases are the two boundaries — the values
 * that decide whether someone shows up as "needs attention" or not.
 */
describe('risk bands', () => {
  const cases: Array<[number, string, string]> = [
    [0, 'THẤP', 'var(--risk-low)'],
    [39, 'THẤP', 'var(--risk-low)'],
    [40, 'TRUNG BÌNH', 'var(--risk-medium)'],
    [69, 'TRUNG BÌNH', 'var(--risk-medium)'],
    [70, 'CAO', 'var(--risk-high)'],
    [100, 'CAO', 'var(--risk-high)'],
  ];

  it.each(cases)('scores %i as %s', (score, label, color) => {
    expect(riskLabel(score)).toBe(label);
    expect(riskColor(score)).toBe(color);
  });

  it('treats each boundary as inclusive of the higher band', () => {
    // 40 and 70 belong to the band above, not below — off by one here moves
    // people between "medium" and "high" on every dashboard at once.
    expect(riskLabel(39.9)).toBe('THẤP');
    expect(riskLabel(40)).toBe('TRUNG BÌNH');
    expect(riskLabel(69.9)).toBe('TRUNG BÌNH');
    expect(riskLabel(70)).toBe('CAO');
  });

  it('keeps colour and label on the same band', () => {
    // They are read together; disagreeing would show a red pill labelled THẤP.
    for (let score = 0; score <= 100; score += 1) {
      const band = riskLabel(score);
      const expected =
        band === 'CAO'
          ? 'var(--risk-high)'
          : band === 'TRUNG BÌNH'
            ? 'var(--risk-medium)'
            : 'var(--risk-low)';
      expect(riskColor(score)).toBe(expected);
    }
  });

  it('clamps nothing but still lands in a band outside 0–100', () => {
    // Scores are meant to be 0–100; if a computation ever escapes that range the
    // UI should still render something rather than an undefined colour.
    expect(riskLabel(-5)).toBe('THẤP');
    expect(riskLabel(150)).toBe('CAO');
    expect(riskColor(-5)).toBe('var(--risk-low)');
    expect(riskColor(150)).toBe('var(--risk-high)');
  });

  it('falls back to the low band for a non-numeric score', () => {
    // NaN fails every comparison, so it lands in the final branch. Worth
    // pinning: a missing score must read as low, never as high.
    expect(riskLabel(Number.NaN)).toBe('THẤP');
    expect(riskColor(Number.NaN)).toBe('var(--risk-low)');
  });
});
