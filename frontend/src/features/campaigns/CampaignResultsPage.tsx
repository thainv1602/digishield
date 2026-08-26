import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useT } from '@/shared/i18n/i18nContext';
import { Button, DataTable, StatusPill, useToast } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import {
  useCampaign,
  useSendCampaign,
  type CampaignResultRow,
  type SendResult,
} from './api';
import { downloadCsv } from '@/shared/lib/csv';
import {
  toFunnel,
  toReported,
  VALUE_FITS_INSIDE_PCT,
  type FunnelBar,
} from './funnel';

/** Absolute origin the tracking links resolve against (the API host). */
function trackOrigin(): string {
  const base = import.meta.env.VITE_API_BASE_URL as string | undefined;
  try {
    if (base && /^https?:\/\//.test(base)) return new URL(base).origin;
  } catch {
    /* fall through to the page origin */
  }
  return window.location.origin;
}

/** QR image URL (backend `/api/v1/qr`) encoding an absolute tracking link. */
function qrSrc(absoluteUrl: string, size = 120): string {
  return `${trackOrigin()}/api/v1/qr?size=${size}&data=${encodeURIComponent(absoluteUrl)}`;
}

/**
 * CampaignResultsPage — completed simulation campaign results.
 * Pixel-matched to the design handoff "CAMPAIGN RESULTS" screen.
 *
 * Data comes from the live backend via `useCampaign(id)`
 * (`GET /sim/campaigns/:id`). The funnel bars and per-user result rows are
 * derived from the response; loading/error/empty states handled inline.
 */

/**
 * One labelled bar.
 *
 * The bar's length is the value and nothing else. The previous version gave the
 * short bars a `minWidth` so the number printed inside them stayed readable,
 * which meant a 4% bar and a 9% bar could be drawn the same length — the one
 * thing a bar chart is supposed to get right. Now a value that does not fit
 * sits outside the bar in ink instead, and the bar keeps its true length.
 */
function FunnelRow({ bar, label }: { bar: FunnelBar; label: string }) {
  const insideBar = bar.width >= VALUE_FITS_INSIDE_PCT;
  const value = (
    <span
      style={{
        fontSize: 13,
        fontWeight: 700,
        color: insideBar ? 'white' : 'var(--color-text)',
        fontFamily: "'JetBrains Mono', monospace",
        whiteSpace: 'nowrap',
      }}
    >
      {bar.value}
    </span>
  );
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
      <div
        style={{
          width: 100,
          fontSize: 13,
          color: 'var(--color-muted)',
          textAlign: 'right',
          flexShrink: 0,
        }}
      >
        {label}
      </div>
      <div
        style={{
          flex: 1,
          background: 'var(--color-bg)',
          borderRadius: 4,
          height: 28,
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <div
          style={{
            background: bar.color,
            height: '100%',
            width: `${bar.width}%`,
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            paddingLeft: insideBar ? 12 : 0,
            flexShrink: 0,
          }}
        >
          {insideBar && value}
        </div>
        {!insideBar && <div style={{ paddingLeft: 8 }}>{value}</div>}
      </div>
      <div
        style={{
          width: 44,
          textAlign: 'right',
          fontSize: 12,
          color: bar.pctColor,
          fontWeight: bar.pctColor === 'var(--color-muted)' ? 400 : 600,
        }}
      >
        {bar.pctText}
      </div>
    </div>
  );
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
  const toast = useToast();
  const { id } = useParams<{ id: string }>();
  const { data: detail, isLoading, isError, refetch } = useCampaign(id);

  const sendMut = useSendCampaign();
  const [sendResult, setSendResult] = useState<SendResult | null>(null);
  const status = (detail?.status ?? '').toLowerCase();
  // Show "send" until the campaign is actively running or finished.
  const canSend = Boolean(id) && (status === 'draft' || status === 'scheduled' || status === '');

  function handleSend() {
    if (!id) return;
    sendMut.mutate(
      { campaignId: id, groupId: detail?.groupId ?? null },
      {
        onSuccess: (res) => {
          setSendResult(res);
          toast(t('Đã gửi tới {n} người nhận', { n: res.recipientCount }));
          refetch();
        },
        onError: () => toast(t('Gửi mô phỏng thất bại, thử lại')),
      },
    );
  }

  const funnel = toFunnel(detail);
  const reported = toReported(detail);
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {canSend && (
              <Button
                type="button"
                variant="primary"
                disabled={sendMut.isPending}
                onClick={handleSend}
              >
                {sendMut.isPending ? t('Đang gửi…') : t('Gửi mô phỏng')}
              </Button>
            )}
            <StatusPill variant={isCompleted ? 'safe' : 'warning'} dot>
              {isCompleted ? t('Đã hoàn thành') : detail?.status ?? '—'}
            </StatusPill>
          </div>
        </div>

        {/* Tracking links after a send (no real MTA wired — click to simulate) */}
        {sendResult && (
          <div style={{ ...cardStyle, padding: 20, marginBottom: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 6 }}>
              {t('Đã gửi tới {n} người nhận', { n: sendResult.recipientCount })}
            </div>
            <div style={{ fontSize: 12.5, color: 'var(--color-muted)', marginBottom: 12 }}>
              {t('Đã gửi link qua kênh của chiến dịch (email/SMS) tới người nhận. Nếu chưa cấu hình nhà cung cấp, hệ thống chạy mô phỏng — mở link/QR dưới đây để thử ghi nhận sự kiện Bấm.')}
            </div>
            {sendResult.recipients.length === 0 ? (
              <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
                {t('Nhóm mục tiêu chưa có thành viên nào.')}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {sendResult.recipients.map((r) => {
                  const absoluteUrl = `${trackOrigin()}${r.trackUrl}`;
                  const isQr = (detail?.channel ?? '').toLowerCase() === 'qr';
                  return (
                    <div
                      key={r.token}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 12,
                        padding: 10,
                        border: '1px solid var(--color-border)',
                        borderRadius: 8,
                      }}
                    >
                      <a href={absoluteUrl} target="_blank" rel="noopener noreferrer" aria-label={t('Mở liên kết mô phỏng')}>
                        <img
                          src={qrSrc(absoluteUrl, isQr ? 132 : 96)}
                          width={isQr ? 88 : 56}
                          height={isQr ? 88 : 56}
                          alt={t('Mã QR mô phỏng')}
                          style={{ display: 'block', border: '1px solid var(--color-border)', borderRadius: 4 }}
                        />
                      </a>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 12.5, color: 'var(--color-text)', fontWeight: 600, marginBottom: 2 }}>
                          {r.userId}
                        </div>
                        <a
                          href={absoluteUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            fontSize: 12,
                            color: 'var(--color-blue)',
                            fontFamily: "'JetBrains Mono', monospace",
                            wordBreak: 'break-all',
                          }}
                        >
                          {isQr ? t('Quét QR hoặc mở liên kết mô phỏng') : t('Mở liên kết mô phỏng')}
                        </a>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

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
              <FunnelRow key={f.label} bar={f} label={t(f.label)} />
            ))}
          </div>

          {/* Reporting sits below a rule, outside the funnel: it is a separate
              outcome, not a later stage, so it must not read as one. */}
          <div
            style={{
              marginTop: 16,
              paddingTop: 16,
              borderTop: '1px solid var(--color-border)',
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}
          >
            <div style={{ ...labelStyle, marginLeft: 114 }}>{t('Kết quả tích cực')}</div>
            <FunnelRow bar={reported} label={t(reported.label)} />
            <div style={{ fontSize: 12, color: 'var(--color-muted)', marginLeft: 114 }}>
              {t('Tính trên số email đã gửi. Một người có thể báo cáo mà không mở hoặc bấm, nên đây không phải chặng tiếp theo của phễu.')}
            </div>
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
              onClick={() =>
                downloadCsv(
                  'campaign-results.csv',
                  [t('Tên'), t('Phòng ban'), t('Hành động'), t('Học tập')],
                  rows.map((r) => [r.name, r.dept, r.action, r.learning]),
                )
              }
              disabled={rows.length === 0}
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
