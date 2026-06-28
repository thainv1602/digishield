import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import {
  PERSONAS,
  personaToRole,
  defaultRouteForPersona,
  type Persona,
} from '@/app/auth/roles';
import { Logo } from '@/shared/ui';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';
import { cognitoEnabled } from '@/app/auth/cognito';
import { AuthScreen, AuthCard, authInputStyle, authLabelStyle } from './authShared';

/** Login — 4-role pill segmented control + email/password + SSO. */
export default function LoginPage() {
  const navigate = useNavigate();
  const { login, signinRedirect } = useAuth();
  const [persona, setPersona] = useState<Persona>('admin');
  const [email, setEmail] = useState('admin@coquan.gov.vn');
  const [password, setPassword] = useState('demo1234');
  const [submitting, setSubmitting] = useState(false);

  function doLogin(e?: FormEvent) {
    e?.preventDefault();
    setSubmitting(true);
    setTimeout(() => {
      const role = personaToRole(persona);
      login(
        { id: 'me', tenantId: DEMO_TENANT_ID, role, email, name: 'Nguyễn Tuấn' },
        'demo-token',
      );
      navigate(defaultRouteForPersona(persona));
    }, 600);
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
            Nền tảng nhận thức an ninh số · Digital Security Awareness Platform
          </div>
          <AuthCard style={{ padding: 32 }}>
            <div style={{ fontSize: 14, color: 'var(--color-text-soft)', marginBottom: 20 }}>
              Đăng nhập bằng tài khoản tổ chức (AWS Cognito).
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
              Đăng nhập
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
            Nền tảng nhận thức an ninh số · Digital Security Awareness Platform
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
            Chọn vai trò để đăng nhập
          </div>

          <div style={{ marginBottom: 20 }}>
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
              Vai trò / Role
            </div>
            <div
              style={{
                display: 'flex',
                background: 'var(--color-bg-auth)',
                borderRadius: 9,
                padding: 3,
                gap: 2,
              }}
            >
              {PERSONAS.map((p) => {
                const active = p.id === persona;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setPersona(p.id)}
                    style={{
                      flex: 1,
                      padding: '8px 2px',
                      textAlign: 'center',
                      borderRadius: 7,
                      border: 'none',
                      fontSize: 11.5,
                      fontWeight: 500,
                      cursor: 'pointer',
                      transition: 'all .15s',
                      background: active ? 'var(--color-blue)' : 'transparent',
                      color: active ? '#fff' : 'var(--color-muted)',
                    }}
                  >
                    {p.label}
                  </button>
                );
              })}
            </div>
          </div>

          <form onSubmit={doLogin}>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="login-email" style={authLabelStyle}>
                Email công việc
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
                  Mật khẩu
                </label>
                <span
                  onClick={() => navigate('/auth/forgot-password')}
                  style={{ fontSize: 12, color: 'var(--color-blue)', cursor: 'pointer' }}
                >
                  Quên mật khẩu?
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
              {submitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
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
            Đăng nhập bằng SSO (Entra ID / Google Workspace)
          </button>
        </AuthCard>

        <div style={{ textAlign: 'center', marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
          DigiShield v1.0 · Lá Chắn Số · 2026
        </div>
      </div>
    </AuthScreen>
  );
}
