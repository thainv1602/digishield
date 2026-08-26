import { useMemo } from 'react';
import { Button, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/i18nContext';
import { useInterventions, type InterventionEvent, type InterventionDecision } from './api';

/**
 * InterventionLogPage — transaction-risk intervention audit log (`/soc/interventions`).
 *
 * 4 decision KPIs (Allow / Warn / Pause / Block) computed from the feed + an
 * audit table with time, risk signals, the acting user and the decision.
 *
 * Data comes from the live backend via `useInterventions()`
 * (`GET /interventions`). Loading/error/empty states handled below. "Xuất CSV"
 * exports the currently loaded rows client-side.
 */

type Decision = 'ALLOW' | 'WARN' | 'PAUSE' | 'BLOCK';

interface LogRow {
  id: string;
  time: string;
  signal: string;
  user: string;
  decision: Decision;
}

const DECISION_BADGE: Record<Decision, { bg: string; color: string }> = {
  ALLOW: { bg: '#DDF3E6', color: '#0F7A4A' },
  WARN: { bg: '#FCEBCF', color: '#C0720A' },
  PAUSE: { bg: '#FCEBCF', color: '#C0720A' },
  BLOCK: { bg: '#FBE0DF', color: '#BE2A2F' },
};

const KPI_META: { key: Decision; label: string; color: string }[] = [
  { key: 'ALLOW', label: 'Allow', color: 'var(--teal)' },
  { key: 'WARN', label: 'Warn', color: 'var(--amber)' },
  { key: 'PAUSE', label: 'Pause', color: 'var(--red)' },
  { key: 'BLOCK', label: 'Block', color: '#BE2A2F' },
];

/** Format an ISO timestamp as `dd/mm HH:mm`; falls back to em dash. */
function formatDateTime(iso: string | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toDecision(raw: InterventionDecision | undefined): Decision {
  return (raw ?? 'allow').toUpperCase() as Decision;
}

/** Map a backend `InterventionEvent` onto the table view model. */
function toRow(dto: InterventionEvent, idx: number, noSignalLabel: string): LogRow {
  return {
    id: dto.id ?? `iv-${idx}`,
    time: formatDateTime(dto.ts),
    signal: dto.signals && dto.signals.length > 0 ? dto.signals.join(', ') : noSignalLabel,
    user: dto.user_id ? dto.user_id.slice(0, 8) : '—',
    decision: toDecision(dto.decision),
  };
}

/** Build a CSV blob from the loaded rows and trigger a download. */
function exportCsv(rows: LogRow[]) {
  const header = ['Thời gian', 'Tín hiệu rủi ro', 'Người dùng', 'Quyết định'];
  const escape = (v: string) => `"${v.replace(/"/g, '""')}"`;
  const body = rows.map((r) => [r.time, r.signal, r.user, r.decision].map(escape).join(','));
  const csv = [header.map(escape).join(','), ...body].join('\n');
  // Prepend a UTF-8 BOM so Excel reads the Vietnamese characters correctly.
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = 'intervention-log.csv';
  a.click();
  URL.revokeObjectURL(url);
}

const GRID = '150px 1fr 130px 110px';

export default function InterventionLogPage() {
  const t = useT();
  const toast = useToast();
  const { data, isLoading, isError, refetch } = useInterventions();

  const noSignalLabel = t('Không có tín hiệu');
  const rows = useMemo<LogRow[]>(
    () => (data ?? []).map((dto, idx) => toRow(dto, idx, noSignalLabel)),
    [data, noSignalLabel],
  );

  const counts = useMemo(() => {
    const c: Record<Decision, number> = { ALLOW: 0, WARN: 0, PAUSE: 0, BLOCK: 0 };
    for (const r of rows) c[r.decision] += 1;
    return c;
  }, [rows]);

  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <header
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            marginBottom: 20,
          }}
        >
          <div>
            <h1 style={pageTitle}>{t('Nhật ký can thiệp · Intervention Log')}</h1>
            <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
              {t('SDK đánh giá rủi ro giao dịch · Phục vụ kiểm toán')}
            </p>
          </div>
          <Button
            type="button"
            variant="secondary"
            disabled={rows.length === 0}
            onClick={() => {
              exportCsv(rows);
              toast(t('Đã xuất {n} dòng', { n: rows.length }));
            }}
          >
            {t('Xuất CSV')}
          </Button>
        </header>

        {/* KPI tiles */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 12,
            marginBottom: 14,
          }}
        >
          {KPI_META.map((k) => (
            <div
              key={k.label}
              style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 10,
                padding: 14,
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  fontFamily: "'Space Grotesk', system-ui",
                  fontSize: 26,
                  fontWeight: 700,
                  color: k.color,
                }}
              >
                {counts[k.key]}
              </div>
              <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 3 }}>{k.label}</div>
            </div>
          ))}
        </div>

        {/* Log table */}
        <div
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: GRID,
              padding: '10px 16px',
              borderBottom: '1px solid var(--border)',
              background: 'var(--bg)',
            }}
          >
            {[t('Thời gian'), t('Tín hiệu rủi ro'), t('Người dùng'), t('Quyết định')].map((h) => (
              <div key={h} style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--muted)' }}>
                {h}
              </div>
            ))}
          </div>

          {isLoading && <TableMessage>{t('Đang tải nhật ký…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              {t('Không tải được dữ liệu.')}{' '}
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <TableMessage>{t('Chưa có can thiệp nào được ghi nhận.')}</TableMessage>
          )}

          {!isLoading &&
            !isError &&
            rows.map((r, idx) => {
            const badge = DECISION_BADGE[r.decision];
            const last = idx === rows.length - 1;
            return (
              <div
                key={r.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: GRID,
                  padding: '12px 16px',
                  borderBottom: last ? 'none' : '1px solid var(--border)',
                  alignItems: 'center',
                }}
              >
                <div style={{ fontSize: 12, fontFamily: "'JetBrains Mono', monospace", color: 'var(--muted)' }}>
                  {r.time}
                </div>
                <div style={{ fontSize: 12.5, color: 'var(--text)' }}>{r.signal}</div>
                <div style={{ fontSize: 12, fontFamily: "'JetBrains Mono', monospace", color: 'var(--muted)' }}>
                  {r.user}
                </div>
                <div>
                  <span
                    style={{
                      background: badge.bg,
                      color: badge.color,
                      borderRadius: 999,
                      padding: '2px 8px',
                      fontSize: 11,
                      fontWeight: 700,
                    }}
                  >
                    {r.decision}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
    </div>
  );
}

function TableMessage({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 13, color: 'var(--muted)' }}>
      {children}
    </div>
  );
}

const inlineRetry: React.CSSProperties = {
  all: 'unset',
  color: 'var(--blue)',
  cursor: 'pointer',
  fontWeight: 600,
};

const pageTitle: React.CSSProperties = {
  fontFamily: "'Space Grotesk', system-ui",
  fontSize: 22,
  fontWeight: 700,
  color: 'var(--text)',
  letterSpacing: '-0.02em',
  margin: 0,
};
