import { Fragment } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import { NAV_BY_PERSONA, roleToPersona, type NavItem } from '@/app/auth/roles';
import { Logo } from './Logo';
import { NavIcon } from './navIcons';
import { useT } from '@/shared/i18n/I18nProvider';
import styles from './Sidebar.module.css';

function initialsOf(name?: string, email?: string): string {
  const src = name ?? email ?? 'U';
  return src
    .split(/\s+/)
    .map((p) => p.charAt(0))
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

/** Left navigation: logo header, persona-scoped RBAC nav, profile card. */
export function Sidebar() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const t = useT();
  const persona = user ? roleToPersona(user.role) : 'admin';
  const items = NAV_BY_PERSONA[persona] ?? [];

  // Group items by section, preserving order.
  const sections: { heading: string | undefined; items: NavItem[] }[] = [];
  for (const item of items) {
    const last = sections[sections.length - 1];
    if (last && last.heading === item.section) last.items.push(item);
    else sections.push({ heading: item.section, items: [item] });
  }

  return (
    <aside className={styles.sidebar} aria-label="Primary navigation">
      <div className={styles.brand}>
        <Logo size={26} />
      </div>

      <nav className={styles.nav}>
        {sections.map((section, si) => (
          <Fragment key={section.heading ?? si}>
            {section.heading ? <div className={styles.section}>{t(section.heading)}</div> : null}
            {section.items.map((item) => (
              <NavLink
                key={item.key}
                to={item.path}
                className={({ isActive }) =>
                  `${styles.link} ${isActive ? styles.linkActive : ''}`
                }
              >
                <NavIcon name={item.icon} />
                <span className={styles.linkLabel}>{t(item.label)}</span>
                {item.badge ? <span className={styles.badge}>{item.badge}</span> : null}
              </NavLink>
            ))}
          </Fragment>
        ))}
      </nav>

      <div className={styles.footer}>
        <button
          type="button"
          className={styles.profile}
          onClick={() => navigate('/dashboard')}
        >
          <span className={styles.avatar} aria-hidden="true">
            {initialsOf(user?.name, user?.email)}
          </span>
          <span className={styles.profileMeta}>
            <span className={styles.profileName}>{user?.name ?? user?.email ?? t('Người dùng')}</span>
            <span className={styles.profileLink}>{t('Hồ sơ & Cài đặt')}</span>
          </span>
          <ChevronRight size={13} strokeWidth={2} color="var(--color-muted)" />
        </button>
      </div>
    </aside>
  );
}
