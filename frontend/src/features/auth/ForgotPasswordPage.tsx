import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Check } from 'lucide-react';
import { AuthScreen, AuthCard, authInputStyle, authLabelStyle } from './authShared';
import { useT } from '@/shared/i18n/I18nProvider';

/** Forgot Password — email input + reset link, with inline success banner. */
export default function ForgotPasswordPage() {
  const t = useT();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@coquan.gov.vn');
  const [sent, setSent] = useState(false);

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
              background: 'rgba(37,102,235,.12)',
              border: '2px solid rgba(37,102,235,.3)',
              marginBottom: 14,
            }}
          >
            <Lock size={24} strokeWidth={2} color="var(--color-blue)" />
          </div>
          <h1 style={{ fontSize: 22, marginBottom: 6 }}>{t('Đặt lại mật khẩu')}</h1>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
            {t('Nhập email để nhận link đặt lại mật khẩu')}
          </div>
        </div>

        <AuthCard>
          <div style={{ marginBottom: 20 }}>
            <label htmlFor="fp-email" style={authLabelStyle}>
              {t('Email công việc')}
            </label>
            <input
              id="fp-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              style={authInputStyle}
            />
          </div>

          <button
            type="button"
            onClick={() => setSent(true)}
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
              marginBottom: 16,
            }}
          >
            {t('Gửi link đặt lại mật khẩu')}
          </button>

          {sent ? (
            <div
              style={{
                background: 'var(--tint-teal)',
                border: '1px solid rgba(24,147,92,.25)',
                borderRadius: 8,
                padding: '12px 16px',
                fontSize: 13,
                color: 'var(--color-teal)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <Check size={16} strokeWidth={2.5} />
              {t('Link đã gửi tới {email} — kiểm tra hộp thư', { email })}
            </div>
          ) : null}
        </AuthCard>

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <span
            onClick={() => navigate('/login')}
            style={{ fontSize: 13, color: 'var(--color-muted)', cursor: 'pointer' }}
          >
            {t('← Quay lại đăng nhập')}
          </span>
        </div>
      </div>
    </AuthScreen>
  );
}
