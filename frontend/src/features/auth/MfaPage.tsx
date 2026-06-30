import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Check } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import { ROLES, defaultRouteForRole } from '@/app/auth/roles';
import { OtpInput } from '@/shared/ui';
import { AuthScreen, AuthCard } from './authShared';
import { useT } from '@/shared/i18n/I18nProvider';

/** MFA — 6-box OTP + trust-device checkbox + confirm. */
export default function MfaPage() {
  const navigate = useNavigate();
  const t = useT();
  const { login } = useAuth();
  const [code, setCode] = useState('384');
  const [trust, setTrust] = useState(true);

  function confirm() {
    const role = ROLES.ORG_ADMIN;
    login({ id: 'me', tenantId: 'tenant-demo', role, name: 'Nguyễn Tuấn' }, 'demo-token');
    navigate(defaultRouteForRole(role));
  }

  return (
    <AuthScreen>
      <div style={{ width: 380, animation: 'fadeUp .4s ease' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 56,
              height: 56,
              borderRadius: '50%',
              background: 'rgba(237,169,44,.12)',
              border: '2px solid rgba(237,169,44,.3)',
              marginBottom: 14,
            }}
          >
            <ShieldCheck size={24} strokeWidth={2} color="var(--color-amber)" />
          </div>
          <h1 style={{ fontSize: 22, marginBottom: 6 }}>{t('Xác thực 2 lớp · MFA')}</h1>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
            {t('Nhập mã 6 chữ số từ ứng dụng authenticator')}
          </div>
        </div>

        <AuthCard>
          <div style={{ marginBottom: 24 }}>
            <OtpInput value={code} onChange={setCode} />
          </div>

          <button
            type="button"
            onClick={() => setTrust((v) => !v)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 20,
              cursor: 'pointer',
              background: 'transparent',
              border: 'none',
              padding: 0,
            }}
          >
            <span
              style={{
                width: 18,
                height: 18,
                borderRadius: 4,
                background: trust ? 'rgba(37,102,235,.2)' : 'transparent',
                border: `1.5px solid ${trust ? 'var(--color-blue)' : 'var(--color-input-border)'}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {trust ? <Check size={11} strokeWidth={3} color="var(--color-blue)" /> : null}
            </span>
            <span style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
              {t('Tin thiết bị này trong 30 ngày')}
            </span>
          </button>

          <button
            type="button"
            onClick={confirm}
            style={{
              width: '100%',
              background: 'var(--color-blue)',
              color: '#fff',
              border: 'none',
              borderRadius: 9,
              padding: 12,
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: 15,
              marginBottom: 12,
            }}
          >
            {t('Xác nhận')}
          </button>

          <div
            style={{
              textAlign: 'center',
              fontSize: 13,
              color: 'var(--color-muted)',
              cursor: 'pointer',
            }}
          >
            {t('Dùng phương thức khác (SMS / email)')}
          </div>
        </AuthCard>
      </div>
    </AuthScreen>
  );
}
