import { Logo } from '@/shared/ui';
import { useAuth } from '@/app/auth/useAuth';
import { cognitoEnabled } from '@/app/auth/cognito';
import { useT } from '@/shared/i18n/I18nProvider';
import { AuthScreen, AuthCard } from './authShared';

/** Login — delegates entirely to the Cognito hosted UI (OIDC code + PKCE). */
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
            <div style={{ fontSize: 13.5, color: 'var(--color-muted)', lineHeight: 1.6 }}>
              {t('Chưa cấu hình đăng nhập. Đặt VITE_COGNITO_AUTHORITY và VITE_COGNITO_CLIENT_ID rồi build lại.')}
            </div>
          )}
        </AuthCard>
        <div style={{ marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
          DigiShield v1.0 · Lá Chắn Số · 2026
        </div>
      </div>
    </AuthScreen>
  );
}
