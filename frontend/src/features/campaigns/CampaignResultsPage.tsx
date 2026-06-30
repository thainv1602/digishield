import { useParams } from 'react-router-dom';
import { useT } from '@/shared/i18n/I18nProvider';
import { DataTable, StatusPill } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useCampaign, type CampaignDetail, type CampaignResultRow } from './api';

/**
 * CampaignResultsPage — completed simulation campaign results.
 * Pixel-matched to the design handoff "CAMPAIGN RESULTS" screen.
 *
 * Data comes from the live backend via `useCampaign(id)`
 * (`GET /sim/campaigns/:id`). The funnel bars and per-user result rows are
 * derived from the response; loading/error/empty states handled inline.
 */

type FunnelBar = {
  label: string;
  value: string;
  pct: number;
  width: number;
  minWidth?: number;
  color: string;
  pctColor: string;
};

const FUNNEL_FALLBACK: FunnelBar[] = [
  { label: 'Gửi', value: '0', pct: 0, width: 0, color: 'var(--color-blue)', pctColor: 'var(--color-muted)' },
  { label: 'Mở', value: '0', pct: 0, width: 0, color: '#4D86F7', pctColor: 'var(--color-muted)' },
  { label: 'Bấm', value: '0', pct: 0, width: 0, minWidth: 80, color: 'var(--color-amber)', pctColor: 'var(--color-amber)' },
  { label: 'Nhập liệu', value: '0', pct: 0, width: 0, minWidth: 60, color: 'var(--color-red)', pctColor: 'var(--color-red)' },
  { label: 'Báo cáo', value: '0', pct: 0, width: 0, minWidth: 60, color: 'var(--color-teal)', pctColor: 'var(--color-teal)' },
];

/** Build the funnel bars from the campaign detail (percent of delivered). */
function toFunnel(detail: CampaignDetail | undefined): FunnelBar[] {
  const f = detail?.funnel;
  if (!f) return FUNNEL_FALLBACK;
  const base = f.delivered > 0 ? f.delivered : 1;
  const pct = (n: number) => Math.round((n / base) * 1000) / 10;
  const fmt = (n: number) => n.toLocaleString('en-US');
  return [
    { label: 'Gửi', value: fmt(f.delivered), pct: pct(f.delivered), width: pct(f.delivered), color: 'var(--color-blue)', pctColor: 'var(--color-muted)' },
    { label: 'Mở', value: fmt(f.open), pct: pct(f.open), width: pct(f.open), color: '#4D86F7', pctColor: 'var(--color-muted)' },
    { label: 'Bấm', value: fmt(f.click), pct: pct(f.click), width: pct(f.click), minWidth: 80, color: 'var(--color-amber)', pctColor: 'var(--color-amber)' },
    { label: 'Nhập liệu', value: fmt(f.submit), pct: pct(f.submit), width: pct(f.submit), minWidth: 60, color: 'var(--color-red)', pctColor: 'var(--color-red)' },
    { label: 'Báo cáo', value: fmt(f.report), pct: pct(f.report), width: pct(f.report), minWidth: 60, color: 'var(--color-teal)', pctColor: 'var(--color-teal)' },
  ];
}

type ResultRow = {
  id: string;
  name: string;
  dept: string;
  action: string;
  actionColor: string;
  learning: string;
  learningColor: string;
};

const ACTION_META: Record<string, { label: string; color: string }> = {
  open: { label: 'Mở', color: 'var(--color-muted)' },
  click: { label: 'Bấm link', color: 'var(--color-amber)' },
  submit: { label: 'Nhập liệu', color: 'var(--color-red)' },
  report: { label: 'Báo cáo', color: 'var(--color-teal)' },
  ignore: { label: 'Bỏ qua', color: 'var(--color-muted)' },
};

const LEARNING_META: Record<string, { label: string; color: string }> = {
  in_progress: { label: '⏳ Đang học', color: 'var(--color-amber)' },
  completed: { label: '✓ Hoàn thành', color: 'var(--color-teal)' },
  not_started: { label: '—', color: 'var(--color-muted)' },
};

/** Map a backend result row onto the FE table row. */
function toResultRow(r: CampaignResultRow, index: number): ResultRow {
  const action = (r.action ?? '').toLowerCase();
  const learning = (r.learningStatus ?? '').toLowerCase();
  const am = ACTION_META[action] ?? { label: r.action ?? '—', color: 'var(--color-muted)' };
  const lm = LEARNING_META[learning] ?? { label: r.learningStatus ?? '—', color: 'var(--color-muted)' };
  return {
    id: `${index}`,
    name: r.name ?? '—',
    dept: r.department ?? '',
    action: am.label,
    actionColor: am.color,
    learning: lm.label,
    learningColor: lm.color,
  };
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};
const labelStyle: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 600,
  color: 'var(--color-muted)',
  letterSpacing: '.08em',
  textTransform: 'uppercase',
};

export default function CampaignResultsPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();
  const { data: detail, isLoading, isError, refetch } = useCampaign(id);

  const funnel = toFunnel(detail);
  const rows = (detail?.results ?? []).map(toResultRow);
  const isCompleted = (detail?.status ?? '').toLowerCase() === 'completed';
  const channelLabel = detail?.channel ? detail.channel.toUpperCase() : '';
  const autoEnrolled = detail?.funnel?.click ?? 0;

  const columns: ColumnDef<ResultRow>[] = [
    {
      id: 'name',
      header: t('Người dùng'),
      cell: (r) => <span style={{ fontWeight: 500, color: 'var(--color-text)' }}>{r.name}</span>,
    },
    { id: 'dept', header: t('Phòng ban'), cell: (r) => <span style={{ color: 'var(--color-muted)' }}>{r.dept}</span>, width: '120px' },
    {
      id: 'action',
      header: t('Hành động'),
      cell: (r) => <span style={{ color: r.actionColor, fontWeight: 500 }}>{t(r.action)}</span>,
      width: '130px',
    },
    {
      id: 'learning',
      header: t('Đã học?'),
      cell: (r) => <span style={{ color: r.learningColor }}>{t(r.learning)}</span>,
      width: '140px',
    },
  ];

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
        {/* Header / status */}
        <div
          style={{
            ...cardStyle,
            padding: '20px 24px',
            marginBottom: 14,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div style={{ ...labelStyle, marginBottom: 6 }}>{t('Chiến dịch')}</div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 20,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.01em',
              }}
            >
              {detail?.name ? `"${detail.name}"` : isLoading ? t('Đang tải…') : t('Chiến dịch')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {[channelLabel, detail?.status].filter(Boolean).join(' · ')}
            </div>
          </div>
          <StatusPill variant={isCompleted ? 'safe' : 'warning'} dot>
            {isCompleted ? t('Đã hoàn thành') : detail?.status ?? '—'}
          </StatusPill>
        </div>

        {isError && (
          <div
            style={{
              ...cardStyle,
              padding: '28px 24px',
              marginBottom: 14,
              textAlign: 'center',
              fontSize: 13.5,
              color: 'var(--color-muted)',
            }}
          >
            <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
              {t('Không tải được chiến dịch.')}{' '}
            </span>
            <button
              type="button"
              onClick={() => refetch()}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--color-blue)',
                fontWeight: 600,
                fontSize: 13.5,
                cursor: 'pointer',
                padding: 0,
              }}
            >
              {t('Thử lại')}
            </button>
          </div>
        )}

        {/* Funnel */}
        <div style={{ ...cardStyle, padding: 24, marginBottom: 14 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 20 }}>
            {t('Phễu chiến dịch · Campaign Funnel')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {funnel.map((f) => (
              <div key={f.label} style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <div
                  style={{
                    width: 100,
                    fontSize: 13,
                    color: 'var(--color-muted)',
                    textAlign: 'right',
                    flexShrink: 0,
                  }}
                >
                  {t(f.label)}
                </div>
                <div
                  style={{
                    flex: 1,
                    background: 'var(--color-bg)',
                    borderRadius: 4,
                    height: 28,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      background: f.color,
                      height: '100%',
                      width: `${f.width}%`,
                      minWidth: f.minWidth,
                      display: 'flex',
                      alignItems: 'center',
                      paddingLeft: 12,
                    }}
                  >
                    <span
                      style={{
                        fontSize: 13,
                        fontWeight: 700,
                        color: 'white',
                        fontFamily: "'JetBrains Mono', monospace",
                      }}
                    >
                      {f.value}
                    </span>
                  </div>
                </div>
                <div
                  style={{
                    width: 44,
                    textAlign: 'right',
                    fontSize: 12,
                    color: f.pctColor,
                    fontWeight: f.pctColor === 'var(--color-muted)' ? 400 : 600,
                  }}
                >
                  {f.pct}%
                </div>
              </div>
            ))}
          </div>
          <div
            style={{
              marginTop: 14,
              background: 'rgba(37,102,235,.08)',
              border: '1px solid rgba(37,102,235,.2)',
              borderRadius: 8,
              padding: '12px 16px',
              fontSize: 13,
              color: 'var(--color-blue)',
            }}
          >
            {t('{n} người bấm link đã được tự động đăng ký vào "Khóa học nhận diện Phishing"', { n: autoEnrolled })}
          </div>
        </div>

        {/* Results table */}
        <div style={{ ...cardStyle, overflow: 'hidden' }}>
          <div
            style={{
              padding: '14px 20px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>{t('Danh sách hành động')}</div>
            <button
              type="button"
              style={{ fontSize: 12, color: 'var(--color-blue)', cursor: 'pointer', background: 'none', border: 'none' }}
            >
              {t('Xuất CSV')}
            </button>
          </div>
          {isLoading && (
            <div style={resultMsg}>{t('Đang tải kết quả…')}</div>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <div style={resultMsg}>{t('Chưa có dữ liệu hành động.')}</div>
          )}
          {!isLoading && rows.length > 0 && (
            <DataTable<ResultRow> columns={columns} data={rows} rowKey={(r) => r.id} />
          )}
        </div>
      </div>
    </>
  );
}

const resultMsg: React.CSSProperties = {
  padding: '28px 20px',
  textAlign: 'center',
  fontSize: 13.5,
  color: 'var(--color-muted)',
};
