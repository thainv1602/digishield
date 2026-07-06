import { useState } from 'react';
import { useToast } from '@/shared/ui';
import { useNotifications, useBroadcastAlert, type Notification } from '@/features/notifications/api';
import { useT } from '@/shared/i18n/I18nProvider';

/**
 * AlertCenterPage — broadcast composer + history (`/soc/alerts`).
 *
 * Compose form (severity select + textarea + red Broadcast button) and a
 * broadcast history list (severity badge + reach count).
 *
 * The history list is fed by the live backend via `useNotifications()`
 * (`GET /notifications`); the compose form broadcasts via `useBroadcastAlert()`
 * (`POST /alerts/broadcast`), which fans the alert out to every tenant user.
 */

type Severity = 'CRITICAL' | 'WARNING' | 'INFO';

interface BroadcastRecord {
  id: string;
  severity: Severity;
  at: string;
  message: string;
  reach: string;
}

const SEVERITY_OPTIONS: { value: Severity; label: string }[] = [
  { value: 'CRITICAL', label: 'CRITICAL — Đe dọa đang hoạt động' },
  { value: 'WARNING', label: 'WARNING — Cần chú ý' },
  { value: 'INFO', label: 'INFO — Thông báo chung' },
];

const SEVERITY_BADGE: Record<Severity, { bg: string; color: string }> = {
  CRITICAL: { bg: '#FBE0DF', color: '#BE2A2F' },
  WARNING: { bg: '#FCEBCF', color: '#C0720A' },
  INFO: { bg: 'rgba(37,102,235,.1)', color: '#1A4FD0' },
};

/** Map a notification `type` (alert|reminder|system) onto a severity badge. */
function typeToSeverity(type: string | null | undefined): Severity {
  if (type === 'alert') return 'CRITICAL';
  if (type === 'reminder') return 'WARNING';
  return 'INFO';
}

/** Format an ISO instant as `DD/MM HH:mm` (best effort). */
function formatAt(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Map a backend `NotificationView` onto a history row. */
function toRecord(n: Notification, t: (s: string) => string): BroadcastRecord {
  const channel = n.channel ?? 'in_app';
  const status = n.status ?? '';
  return {
    id: n.id,
    severity: typeToSeverity(n.type),
    at: formatAt(n.scheduledAt),
    message: n.title ?? n.body ?? t('(không có nội dung)'),
    reach: [channel, status].filter(Boolean).join(' · '),
  };
}

export default function AlertCenterPage() {
  const t = useT();
  const toast = useToast();
  const [severity, setSeverity] = useState<Severity>('CRITICAL');
  const [message, setMessage] = useState('');
  const { data, isLoading, isError, refetch } = useNotifications();
  const broadcastMut = useBroadcastAlert();

  const history = (data ?? []).map((n) => toRecord(n, t));

  function broadcast() {
    if (!message.trim()) {
      toast(t('Vui lòng nhập nội dung cảnh báo.'));
      return;
    }
    broadcastMut.mutate(
      { message: message.trim(), severity: severity.toLowerCase() as 'info' | 'warning' | 'critical' },
      {
        onSuccess: (res) => {
          toast(t('Đã phát cảnh báo {severity} tới {reach} người.', { severity, reach: res.reach }));
          setMessage('');
        },
        onError: () => toast(t('Không phát được cảnh báo, thử lại.')),
      },
    );
  }

  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <header style={{ marginBottom: 20 }}>
          <h1 style={pageTitle}>{t('Trung tâm cảnh báo · Alert Center')}</h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
            {t('Soạn và phát cảnh báo tới mọi người dùng trong tổ chức')}
          </p>
        </header>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {/* Compose form */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              broadcast();
            }}
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 12,
              padding: 24,
            }}
          >
            <h2 style={{ fontSize: 15, fontWeight: 600, color: 'var(--text)', margin: '0 0 20px' }}>
              {t('Soạn cảnh báo mới')}
            </h2>

            <div style={{ marginBottom: 14 }}>
              <label htmlFor="alert-severity" style={fieldLabel}>
                {t('Mức độ')}
              </label>
              <select
                id="alert-severity"
                value={severity}
                onChange={(e) => setSeverity(e.target.value as Severity)}
                style={fieldControl}
              >
                {SEVERITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {t(o.label)}
                  </option>
                ))}
              </select>
            </div>

            <div style={{ marginBottom: 14 }}>
              <label htmlFor="alert-message" style={fieldLabel}>
                {t('Nội dung cảnh báo')}
              </label>
              <textarea
                id="alert-message"
                rows={4}
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder={t('Mô tả mối đe dọa, hướng dẫn xử lý...')}
                style={{ ...fieldControl, resize: 'none' }}
              />
            </div>

            <button
              type="submit"
              disabled={broadcastMut.isPending}
              style={{
                width: '100%',
                background: 'var(--red)',
                color: '#fff',
                border: 'none',
                borderRadius: 9,
                padding: 13,
                textAlign: 'center',
                fontWeight: 700,
                fontSize: 15,
                cursor: broadcastMut.isPending ? 'default' : 'pointer',
                opacity: broadcastMut.isPending ? 0.7 : 1,
              }}
            >
              {broadcastMut.isPending ? t('Đang phát…') : t('Phát cảnh báo toàn tổ chức')}
            </button>
          </form>

          {/* History */}
          <section
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 12,
              padding: 24,
            }}
          >
            <h2 style={{ fontSize: 15, fontWeight: 600, color: 'var(--text)', margin: '0 0 16px' }}>
              {t('Lịch sử phát · Broadcast History')}
            </h2>
            {isLoading && (
              <div style={{ fontSize: 13.5, color: 'var(--muted)' }}>{t('Đang tải lịch sử…')}</div>
            )}
            {!isLoading && isError && (
              <div style={{ fontSize: 13.5, color: 'var(--muted)' }}>
                <span style={{ color: 'var(--red)', fontWeight: 600 }}>{t('Không tải được lịch sử. ')}</span>
                <button
                  type="button"
                  onClick={() => refetch()}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--blue)',
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
            {!isLoading && !isError && history.length === 0 && (
              <div style={{ fontSize: 13.5, color: 'var(--muted)' }}>{t('Chưa có thông báo nào.')}</div>
            )}
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 10 }}>
              {history.map((rec) => {
                const badge = SEVERITY_BADGE[rec.severity];
                const critical = rec.severity === 'CRITICAL';
                return (
                  <li
                    key={rec.id}
                    style={{
                      background: critical ? 'rgba(221,59,64,.07)' : 'var(--bg)',
                      border: critical
                        ? '1px solid rgba(221,59,64,.15)'
                        : '1px solid var(--border)',
                      borderRadius: 10,
                      padding: 14,
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        marginBottom: 6,
                      }}
                    >
                      <span
                        style={{
                          background: badge.bg,
                          color: badge.color,
                          borderRadius: 999,
                          padding: '2px 8px',
                          fontSize: 10.5,
                          fontWeight: 700,
                        }}
                      >
                        {rec.severity}
                      </span>
                      <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>{rec.at}</span>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--text)', marginBottom: 3 }}>
                      {rec.message}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--muted)' }}>{rec.reach}</div>
                  </li>
                );
              })}
            </ul>
          </section>
        </div>
    </div>
  );
}

const pageTitle: React.CSSProperties = {
  fontFamily: "'Space Grotesk', system-ui",
  fontSize: 22,
  fontWeight: 700,
  color: 'var(--text)',
  letterSpacing: '-0.02em',
  margin: 0,
};

const fieldLabel: React.CSSProperties = {
  display: 'block',
  fontSize: 13,
  fontWeight: 500,
  color: 'var(--muted)',
  marginBottom: 6,
};

const fieldControl: React.CSSProperties = {
  width: '100%',
  background: 'var(--bg)',
  border: '1px solid var(--input-bdr, #C0CBDC)',
  borderRadius: 8,
  padding: '10px 14px',
  color: 'var(--text)',
  fontSize: 14,
  fontFamily: 'inherit',
  boxSizing: 'border-box',
};
