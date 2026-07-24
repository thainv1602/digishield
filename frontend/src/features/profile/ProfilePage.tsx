import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Input, useToast } from '@/shared/ui';
import { useI18n } from '@/shared/i18n/I18nProvider';
import type { Lang } from '@/shared/i18n/messages';
import { useAuth } from '@/app/auth/useAuth';
import { useMyProfile, useUpdateProfile } from './api';

const ROLE_LABELS: Record<string, string> = {
  super_admin: 'Quản trị hệ thống',
  org_admin: 'Quản trị tổ chức',
  manager: 'Quản lý',
  content_editor: 'Biên tập nội dung',
  analyst: 'Chuyên viên phân tích',
  learner: 'Người học',
};

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 24,
};

/**
 * Profile & settings for the signed-in user: read-only identity from the auth
 * token plus the UI-language preference (persisted by the i18n provider) and a
 * sign-out action. No backend call — the identity comes from the JWT and the
 * language from local storage.
 */
export default function ProfilePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { user, logout } = useAuth();
  const { lang, setLang, t } = useI18n();
  const { data: profile } = useMyProfile();
  const updateProfile = useUpdateProfile();

  const [name, setName] = useState('');
  // Seed the name field from the persisted profile, else the JWT name.
  useEffect(() => {
    setName(profile?.name ?? user?.name ?? '');
  }, [profile?.name, user?.name]);
  // Apply the persisted UI language once the profile loads.
  useEffect(() => {
    if (profile?.locale === 'vi' || profile?.locale === 'en') setLang(profile.locale);
  }, [profile?.locale, setLang]);

  const email = user?.email ?? null;
  const roleLabel = user?.role ? t(ROLE_LABELS[user.role] ?? user.role) : '—';
  const langs: { value: Lang; label: string }[] = [
    { value: 'vi', label: 'Tiếng Việt' },
    { value: 'en', label: 'English' },
  ];

  const saveName = () => {
    updateProfile.mutate(
      { name: name.trim(), locale: lang, email },
      {
        onSuccess: () => toast({ msg: t('Đã lưu hồ sơ'), variant: 'success' }),
        onError: () => toast({ msg: t('Không lưu được, thử lại'), variant: 'error' }),
      },
    );
  };

  const changeLang = (next: Lang) => {
    setLang(next);
    // Persist the choice to the account (best effort — local change already applied).
    updateProfile.mutate({ locale: next, name: name.trim(), email });
  };

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 640 }}>
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
          {t('Hồ sơ & Cài đặt')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
          {t('Thông tin tài khoản và tùy chọn cá nhân')}
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {/* Account */}
        <div style={cardStyle}>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
            {t('Thông tin tài khoản')}
          </div>
          <Row label={t('Tên')}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                aria-label={t('Tên')}
                placeholder={t('Tên hiển thị')}
                style={{ width: 200 }}
              />
              <Button variant="primary" onClick={saveName} disabled={updateProfile.isPending}>
                {updateProfile.isPending ? t('Đang lưu…') : t('Lưu')}
              </Button>
            </div>
          </Row>
          <Row label={t('Email')} value={user?.email ?? '—'} />
          <Row label={t('Vai trò')}>
            <span
              style={{
                display: 'inline-block',
                padding: '2px 8px',
                borderRadius: 6,
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--color-blue)',
                background: 'var(--tint-blue-soft)',
              }}
            >
              {roleLabel}
            </span>
          </Row>
          <Row label={t('Tổ chức')} value={user?.tenantId ?? '—'} mono last />
        </div>

        {/* Preferences */}
        <div style={cardStyle}>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
            {t('Tùy chọn')}
          </div>
          <Row label={t('Ngôn ngữ')} last>
            <div
              role="group"
              aria-label={t('Ngôn ngữ')}
              style={{ display: 'inline-flex', border: '1px solid var(--color-border)', borderRadius: 8, overflow: 'hidden' }}
            >
              {langs.map((o) => {
                const active = lang === o.value;
                return (
                  <button
                    key={o.value}
                    type="button"
                    onClick={() => changeLang(o.value)}
                    aria-pressed={active}
                    style={{
                      all: 'unset',
                      cursor: 'pointer',
                      padding: '6px 14px',
                      fontSize: 12.5,
                      fontWeight: 600,
                      color: active ? '#fff' : 'var(--color-muted)',
                      background: active ? 'var(--color-blue)' : 'transparent',
                    }}
                  >
                    {o.label}
                  </button>
                );
              })}
            </div>
          </Row>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            variant="outline"
            onClick={() => {
              logout();
              navigate('/login', { replace: true });
            }}
          >
            {t('Đăng xuất')}
          </Button>
        </div>
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  children,
  mono,
  last,
}: {
  label: string;
  value?: string;
  children?: React.ReactNode;
  mono?: boolean;
  last?: boolean;
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 16,
        padding: '10px 0',
        borderBottom: last ? undefined : '1px solid var(--color-border)',
      }}
    >
      <span style={{ fontSize: 13, color: 'var(--color-muted)' }}>{label}</span>
      {children ?? (
        <span
          style={{
            fontSize: 13.5,
            color: 'var(--color-text)',
            fontFamily: mono ? "'JetBrains Mono', monospace" : undefined,
          }}
        >
          {value}
        </span>
      )}
    </div>
  );
}
