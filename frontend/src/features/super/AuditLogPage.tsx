import { useMemo, useState } from 'react';
import { Button, DataTable, Select } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useAuditLogs, type AuditLogEntry } from './api';
import { useT } from '@/shared/i18n/I18nProvider';
import { downloadCsv } from '@/shared/lib/csv';

/**
 * AuditLogPage — Super Admin sensitive-action audit trail.
 * Pixel-matched to the design handoff "AUDIT LOG" screen.
 *
 * Data comes from the live backend via `useAuditLogs()` (`GET /audit`).
 * Loading/error/empty states handled below.
 */

// Action severity → badge color. critical=red, sensitive=amber, standard=gray.
type Severity = 'critical' | 'sensitive' | 'standard';
const severityStyle: Record<Severity, { bg: string; color: string }> = {
  critical: { bg: 'var(--pill-threat-bg)', color: 'var(--pill-threat-fg)' },
  sensitive: { bg: 'var(--pill-warning-bg)', color: 'var(--pill-warning-fg)' },
  standard: { bg: 'rgba(105,120,143,.12)', color: 'var(--color-muted)' },
};

function normalizeSeverity(value: string | null): Severity {
  return value === 'critical' || value === 'sensitive' || value === 'standard' ? value : 'standard';
}

type AuditRow = {
  id: string;
  time: string;
  actor: string;
  action: string;
  severity: Severity;
  object: string;
  ip: string;
};

/** Map a backend `AuditLogView` onto the table view model. */
function toRow(dto: AuditLogEntry): AuditRow {
  let time = '';
  if (dto.ts) {
    const when = new Date(dto.ts);
    time = Number.isNaN(when.getTime())
      ? dto.ts
      : `${when.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })} ${when.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
  }
  return {
    id: dto.id,
    time,
    actor: dto.actor ?? '—',
    action: dto.action ?? '—',
    severity: normalizeSeverity(dto.severity),
    object: dto.target ?? '—',
    ip: dto.ip ?? '—',
  };
}

const monoMuted: React.CSSProperties = {
  fontSize: 12,
  fontFamily: "'JetBrains Mono', monospace",
  color: 'var(--color-muted)',
};

export default function AuditLogPage() {
  const t = useT();
  const { data, isLoading, isError, refetch } = useAuditLogs();

  const allRows = useMemo<AuditRow[]>(() => (data ?? []).map(toRow), [data]);

  const [actorF, setActorF] = useState('');
  const [actionF, setActionF] = useState('');
  const [dateF, setDateF] = useState('');

  const actorOptions = useMemo(
    () => Array.from(new Set(allRows.map((r) => r.actor).filter((v) => v && v !== '—'))).sort(),
    [allRows],
  );
  const actionOptions = useMemo(
    () => Array.from(new Set(allRows.map((r) => r.action).filter((v) => v && v !== '—'))).sort(),
    [allRows],
  );
  const dateOptions = useMemo(
    () => Array.from(new Set(allRows.map((r) => r.time.split(' ')[0]).filter(Boolean))).sort(),
    [allRows],
  );

  const rows = useMemo(
    () =>
      allRows.filter((r) => {
        if (actorF && r.actor !== actorF) return false;
        if (actionF && r.action !== actionF) return false;
        if (dateF && !r.time.startsWith(dateF)) return false;
        return true;
      }),
    [allRows, actorF, actionF, dateF],
  );

  const exportCsv = () =>
    downloadCsv(
      'audit-log.csv',
      [t('Thời gian'), t('Người thực hiện'), t('Hành động'), t('Đối tượng'), 'IP'],
      rows.map((r) => [r.time, r.actor, r.action, r.object, r.ip]),
    );

  const columns: ColumnDef<AuditRow>[] = [
    { id: 'time', header: t('Thời gian'), cell: (r) => <span style={monoMuted}>{r.time}</span>, width: '140px' },
    {
      id: 'actor',
      header: t('Người thực hiện'),
      cell: (r) => <span style={{ fontSize: 13, color: 'var(--color-text)' }}>{r.actor}</span>,
    },
    {
      id: 'action',
      header: t('Hành động'),
      cell: (r) => {
        const s = severityStyle[r.severity];
        return (
          <span
            style={{
              background: s.bg,
              color: s.color,
              borderRadius: 99,
              padding: '2px 8px',
              fontSize: 11,
              fontWeight: 600,
            }}
          >
            {r.action}
          </span>
        );
      },
      width: '180px',
    },
    {
      id: 'object',
      header: t('Đối tượng'),
      cell: (r) => <span style={{ fontSize: 12.5, color: 'var(--color-muted)' }}>{r.object}</span>,
      width: '140px',
    },
    { id: 'ip', header: 'IP', cell: (r) => <span style={monoMuted}>{r.ip}</span>, width: '100px' },
  ];

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 20,
          }}
        >
          <div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 22,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.02em',
              }}
            >
              {t('Audit Log')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {t('Toàn bộ hành động nhạy cảm · Phục vụ điều tra & tuân thủ')}
            </div>
          </div>
          <Button variant="outline" onClick={exportCsv} disabled={rows.length === 0}>
            {t('Xuất CSV')}
          </Button>
        </div>

        {/* Filter bar */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
          <Select
            aria-label={t('Lọc theo người thực hiện')}
            value={actorF}
            onChange={(e) => setActorF(e.target.value)}
          >
            <option value="">{t('Người thực hiện')}</option>
            {actorOptions.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
          <Select
            aria-label={t('Lọc theo hành động')}
            value={actionF}
            onChange={(e) => setActionF(e.target.value)}
          >
            <option value="">{t('Hành động')}</option>
            {actionOptions.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
          <Select aria-label={t('Lọc theo ngày')} value={dateF} onChange={(e) => setDateF(e.target.value)}>
            <option value="">{t('Tất cả ngày')}</option>
            {dateOptions.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </Select>
        </div>

        {/* Audit table */}
        <div
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          {isLoading && <TableMessage>{t('Đang tải nhật ký…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được nhật ký kiểm toán. ')}</span>
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <TableMessage>{t('Chưa có bản ghi nào.')}</TableMessage>
          )}
          {!isLoading && !isError && rows.length > 0 && (
            <DataTable<AuditRow> columns={columns} data={rows} rowKey={(r) => r.id} />
          )}
        </div>
      </div>
    </>
  );
}

function TableMessage({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ padding: '28px 16px', textAlign: 'center', fontSize: 13.5, color: 'var(--color-muted)' }}>
      {children}
    </div>
  );
}

const inlineRetry: React.CSSProperties = {
  background: 'none',
  border: 'none',
  color: 'var(--color-blue)',
  fontWeight: 600,
  fontSize: 13.5,
  cursor: 'pointer',
  padding: 0,
};
