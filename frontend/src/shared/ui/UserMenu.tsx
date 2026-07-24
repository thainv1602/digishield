import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './UserMenu.module.css';

/** Human-readable label per role; falls back to the raw value. */
const ROLE_LABELS: Record<string, string> = {
  super_admin: 'Quản trị hệ thống',
  org_admin: 'Quản trị tổ chức',
  manager: 'Quản lý',
  content_editor: 'Biên tập nội dung',
  analyst: 'Chuyên viên phân tích',
  learner: 'Người học',
};

/**
 * Avatar button that opens a dropdown with the signed-in user's identity
 * (name, email, role) and a sign-out action — clicking the avatar no longer
 * logs out directly.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const displayName = user?.name ?? user?.email ?? t('Người dùng');
  const initials = displayName
    .split(/\s+/)
    .map((p) => p.charAt(0))
    .slice(0, 2)
    .join('')
    .toUpperCase();
  const roleLabel = user?.role ? t(ROLE_LABELS[user.role] ?? user.role) : null;

  return (
    <div className={styles.root} ref={ref}>
      <button
        type="button"
        className={styles.avatar}
        aria-label={t('Tài khoản')}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        {initials}
      </button>

      {open ? (
        <div className={styles.panel} role="menu">
          <div className={styles.identity}>
            <div className={styles.bigAvatar}>{initials}</div>
            <div className={styles.info}>
              <div className={styles.name}>{displayName}</div>
              {user?.email ? <div className={styles.email}>{user.email}</div> : null}
              {roleLabel ? <span className={styles.role}>{roleLabel}</span> : null}
            </div>
          </div>
          <button
            type="button"
            className={styles.logout}
            role="menuitem"
            onClick={() => {
              setOpen(false);
              logout();
              navigate('/login', { replace: true });
            }}
          >
            <LogOut size={15} strokeWidth={2} />
            {t('Đăng xuất')}
          </button>
        </div>
      ) : null}
    </div>
  );
}
