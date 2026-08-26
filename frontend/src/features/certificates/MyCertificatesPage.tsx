import { Link } from 'react-router-dom';
import { Award } from 'lucide-react';
import { useT } from '@/shared/i18n/i18nContext';
import { useAuth } from '@/app/auth/useAuth';
import { useUserCertificates } from '../learning/api';

/** Short date label (best effort). */
function fmtDate(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('vi-VN');
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};

/**
 * MyCertificatesPage — the signed-in learner's certificates (`/certificates`).
 * Lists the user's certificates (`GET /users/{id}/certificates`); each links to
 * the single-certificate view (`/certificates/{id}`). Replaces the old nav entry
 * that pointed at a hard-coded `/certificates/1` and always errored.
 */
export default function MyCertificatesPage() {
  const t = useT();
  const { user } = useAuth();
  const { data, isLoading, isError, refetch } = useUserCertificates(user?.id);
  const rows = data ?? [];

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
          {t('Chứng chỉ của tôi')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
          {t('Chứng chỉ hoàn thành các khóa học của bạn')}
        </div>
      </div>

      {isLoading && <Msg>{t('Đang tải chứng chỉ…')}</Msg>}
      {!isLoading && isError && (
        <Msg>
          <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
            {t('Không tải được chứng chỉ.')}{' '}
          </span>
          <button type="button" onClick={() => refetch()} style={retry}>
            {t('Thử lại')}
          </button>
        </Msg>
      )}
      {!isLoading && !isError && rows.length === 0 && (
        <Msg>{t('Bạn chưa có chứng chỉ nào. Hoàn thành một khóa học để nhận chứng chỉ.')}</Msg>
      )}

      {!isLoading && !isError && rows.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {rows.map((c) => (
            <Link
              key={c.id}
              to={`/certificates/${c.id}`}
              style={{ ...cardStyle, padding: 18, display: 'flex', alignItems: 'center', gap: 14, textDecoration: 'none' }}
            >
              <Award size={28} color="var(--color-amber)" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                  {t('Chứng chỉ')} · {c.serial ?? c.id.slice(0, 8)}
                </div>
                <div style={{ fontSize: 12.5, color: 'var(--color-muted)' }}>
                  {t('Cấp ngày')} {fmtDate(c.issued_at)}
                </div>
              </div>
              <span style={{ fontSize: 12.5, color: 'var(--color-blue)', fontWeight: 600 }}>
                {t('Xem')} →
              </span>
            </Link>
          ))}
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
