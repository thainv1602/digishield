import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import { roleToPersona, defaultRouteForPersona, type Role } from '@/app/auth/roles';
import { Logo } from '@/shared/ui';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';
import { axiosInstance } from '@/shared/api/client';
import { cognitoEnabled } from '@/app/auth/cognito';
import { useT } from '@/shared/i18n/I18nProvider';
import { AuthScreen, AuthCard, authInputStyle, authLabelStyle } from './authShared';

/** Shape of GET /users rows (snake_case wire format). */
interface UserRow {
  id: string;
  org_id: string;
  email: string;
  name: string;
  role: Role;
}

/** Login — email/password + SSO; the role comes from the account, not a picker. */
export default function LoginPage() {
  const navigate = useNavigate();
  const { login, signinRedirect } = useAuth();
  const t = useT();
  const [email, setEmail] = useState('admin@coquan.gov.vn');
  const [password, setPassword] = useState('demo1234');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function doLogin(e?: FormEvent) {
    e?.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const { data: tokens } = await axiosInstance.post<{ access_token: string }>(
        '/auth/login',
        { email, password },
      );
      // The demo backend issues static tokens without an identity, so resolve
      // the account (and its role) from the seeded users list by email.
      const { data: users } = await axiosInstance.get<UserRow[]>('/users');
      const account = users.find(
        (u) => u.email?.toLowerCase() === email.trim().toLowerCase(),
      );
      if (!account) {
        setError(t('Không tìm thấy tài khoản với email này.'));
        return;
      }
      login(
        {
          id: account.id,
          tenantId: account.org_id ?? DEMO_TENANT_ID,
          role: account.role,
          email: account.email,
          name: account.name,
        },
        tokens.access_token,
      );
      navigate(defaultRouteForPersona(roleToPersona(account.role)));
    } catch {
      setError(t('Không đăng nhập được — máy chủ chưa sẵn sàng, thử lại sau.'));
    } finally {
      setSubmitting(false);
    }
  }

  // Deployed build: delegate auth to the Cognito hosted UI (OIDC code + PKCE).
  if (cognitoEnabled) {
    return (
      <AuthScreen>
        <div style={{ width: 400, animation: 'fadeUp .4s ease', textAlign: 'center' }}>
          <div style={{ marginBottom: 12, display: 'inline-flex' }}>
            <Logo size={36} wordmarkSize={26} />
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)', marginBottom: 28 }}>
            {t('Nền tảng nhận thức an ninh số')}
          </div>
          <AuthCard style={{ padding: 32 }}>
            <div style={{ fontSize: 14, color: 'var(--color-text-soft)', marginBottom: 20 }}>
              {t('Đăng nhập bằng tài khoản tổ chức (AWS Cognito).')}
            </div>
            <button
              type="button"
              onClick={() => signinRedirect()}
              style={{
                width: '100%',
                background: 'var(--color-blue)',
                color: '#fff',
                border: 'none',
                borderRadius: 9,
                padding: 13,
                fontWeight: 600,
                cursor: 'pointer',
                fontSize: 15,
              }}
            >
              {t('Đăng nhập')}
            </button>
          </AuthCard>
          <div style={{ marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
            DigiShield v1.0 · Lá Chắn Số · 2026
          </div>
        </div>
      </AuthScreen>
    );
  }

  return (
    <AuthScreen>
      <div style={{ width: 400, animation: 'fadeUp .4s ease' }}>
        <div style={{ textAlign: 'center', marginBottom: 36 }}>
          <div style={{ marginBottom: 12, display: 'inline-flex' }}>
            <Logo size={36} wordmarkSize={26} />
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
            {t('Nền tảng nhận thức an ninh số')}
          </div>
        </div>

        <AuthCard style={{ padding: 32 }}>
          <div
            style={{
              background: 'var(--tint-blue-soft)',
              border: '1px solid rgba(37,102,235,.15)',
              borderRadius: 8,
              padding: '10px 14px',
              marginBottom: 24,
              fontSize: 12.5,
              color: 'var(--color-primary-strong)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <Users size={14} strokeWidth={2} />
            {t('Vai trò lấy theo tài khoản — đăng nhập bằng email demo đã seed.')}
          </div>

          <form onSubmit={doLogin}>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="login-email" style={authLabelStyle}>
                {t('Email công việc')}
              </label>
              <input
                id="login-email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={authInputStyle}
              />
            </div>

            <div style={{ marginBottom: 24 }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 6,
                }}
              >
                <label htmlFor="login-pw" style={{ ...authLabelStyle, marginBottom: 0 }}>
                  {t('Mật khẩu')}
                </label>
                <span
                  onClick={() => navigate('/auth/forgot-password')}
                  style={{ fontSize: 12, color: 'var(--color-blue)', cursor: 'pointer' }}
                >
                  {t('Quên mật khẩu?')}
                </span>
              </div>
              <input
                id="login-pw"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={authInputStyle}
              />
            </div>

            {error && (
              <div
                style={{
                  marginBottom: 14,
                  padding: '9px 12px',
                  borderRadius: 8,
                  background: 'var(--tint-red-soft, rgba(220,38,38,.08))',
                  border: '1px solid rgba(220,38,38,.25)',
                  fontSize: 12.5,
                  color: 'var(--color-red, #dc2626)',
                }}
              >
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              style={{
                width: '100%',
                background: 'var(--color-blue)',
                color: '#fff',
                border: 'none',
                borderRadius: 9,
                padding: 13,
                textAlign: 'center',
                fontWeight: 600,
                cursor: 'pointer',
                fontSize: 15,
                marginBottom: 12,
                opacity: submitting ? 0.7 : 1,
              }}
            >
              {submitting ? t('Đang đăng nhập…') : t('Đăng nhập')}
            </button>
          </form>

          <button
            type="button"
            onClick={() => navigate('/auth/sso')}
            style={{
              width: '100%',
              border: '1px solid var(--color-input-border)',
              background: 'transparent',
              borderRadius: 9,
              padding: 11,
              textAlign: 'center',
              fontSize: 13.5,
              color: 'var(--color-text-soft)',
              cursor: 'pointer',
            }}
          >
            {t('Đăng nhập bằng SSO (Entra ID / Google Workspace)')}
          </button>

          <div style={{ marginTop: 18 }}>
            <div
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: 'var(--color-muted)',
                letterSpacing: '.08em',
                textTransform: 'uppercase',
                marginBottom: 8,
              }}
            >
              {t('Tài khoản demo (mật khẩu bất kỳ)')}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {[
                'admin@coquan.gov.vn',
                'manager@coquan.gov.vn',
                'editor@coquan.gov.vn',
                'analyst@coquan.gov.vn',
                'learner@coquan.gov.vn',
                'superadmin@digishield.vn',
              ].map((demoEmail) => (
                <button
                  key={demoEmail}
                  type="button"
                  onClick={() => setEmail(demoEmail)}
                  style={{
                    border: '1px solid var(--color-input-border)',
                    background: email === demoEmail ? 'var(--tint-blue-soft)' : 'transparent',
                    borderRadius: 999,
                    padding: '4px 10px',
                    fontSize: 11.5,
                    color: 'var(--color-text-soft)',
                    cursor: 'pointer',
                  }}
                >
                  {demoEmail}
                </button>
              ))}
            </div>
          </div>
        </AuthCard>

        <div style={{ textAlign: 'center', marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
          DigiShield v1.0 · Lá Chắn Số · 2026
        </div>
      </div>
    </AuthScreen>
  );
}
