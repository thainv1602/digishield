import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/app/auth/useAuth';
import { ROLES, defaultRouteForRole } from '@/app/auth/roles';
import { AuthScreen, AuthCard, authInputStyle, authLabelStyle } from './authShared';
import { useT } from '@/shared/i18n/I18nProvider';

const IDPS = ['Microsoft Entra', 'Google Workspace', 'SAML 2.0'];

/** SSO — org domain input + redirect spinner + supported IdP pills. */
export default function SsoPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const t = useT();
  const [domain, setDomain] = useState('coquan.gov.vn');

  function continueSso() {
    const role = ROLES.ORG_ADMIN;
    login({ id: 'me', tenantId: 'tenant-demo', role, name: 'Nguyễn Tuấn' }, 'demo-sso-token');
    navigate(defaultRouteForRole(role));
  }

  return (
    <AuthScreen>
      <div style={{ width: 400, animation: 'fadeUp .4s ease' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <h1 style={{ fontSize: 22, marginBottom: 6 }}>{t('Đăng nhập bằng SSO')}</h1>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
            {t('Nhập tên miền tổ chức của bạn')}
          </div>
        </div>

        <AuthCard>
          <div style={{ marginBottom: 16 }}>
            <label htmlFor="sso-domain" style={authLabelStyle}>
              {t('Tên miền tổ chức')}
            </label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                id="sso-domain"
                type="text"
                value={domain}
                onChange={(e) => setDomain(e.target.value)}
                style={{ ...authInputStyle, flex: 1 }}
              />
              <button
                type="button"
                onClick={continueSso}
                style={{
                  background: 'var(--color-blue)',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 8,
                  padding: '10px 16px',
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                {t('Tiếp tục')}
              </button>
            </div>
          </div>

          <div
            style={{
              background: 'var(--tint-blue-soft)',
              border: '1px solid rgba(37,102,235,.2)',
              borderRadius: 10,
              padding: 16,
              marginBottom: 20,
            }}
          >
            <div
              style={{
                fontSize: 13,
                fontWeight: 600,
                color: 'var(--color-primary-strong)',
                marginBottom: 8,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <span
                style={{
                  width: 16,
                  height: 16,
                  borderRadius: '50%',
                  border: '2px solid #7FACFF',
                  borderTopColor: 'transparent',
                  animation: 'spin .8s linear infinite',
                  display: 'inline-block',
                }}
              />
              {t('Đang chuyển hướng tới Microsoft Entra ID...')}
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>
              {t('Tổ chức:')} <strong style={{ color: 'var(--color-text)' }}>{t('Cơ quan ABC ({domain})', { domain })}</strong>
              <br />
              IdP: <strong style={{ color: 'var(--color-text)' }}>Microsoft Entra ID</strong>
            </div>
          </div>

          <div
            style={{
              fontSize: 12,
              color: 'var(--color-muted)',
              fontWeight: 500,
              textAlign: 'center',
              marginBottom: 12,
            }}
          >
            {t('Hỗ trợ IdP')}
          </div>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
            {IDPS.map((idp) => (
              <div
                key={idp}
                style={{
                  background: 'var(--color-input-bg)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 8,
                  padding: '8px 14px',
                  fontSize: 12.5,
                  color: 'var(--color-muted)',
                }}
              >
                {idp}
              </div>
            ))}
          </div>
        </AuthCard>

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <span
            onClick={() => navigate('/login')}
            style={{ fontSize: 13, color: 'var(--color-muted)', cursor: 'pointer' }}
          >
            ← {t('Quay lại đăng nhập')}
          </span>
        </div>
      </div>
    </AuthScreen>
  );
}
