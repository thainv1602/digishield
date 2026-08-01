import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '@/shared/ui';
import { useAuth } from '@/app/auth/useAuth';
import { cognitoEnabled } from '@/app/auth/cognito';
import { ALL_ROLES, defaultRouteForRole, ROLES, type Role } from '@/app/auth/roles';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';
import { useT } from '@/shared/i18n/I18nProvider';
import { AuthScreen, AuthCard } from './authShared';

const ROLE_LABELS: Record<Role, string> = {
  [ROLES.SUPER_ADMIN]: 'Super Admin',
  [ROLES.ORG_ADMIN]: 'Org Admin',
  [ROLES.MANAGER]: 'Manager',
  [ROLES.CONTENT_EDITOR]: 'Content Editor',
  [ROLES.ANALYST]: 'Analyst',
  [ROLES.LEARNER]: 'Learner',
};

const fieldStyle = {
  width: '100%',
  padding: '10px 12px',
  borderRadius: 9,
  border: '1px solid var(--color-border)',
  fontSize: 14,
  background: 'var(--color-bg)',
  color: 'var(--color-text)',
} as const;

/**
 * Dev sign-in, rendered only when Cognito is not configured (local dev, CI,
 * Selenium E2E). No credentials are checked: the dev backend profile is
 * permitAll, so this just seeds the client-side principal. The stable ids
 * (login-role / login-email / login-submit) are the contract the `:e2e`
 * Page Object and the course slides rely on.
 */
function DevLoginForm() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const t = useT();
  const [role, setRole] = useState<Role>(ROLES.LEARNER);
  const [email, setEmail] = useState('learner@dev.digishield.local');
  const [emailEdited, setEmailEdited] = useState(false);

  const pickRole = (next: Role) => {
    setRole(next);
    if (!emailEdited) setEmail(`${next}@dev.digishield.local`);
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    login(
      { id: `dev-${role}`, tenantId: DEMO_TENANT_ID, role, email, name: email },
      'dev-token',
    );
    const from = (location.state as { from?: string } | null)?.from;
    navigate(from ?? defaultRouteForRole(role), { replace: true });
  };

  return (
    <form onSubmit={onSubmit} style={{ textAlign: 'left' }}>
      <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 18 }}>
        {t('Dev sign-in — Cognito chưa cấu hình (chỉ dành cho môi trường dev/E2E).')}
      </div>
      <label htmlFor="login-role" style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
        {t('Vai trò')}
      </label>
      <select
        id="login-role"
        value={role}
        onChange={(e) => pickRole(e.target.value as Role)}
        style={{ ...fieldStyle, marginBottom: 14 }}
      >
        {ALL_ROLES.map((r) => (
          <option key={r} value={r}>
            {ROLE_LABELS[r]}
          </option>
        ))}
      </select>
      <label htmlFor="login-email" style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
        Email
      </label>
      <input
        id="login-email"
        type="email"
        required
        value={email}
        onChange={(e) => {
          setEmail(e.target.value);
          setEmailEdited(true);
        }}
        style={{ ...fieldStyle, marginBottom: 20 }}
      />
      <button
        id="login-submit"
        type="submit"
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
        {t('Đăng nhập (dev)')}
      </button>
    </form>
  );
}

/** Login — Cognito hosted UI (OIDC code + PKCE) when configured, dev sign-in otherwise. */
export default function LoginPage() {
  const { signinRedirect } = useAuth();
  const t = useT();

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
          {cognitoEnabled ? (
            <>
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
            </>
          ) : (
            <DevLoginForm />
          )}
        </AuthCard>
        <div style={{ marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
          DigiShield v1.0 · Lá Chắn Số · 2026
        </div>
      </div>
    </AuthScreen>
  );
}
