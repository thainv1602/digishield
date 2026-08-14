import { describe, expect, it } from 'vitest';
import { toFunnel, toReported, VALUE_FITS_INSIDE_PCT } from './funnel';
import type { CampaignDetail } from './api';

/** The example from DigiShield_UIUX_Spec.md, where reports outnumber submissions. */
const SPEC_EXAMPLE = {
  funnel: { delivered: 1000, open: 540, click: 132, submit: 41, report: 88 },
} as CampaignDetail;

describe('toFunnel', () => {
  it('is only the failure path', () => {
    expect(toFunnel(SPEC_EXAMPLE).map((b) => b.label)).toEqual([
      'Gửi',
      'Mở',
      'Bấm',
      'Nhập liệu',
    ]);
  });

  it('never widens, whatever the reporting figure is', () => {
    // The defect this replaces: "Báo cáo" was the last bar, so the spec's own
    // example drew 41 then 88 — a funnel that gets wider at the bottom, which
    // states that reporting is a subset of submitting. It is not.
    const widths = toFunnel(SPEC_EXAMPLE).map((b) => b.width);
    for (let i = 1; i < widths.length; i++) {
      expect(widths[i]!).toBeLessThanOrEqual(widths[i - 1]!);
    }
  });

  it('measures every stage against delivered', () => {
    const bars = toFunnel(SPEC_EXAMPLE);
    expect(bars.map((b) => b.pct)).toEqual([100, 54, 13.2, 4.1]);
  });

  it('draws a bar exactly as long as its share', () => {
    // A minWidth used to pad the short bars out so the number inside stayed
    // readable, which drew 4.1% and 13.2% closer to the same length than they
    // are. Width must equal the percentage and nothing else.
    for (const bar of toFunnel(SPEC_EXAMPLE)) {
      expect(bar.width).toBe(bar.pct);
    }
  });

  it('survives a campaign that delivered nothing', () => {
    const nothing = { funnel: { delivered: 0, open: 0, click: 0, submit: 0, report: 0 } } as CampaignDetail;

    expect(toFunnel(nothing).map((b) => b.pct)).toEqual([0, 0, 0, 0]);
    expect(toReported(nothing).pct).toBe(0);
  });

  it('falls back to empty bars with no detail at all', () => {
    expect(toFunnel(undefined)).toHaveLength(4);
    expect(toFunnel(undefined).every((b) => b.width === 0)).toBe(true);
  });
});

describe('toReported', () => {
  it('is a single outcome, measured against delivered', () => {
    const reported = toReported(SPEC_EXAMPLE);

    expect(reported.label).toBe('Báo cáo');
    expect(reported.value).toBe('88');
    // 88 of the 1,000 delivered — not of the 41 who submitted.
    expect(reported.pct).toBe(8.8);
  });

  it('is allowed to exceed the funnel stages below it', () => {
    // The whole point: 88 reports against 41 submissions is a normal campaign,
    // not a contradiction, once reporting is off the funnel's scale.
    const submitted = toFunnel(SPEC_EXAMPLE).at(-1)!;

    expect(toReported(SPEC_EXAMPLE).pct).toBeGreaterThan(submitted.pct);
  });

  it('reports zero when there is no detail', () => {
    expect(toReported(undefined).value).toBe('0');
    expect(toReported(undefined).pct).toBe(0);
  });
});

describe('pctText', () => {
  it('never reports a real result as zero percent', () => {
    // 2 submissions out of 5,000 is 0.04%, which rounds to "0%" — a figure that
    // says nobody submitted when somebody did.
    const tiny = { funnel: { delivered: 5000, open: 60, click: 9, submit: 2, report: 0 } } as CampaignDetail;
    const submitted = toFunnel(tiny).at(-1)!;

    expect(submitted.pct).toBe(0);
    expect(submitted.pctText).toBe('<0,1%');
  });

  it('says plain zero when it really is zero', () => {
    const none = { funnel: { delivered: 5000, open: 60, click: 9, submit: 0, report: 0 } } as CampaignDetail;

    expect(toFunnel(none).at(-1)!.pctText).toBe('0%');
    expect(toReported(none).pctText).toBe('0%');
  });

  it('leaves ordinary shares alone', () => {
    expect(toFunnel(SPEC_EXAMPLE).map((b) => b.pctText)).toEqual(['100%', '54%', '13.2%', '4.1%']);
    expect(toReported(SPEC_EXAMPLE).pctText).toBe('8.8%');
  });
});

describe('value placement', () => {
  it('keeps the number outside a bar too short to hold it', () => {
    // 4.1% of the row is a few pixels wide; a number printed inside it would be
    // clipped, which is what the minWidth padding existed to avoid.
    const submitted = toFunnel(SPEC_EXAMPLE).at(-1)!;

    expect(submitted.width).toBeLessThan(VALUE_FITS_INSIDE_PCT);
  });

  it('keeps it inside once the bar is wide enough', () => {
    const delivered = toFunnel(SPEC_EXAMPLE)[0]!;

    expect(delivered.width).toBeGreaterThanOrEqual(VALUE_FITS_INSIDE_PCT);
  });
});
