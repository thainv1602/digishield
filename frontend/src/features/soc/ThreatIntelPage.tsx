import { useMemo } from 'react';
import { Button, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useThreatIntel, useConvertThreatIntel, type ThreatIntel } from './api';

/**
 * ThreatIntelPage — threat intelligence feed (`/soc/threat-intel`).
 *
 * A table of incoming threats with source, status badge, detection date and a
 * ThreatFlip action that turns an item into a coaching lesson.
 *
 * Data comes from the live backend via `useThreatIntel()` (`GET /threat-intel`);
 * the ThreatFlip action calls `useConvertThreatIntel()`
 * (`POST /threat-intel/{id}/convert`). Loading/error/empty states handled below.
 */

type TiStatus = 'flipped' | 'new';

interface ThreatRow {
  id: string;
  title: string;
  meta: string;
  source: string;
  status: TiStatus;
  detectedAt: string;
}

const STATUS_BADGE: Record<TiStatus, { label: string; bg: string; color: string }> = {
  flipped: { label: 'Đã chuyển bài học', bg: '#DDF3E6', color: '#0F7A4A' },
  new: { label: 'Mới nhận', bg: 'rgba(105,120,143,.12)', color: '#69788F' },
};

/** Format an ISO timestamp as dd/mm/yyyy; falls back to em dash. */
function formatDate(iso: string | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('vi-VN');
}

/** Map a backend `ThreatIntel` record onto the table view model. */
function toRow(dto: ThreatIntel, idx: number, t: (key: string, vars?: Record<string, string | number>) => string): ThreatRow {
  const payload = (dto.raw_payload ?? '').trim();
  return {
    id: dto.id ?? `ti-${idx}`,
    title: payload.length > 0 ? payload.slice(0, 120) : t('Mối đe dọa chưa có mô tả'),
    meta: dto.source ? t('Nguồn: {source}', { source: dto.source }) : 'Threat intel',
    source: dto.source ?? '—',
    status: dto.converted_template_id ? 'flipped' : 'new',
    detectedAt: formatDate(dto.collected_at),
  };
}

const GRID = '1fr 100px 120px 120px 100px';

export default function ThreatIntelPage() {
  const t = useT();
  const toast = useToast();
  const { data, isLoading, isError, refetch } = useThreatIntel();
  const convert = useConvertThreatIntel();

  const rows = useMemo<ThreatRow[]>(() => (data ?? []).map((dto, idx) => toRow(dto, idx, t)), [data, t]);

  function onThreatFlip(id: string) {
    convert.mutate(id, {
      onSuccess: () => toast(t('ThreatFlip đã tạo bài học')),
      onError: () => toast(t('ThreatFlip thất bại, thử lại')),
    });
  }

  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <header style={{ marginBottom: 20 }}>
          <h1 style={pageTitle}>Threat Intelligence</h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
            {t('Nguồn tình báo mối đe dọa · ThreatFlip để biến thành bài học')}
          </p>
        </header>

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
            {[t('Mối đe dọa'), t('Nguồn'), t('Trạng thái'), t('Phát hiện')].map((h) => (
              <div key={h} style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--muted)' }}>
                {h}
              </div>
            ))}
            <div />
          </div>

          {isLoading && <TableMessage>{t('Đang tải threat intel…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              {t('Không tải được dữ liệu.')}{' '}
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <TableMessage>{t('Chưa có threat intel nào.')}</TableMessage>
          )}

          {!isLoading &&
            !isError &&
            rows.map((r, idx) => {
            const badge = STATUS_BADGE[r.status];
            const last = idx === rows.length - 1;
            return (
              <div
                key={r.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: GRID,
                  padding: '13px 16px',
                  borderBottom: last ? 'none' : '1px solid var(--border)',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--text)', marginBottom: 3 }}>
                    {r.title}
                  </div>
                  <div style={{ fontSize: 11.5, color: 'var(--muted)' }}>{r.meta}</div>
                </div>
                <div style={{ fontSize: 12.5, color: 'var(--muted)' }}>{r.source}</div>
                <div>
                  <span
                    style={{
                      background: badge.bg,
                      color: badge.color,
                      borderRadius: 999,
                      padding: '2px 8px',
                      fontSize: 11,
                      fontWeight: 600,
                    }}
                  >
                    {t(badge.label)}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>{r.detectedAt}</div>
                <div>
                  {r.status === 'flipped' ? (
                    <span style={{ fontSize: 12, color: 'var(--teal)', fontWeight: 600 }}>
                      {t('✓ Đã tạo bài học')}
                    </span>
                  ) : (
                    <Button
                      type="button"
                      variant="primary"
                      size="sm"
                      disabled={convert.isPending}
                      onClick={() => onThreatFlip(r.id)}
                    >
                      ThreatFlip
                    </Button>
                  )}
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
