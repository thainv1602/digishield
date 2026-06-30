import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/app/auth/useAuth';
import { ROLES, defaultRouteForRole } from '@/app/auth/roles';
import { Logo } from '@/shared/ui';
import { AuthScreen, AuthCard, authInputStyle, authLabelStyle } from './authShared';
import { useT } from '@/shared/i18n/I18nProvider';

type Strength = { score: number; label: string; color: string };

function scorePassword(pw: string): Strength {
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/\d/.test(pw)) score++;
  if (/[^A-Za-z0-9]/.test(pw)) score++;
  const labels = ['Yếu', 'Yếu', 'Trung bình', 'Khá', 'Mạnh'];
  const colors = [
    'var(--color-red)',
    'var(--color-red)',
    'var(--color-amber)',
    'var(--color-teal)',
    'var(--color-teal)',
  ];
  const i = Math.min(score, 4);
  return { score, label: labels[i] ?? 'Yếu', color: colors[i] ?? 'var(--color-red)' };
}

/** Onboarding step 1 — set password (strength bar) + language. */
export default function OnboardingPage() {
  const t = useT();
  const navigate = useNavigate();
  const { login } = useAuth();
  const [pw, setPw] = useState('Demo@2026strong');
  const [confirm, setConfirm] = useState('Demo@2026strong');
  const [lang, setLang] = useState<'vi' | 'en'>('vi');

  const strength = useMemo(() => scorePassword(pw), [pw]);

  function next() {
    const role = ROLES.LEARNER;
    login({ id: 'me', tenantId: 'tenant-demo', role, name: 'Học viên mới' }, 'demo-token');
    navigate(defaultRouteForRole(role));
  }

  return (
    <AuthScreen>
      <div style={{ width: 480, animation: 'fadeUp .4s ease' }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ marginBottom: 16, display: 'inline-flex' }}>
            <Logo size={28} wordmarkSize={20} />
          </div>
          <h1 style={{ fontSize: 22, marginBottom: 6 }}>{t('Chào mừng bạn đến DigiShield!')}</h1>
          <div style={{ fontSize: 13.5, color: 'var(--color-muted)' }}>
            {t('Bạn được Cơ quan ABC mời tham gia · 3 bước nhanh để bắt đầu')}
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginBottom: 28 }}>
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              style={{
                width: 32,
                height: 6,
                borderRadius: 99,
                background: i === 0 ? 'var(--color-blue)' : 'var(--color-border)',
              }}
            />
          ))}
        </div>

        <AuthCard>
          <div
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: 'var(--color-muted)',
              letterSpacing: '.08em',
              textTransform: 'uppercase',
              marginBottom: 16,
            }}
          >
            {t('Bước 1 · Đặt mật khẩu')}
          </div>

          <div style={{ marginBottom: 14 }}>
            <label htmlFor="ob-pw" style={authLabelStyle}>
              {t('Mật khẩu mới')}
            </label>
            <input
              id="ob-pw"
              type="password"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
              style={authInputStyle}
            />
          </div>

          <div style={{ marginBottom: 20 }}>
            <label htmlFor="ob-confirm" style={authLabelStyle}>
              {t('Xác nhận mật khẩu')}
            </label>
            <input
              id="ob-confirm"
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              style={authInputStyle}
            />
            <div
              style={{
                marginTop: 6,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              {[1, 2, 3, 4].map((seg) => (
                <div
                  key={seg}
                  style={{
                    height: 4,
                    flex: 1,
                    borderRadius: 99,
                    background: strength.score >= seg ? strength.color : 'var(--color-border)',
                  }}
                />
              ))}
              <span style={{ fontSize: 11.5, color: strength.color, fontWeight: 500 }}>
                {t(strength.label)}
              </span>
            </div>
          </div>

          <div style={{ marginBottom: 20 }}>
            <label style={{ ...authLabelStyle, color: 'var(--color-muted)', marginBottom: 8 }}>
              {t('Ngôn ngữ')}
            </label>
            <div style={{ display: 'flex', gap: 8 }}>
              {(['vi', 'en'] as const).map((code) => {
                const active = lang === code;
                return (
                  <button
                    key={code}
                    type="button"
                    onClick={() => setLang(code)}
                    style={{
                      flex: 1,
                      background: active ? 'rgba(37,102,235,.1)' : 'var(--color-input-bg)',
                      border: `${active ? '2px' : '1.5px'} solid ${
                        active ? 'var(--color-blue)' : 'var(--color-border)'
                      }`,
                      borderRadius: 8,
                      padding: 10,
                      textAlign: 'center',
                      fontSize: 13.5,
                      fontWeight: active ? 600 : 400,
                      color: active ? 'var(--color-primary-strong)' : 'var(--color-muted)',
                      cursor: 'pointer',
                    }}
                  >
                    {code === 'vi' ? t('Tiếng Việt') : 'English'}
                  </button>
                );
              })}
            </div>
          </div>

          <button
            type="button"
            onClick={next}
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
            {t('Tiếp tục — Bài xếp lớp →')}
          </button>
        </AuthCard>
      </div>
    </AuthScreen>
  );
}
