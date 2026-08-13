import { useMemo, useState } from 'react';
import { ProgressBar, RiskGauge, StatusPill, riskColor, riskLabel } from '@/shared/ui';
import { ArrowRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useT } from '@/shared/i18n/I18nProvider';
import { useDashboard } from './api';
import { RiskTrendChart, TrendAxis } from './RiskTrendChart';
import { shortDate, sliceByDays } from './trend';

/**
 * AdminDashboardPage — security/admin overview inside AppShell.
 * Pixel-matched to the design handoff "DASHBOARD" screen.
 *
 * Data comes from the live backend via `useDashboard()`
 * (`GET /analytics/dashboard`). Loading/error/empty states are handled below;
 * the layout/components are unchanged.
 */

type AiLabel = 'threat' | 'spam' | 'clean';

const labelToVariant = { threat: 'threat', spam: 'warning', clean: 'safe' } as const;
const labelToText = { threat: 'THREAT', spam: 'SPAM', clean: 'CLEAN' } as const;
const dotColor = { threat: 'var(--color-red)', spam: 'var(--color-amber)', clean: 'var(--color-teal)' } as const;

/** Coerce an unknown AI label string to one of the three known variants. */
function normalizeLabel(value: string | null | undefined): AiLabel {
  return value === 'threat' || value === 'spam' || value === 'clean' ? value : 'clean';
}

/**
 * A signed change against the previous period.
 *
 * Colour encodes whether the movement is *good*, not merely whether it points
 * up: risk and phish-prone falling is an improvement, training completion
 * rising is. Every delta used to be painted one fixed colour regardless of
 * sign, so a worsening number still read as reassuring.
 *
 * `upIsGood` has no default on purpose — the polarity is the whole point, and a
 * default would let the next caller reintroduce the bug by omission.
 */
function Delta({
  value,
  suffix = '',
  upIsGood,
}: {
  value: number;
  suffix?: string;
  upIsGood: boolean;
}) {
  const neutral = value === 0;
  const good = (value > 0) === upIsGood;
  const color = neutral
    ? 'var(--color-muted)'
    : good
      ? 'var(--color-teal)'
      : 'var(--color-red)';
  const arrow = neutral ? '' : value > 0 ? '▲ ' : '▼ ';
  return (
    <span style={{ color, fontWeight: 600, fontSize: 12 }}>
      {arrow}
      {value > 0 ? '+' : ''}
      {value}
      {suffix}
    </span>
  );
}

const labelStyle: React.CSSProperties = {
  fontSize: 10.5,
  fontWeight: 600,
  color: 'var(--color-muted)',
  letterSpacing: '.08em',
  textTransform: 'uppercase',
};

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 20,
};

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const t = useT();
  const { data, isLoading, isError, refetch } = useDashboard();
  const [rangeDays, setRangeDays] = useState(90);
  // Computed before the early returns below — hooks cannot sit behind a branch.
  const trend = useMemo(
    () => sliceByDays(data?.risk_trend ?? [], rangeDays),
    [data?.risk_trend, rangeDays],
  );

  if (isLoading) {
    return <DashboardState>{t('Đang tải bảng điều khiển…')}</DashboardState>;
  }

  if (isError || !data) {
    return (
      <DashboardState>
        <div style={{ color: 'var(--color-red)', fontWeight: 600, marginBottom: 8 }}>
          {t('Không tải được dữ liệu bảng điều khiển')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 14 }}>
          {t('Vui lòng kiểm tra kết nối tới máy chủ rồi thử lại.')}
        </div>
        <button type="button" onClick={() => refetch()} style={retryBtn}>
          {t('Thử lại')}
        </button>
      </DashboardState>
    );
  }

  const riskScore = data.risk_score ?? 0;
  // Emphasis, not identity: this organisation carries the accent and the peer
  // averages recede to grey. They used to be painted amber and red, which are
  // the reserved warning/danger colours — an industry average is not an alert,
  // and colouring it by row index also meant the meaning moved if the backend
  // ever reordered the list.
  const benchmarks = (data.benchmarks ?? []).map((b) => ({
    label: b.label,
    value: b.value,
    strong: b.strong,
    color: b.strong ? 'var(--color-blue)' : 'var(--color-muted)',
  }));
  // riskColor/riskLabel are the shared 70/40 scale. The page previously carried
  // its own copy of those thresholds, so the bar could disagree with the pills
  // and the gauge elsewhere in the app.
  const departments = (data.departments ?? []).map((d) => ({
    name: d.name,
    score: d.score,
    color: riskColor(d.score),
    band: riskLabel(d.score),
  }));
  const recentReports = (data.recent_reports ?? []).map((r) => ({
    id: r.id,
    title: r.title,
    who: r.who,
    age: r.age,
    aiLabel: normalizeLabel(r.ai_label),
  }));
  const openAlerts = data.open_alerts ?? { total: 0, critical: 0, warning: 0 };
  const phishPronePct = data.phish_prone_pct ?? 0;
  const trainingCompletion = data.training_completion ?? 0;

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
        {/* KPI row */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 14,
            marginBottom: 20,
          }}
        >
          {/* Risk gauge */}
          <div style={{ ...cardStyle, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <div style={{ ...labelStyle, marginBottom: 10, alignSelf: 'flex-start' }}>Risk Score</div>
            <RiskGauge score={riskScore} size={150} />
            <div style={{ fontSize: 12, color: 'var(--color-muted)', marginTop: 4 }}>
              <Delta value={data.risk_delta ?? 0} upIsGood={false} /> {t('vs tháng trước')}
            </div>
          </div>

          {/* Phish-prone % */}
          <div style={cardStyle}>
            <div style={{ ...labelStyle, marginBottom: 12 }}>Phish-prone %</div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 38,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.02em',
                lineHeight: 1,
                marginBottom: 8,
              }}
            >
              {phishPronePct}%
            </div>
            <Delta value={data.phish_prone_pct_delta ?? 0} suffix="%" upIsGood={false} />
            <span style={{ fontSize: 12, color: 'var(--color-muted)' }}> {t('so tháng trước')}</span>
            <div style={{ fontSize: 12, color: 'var(--color-muted)', marginTop: 10 }}>
              {t('TB ngành gov')}:{' '}
              <strong style={{ color: 'var(--color-text)', fontFamily: "'JetBrains Mono', monospace" }}>
                {data.industry_avg_pct ?? 0}%
              </strong>
            </div>
          </div>

          {/* Training completion */}
          <div style={cardStyle}>
            <div style={{ ...labelStyle, marginBottom: 12 }}>{t('Hoàn thành ĐT')}</div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 38,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.02em',
                lineHeight: 1,
                marginBottom: 8,
              }}
            >
              {trainingCompletion}%
            </div>
            <span
              style={{
                background: 'var(--pill-safe-bg)',
                color: 'var(--pill-safe-fg)',
                borderRadius: 99,
                padding: '2px 8px',
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              {t('hoàn thành')}
            </span>
            <span style={{ fontSize: 12, color: 'var(--color-muted)' }}> {t('trong tháng')}</span>
            <div style={{ marginTop: 12 }}>
              <ProgressBar value={trainingCompletion} max={100} color="var(--color-teal)" />
            </div>
          </div>

          {/* Open alerts */}
          <div style={cardStyle}>
            <div style={{ ...labelStyle, marginBottom: 12 }}>{t('Cảnh báo mở')}</div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 38,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.02em',
                lineHeight: 1,
                marginBottom: 10,
              }}
            >
              {openAlerts.total}
            </div>
            {/* The dot carries the severity; the label stays in ink. Painting
                the count red made a quiet inbox of zero look like an incident,
                and put the value itself on a colour it has to compete with. */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12 }}>
                <Dot color="var(--color-red)" />
                <span style={{ color: 'var(--color-text)', fontWeight: 500 }}>
                  {openAlerts.critical} critical
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12 }}>
                <Dot color="var(--color-amber)" />
                <span style={{ color: 'var(--color-text)', fontWeight: 500 }}>
                  {openAlerts.warning} warning
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Charts row */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '3fr 2fr',
            gap: 14,
            marginBottom: 20,
          }}
        >
          {/* 90-day risk trend line chart */}
          <div style={cardStyle}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 18,
              }}
            >
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                  {t('Xu hướng rủi ro')}
                </div>
                <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>
                  {trend.length > 0
                    ? `${shortDate(trend[0]!.date)} – ${shortDate(trend[trend.length - 1]!.date)}`
                    : t('Chưa có dữ liệu')}
                </div>
              </div>
              <select
                aria-label={t('Khoảng thời gian')}
                value={rangeDays}
                onChange={(e) => setRangeDays(Number(e.target.value))}
                style={{
                  fontSize: 12,
                  color: 'var(--color-text)',
                  background: 'var(--color-bg)',
                  borderRadius: 6,
                  padding: '4px 10px',
                  border: '1px solid var(--color-border)',
                  cursor: 'pointer',
                }}
              >
                <option value={30}>30D</option>
                <option value={60}>60D</option>
                <option value={90}>90D</option>
              </select>
            </div>
            {trend.length === 0 ? (
              <div style={{ fontSize: 13, color: 'var(--color-muted)', padding: '24px 0' }}>
                {t('Chưa có điểm rủi ro nào trong khoảng này.')}
              </div>
            ) : (
              <>
                <RiskTrendChart points={trend} />
                <TrendAxis points={trend} />
              </>
            )}
          </div>

          {/* Benchmark bar chart */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 4 }}>
              {t('So chuẩn ngành')}
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-muted)', marginBottom: 18 }}>Phish-prone %</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {benchmarks.map((b) => (
                <div key={b.label}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: 5,
                    }}
                  >
                    <span
                      style={{
                        fontSize: 12.5,
                        color: b.strong ? 'var(--color-text)' : 'var(--color-muted)',
                        fontWeight: b.strong ? 500 : 400,
                      }}
                    >
                      {b.label}
                    </span>
                    <span
                      style={{
                        fontSize: 12,
                        fontWeight: 600,
                        color: 'var(--color-text)',
                        fontFamily: "'JetBrains Mono', monospace",
                      }}
                    >
                      {b.value}%
                    </span>
                  </div>
                  <div style={{ background: 'var(--color-bg)', borderRadius: 99, height: 7 }}>
                    <div
                      style={{
                        background: b.color,
                        borderRadius: 99,
                        height: 7,
                        width: `${b.value}%`,
                        minWidth: 6,
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Bottom row */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {/* Department risk bars */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Phòng ban rủi ro cao')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
              {departments.length === 0 && (
                <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{t('Chưa có dữ liệu phòng ban.')}</div>
              )}
              {departments.map((d) => (
                <div key={d.name} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div
                    style={{
                      width: 80,
                      fontSize: 13,
                      color: 'var(--color-text)',
                      fontWeight: 500,
                      flexShrink: 0,
                    }}
                  >
                    {d.name}
                  </div>
                  <div style={{ flex: 1, background: 'var(--color-bg)', borderRadius: 99, height: 6 }}>
                    <div
                      style={{
                        background: d.color,
                        borderRadius: 99,
                        height: 6,
                        width: `${d.score}%`,
                      }}
                    />
                  </div>
                  <div
                    style={{
                      width: 28,
                      textAlign: 'right',
                      fontSize: 12,
                      fontWeight: 600,
                      fontFamily: "'JetBrains Mono', monospace",
                      color: 'var(--color-text)',
                      flexShrink: 0,
                    }}
                  >
                    {d.score}
                  </div>
                  {/* The band spelled out, so the colour is never the only thing
                      saying "high". Amber sits at 2.69:1 on white, below the 3:1
                      floor for a mark, which makes a written label mandatory
                      rather than decorative. */}
                  <div
                    style={{
                      width: 76,
                      textAlign: 'right',
                      fontSize: 11,
                      fontWeight: 600,
                      color: 'var(--color-text-soft)',
                      flexShrink: 0,
                    }}
                  >
                    {d.band}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Recent reports list */}
          <div style={cardStyle}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 14,
              }}
            >
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>{t('Báo cáo gần đây')}</div>
              <button
                type="button"
                onClick={() => navigate('/soc/inbox')}
                style={{
                  fontSize: 12,
                  color: 'var(--color-blue)',
                  cursor: 'pointer',
                  background: 'none',
                  border: 'none',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                {t('Xem tất cả')} <ArrowRight size={13} />
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {recentReports.length === 0 && (
                <div style={{ fontSize: 13, color: 'var(--color-muted)', padding: '9px 10px' }}>
                  {t('Chưa có báo cáo nào.')}
                </div>
              )}
              {recentReports.map((r) => (
                <div
                  key={r.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '9px 10px',
                    borderRadius: 8,
                    background: r.aiLabel === 'threat' ? 'rgba(221,59,64,.05)' : undefined,
                  }}
                >
                  <Dot color={dotColor[r.aiLabel]} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        fontSize: 13,
                        fontWeight: 500,
                        color: 'var(--color-text)',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {r.title}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--color-muted)' }}>
                      {r.who} · {r.age}
                    </div>
                  </div>
                  <StatusPill variant={labelToVariant[r.aiLabel]} dot={false}>
                    {labelToText[r.aiLabel]}
                  </StatusPill>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function Dot({ color }: { color: string }) {
  return (
    <div
      style={{ width: 7, height: 7, borderRadius: '50%', background: color, flexShrink: 0 }}
      aria-hidden="true"
    />
  );
}

/** Centered loading/error placeholder that keeps the page chrome intact. */
function DashboardState({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <div
        style={{
          ...cardStyle,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
          minHeight: 240,
          color: 'var(--color-muted)',
          fontSize: 14,
        }}
      >
        {children}
      </div>
    </div>
  );
}

const retryBtn: React.CSSProperties = {
  background: 'var(--color-blue)',
  color: '#fff',
  border: 'none',
  borderRadius: 8,
  padding: '8px 18px',
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
};
