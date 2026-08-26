import { Mail, MessageSquare, ShieldAlert } from 'lucide-react';
import { useT } from '@/shared/i18n/i18nContext';
import { useAuth } from '@/app/auth/useAuth';
import { useMyReports, type MyReport } from './api';

/**
 * MyReportsPage — the learner's own phishing reports (`/learn/reports`).
 *
 * After a learner submits a report from the portal CTA it goes to the SOC
 * inbox; this screen lets the reporter track what happened to it (status +
 * channel), backed by `GET /reports/phishing/mine/{userId}`.
 */

type StatusMeta = { label: string; bg: string; color: string };

/** Status pill labels + colors (VI keys translated via t()). */
function statusMeta(status: string | null, t: ReturnType<typeof useT>): StatusMeta {
  switch (status) {
    case 'confirmed':
      return { label: t('Xác nhận đe dọa'), bg: '#DDF3E6', color: '#0F7A4A' };
    case 'dismissed':
      return { label: t('Không phải đe dọa'), bg: 'var(--color-bg)', color: 'var(--color-muted)' };
    case 'triaging':
      return { label: t('Đang xử lý'), bg: '#FCEBCF', color: '#C0720A' };
    case 'submitted':
    default:
      return { label: t('Đã gửi'), bg: 'rgba(37,102,235,.10)', color: 'var(--color-blue)' };
  }
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};

export default function MyReportsPage() {
  const t = useT();
  const { user } = useAuth();
  const { data, isLoading, isError, refetch } = useMyReports(user?.id);
  const rows: MyReport[] = data ?? [];

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 720 }}>
      <div style={{ marginBottom: 24 }}>
        <div
          style={{
            fontFamily: "'Space Grotesk', system-ui",
            fontSize: 22,
            fontWeight: 700,
            color: 'var(--color-text)',
            letterSpacing: '-.02em',
          }}
        >
          {t('Báo cáo của tôi')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
          {t('Trạng thái các báo cáo lừa đảo bạn đã gửi')}
        </div>
      </div>

      {isLoading && <Msg>{t('Đang tải báo cáo…')}</Msg>}
      {!isLoading && isError && (
        <Msg>
          <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
            {t('Không tải được báo cáo.')}{' '}
          </span>
          <button type="button" onClick={() => refetch()} style={retry}>
            {t('Thử lại')}
          </button>
        </Msg>
      )}
      {!isLoading && !isError && rows.length === 0 && (
        <Msg>{t('Bạn chưa gửi báo cáo nào.')}</Msg>
      )}

      {!isLoading && !isError && rows.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {rows.map((r) => {
            const meta = statusMeta(r.status, t);
            const ChannelIcon = r.channel === 'sms' ? MessageSquare : Mail;
            const preview = (r.payload ?? '').trim().replace(/\s+/g, ' ').slice(0, 120);
            return (
              <div key={r.id} style={{ ...cardStyle, padding: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <ShieldAlert size={18} color="var(--color-red)" />
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 5,
                      fontSize: 12,
                      color: 'var(--color-muted)',
                    }}
                  >
                    <ChannelIcon size={13} /> {r.channel === 'sms' ? t('SMS') : t('Email')}
                  </span>
                  {r.ageLabel && (
                    <span style={{ fontSize: 12, color: 'var(--color-muted)' }}>· {r.ageLabel}</span>
                  )}
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 11.5,
                      fontWeight: 700,
                      padding: '3px 10px',
                      borderRadius: 999,
                      background: meta.bg,
                      color: meta.color,
                    }}
                  >
                    {meta.label}
                  </span>
                </div>
                <div style={{ fontSize: 13, color: 'var(--color-text)', lineHeight: 1.5 }}>
                  {preview || t('(không có nội dung)')}
                  {(r.payload ?? '').length > 120 ? '…' : ''}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Msg({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ ...cardStyle, padding: '28px 20px', textAlign: 'center', fontSize: 13.5, color: 'var(--color-muted)' }}>
      {children}
    </div>
  );
}

const retry: React.CSSProperties = {
  all: 'unset',
  color: 'var(--color-blue)',
  cursor: 'pointer',
  fontWeight: 600,
};
