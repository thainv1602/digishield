import { useEffect, useRef, useState } from 'react';
import type { DashboardTrendPoint } from './api';
import { shortDate } from './trend';

/** Track a block element's rendered width so the chart can size in real pixels. */
function useElementWidth() {
  const ref = useRef<HTMLDivElement | null>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const node = ref.current;
    if (!node || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setWidth(entry.contentRect.width);
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

const PLOT_H = 104;
/** Room on the right for the end-of-series value label. */
const PLOT_PAD_RIGHT = 42;
const PLOT_PAD_TOP = 12;

/**
 * Org risk over time — a single series, so there is no legend: the card title
 * names what is plotted.
 *
 * Drawn at the container's real pixel width rather than being stretched with
 * `preserveAspectRatio="none"`. That attribute scaled x and y by different
 * factors, so the stroke came out thinner along one axis and the end-of-series
 * marker rendered as an ellipse instead of a circle.
 */
export function RiskTrendChart({ points }: { points: DashboardTrendPoint[] }) {
  const { ref, width } = useElementWidth();
  const w = Math.max(width, 1);
  const innerW = Math.max(w - PLOT_PAD_RIGHT, 1);
  const plotBottom = PLOT_H - 8;

  const clamp = (v: number) => Math.max(0, Math.min(100, v));
  const x = (i: number) => (points.length <= 1 ? innerW : (i / (points.length - 1)) * innerW);
  const y = (v: number) => plotBottom - (clamp(v) / 100) * (plotBottom - PLOT_PAD_TOP);

  const last = points[points.length - 1];
  const line = points.map((p, i) => `${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');

  return (
    <div ref={ref} style={{ width: '100%' }}>
      {/* Nothing to draw until the container has been measured. */}
      {width > 0 && last && (
        <svg
          width={w}
          height={PLOT_H}
          viewBox={`0 0 ${w} ${PLOT_H}`}
          style={{ display: 'block' }}
          role="img"
          aria-label={`Điểm rủi ro theo ngày, ${points.length} điểm, kết thúc ở ${last.value}.`}
        >
          <defs>
            <linearGradient id="riskTrendGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#2566EB" stopOpacity="0.15" />
              <stop offset="100%" stopColor="#2566EB" stopOpacity="0.01" />
            </linearGradient>
          </defs>

          {[0, 25, 50, 75, 100].map((v) => (
            <line
              key={v}
              x1={0}
              x2={innerW}
              y1={y(v)}
              y2={y(v)}
              stroke="var(--color-border)"
              strokeWidth={1}
            />
          ))}

          {points.length > 1 && (
            <polygon
              points={`0,${plotBottom} ${line} ${innerW},${plotBottom}`}
              fill="url(#riskTrendGrad)"
            />
          )}
          <polyline
            points={line}
            fill="none"
            stroke="var(--color-blue)"
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
          />

          {/* A ring in the surface colour keeps the marker legible where it sits
              on the line, without drawing a border around the mark. */}
          <circle cx={x(points.length - 1)} cy={y(last.value)} r={5} fill="var(--color-surface)" />
          <circle cx={x(points.length - 1)} cy={y(last.value)} r={3.5} fill="var(--color-blue)" />
          {/* One direct label, on the endpoint. Without it the chart carries no
              readable value at all — there is no axis and no tooltip. */}
          <text
            x={x(points.length - 1) + 10}
            y={y(last.value) + 4}
            fontSize={12.5}
            fontWeight={700}
            fill="var(--color-text)"
          >
            {last.value}
          </text>
        </svg>
      )}
    </div>
  );
}

/** Axis labels for the trend, read from the data rather than hard-coded. */
export function TrendAxis({ points }: { points: DashboardTrendPoint[] }) {
  if (points.length === 0) return null;
  const first = points[0]!;
  const middle = points[Math.floor((points.length - 1) / 2)]!;
  const last = points[points.length - 1]!;
  const ticks = points.length > 2 ? [first, middle, last] : [first, last];
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginTop: 6,
        paddingRight: PLOT_PAD_RIGHT,
      }}
    >
      {ticks.map((p, i) => (
        <span key={`${p.date}-${i}`} style={{ fontSize: 11, color: 'var(--color-muted)' }}>
          {shortDate(p.date)}
        </span>
      ))}
    </div>
  );
}
