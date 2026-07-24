import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { useAuth } from '@/app/auth/useAuth';
import { NAV_BY_PERSONA, roleToPersona } from '@/app/auth/roles';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { NavIcon } from './navIcons';
import styles from './SearchBox.module.css';

/**
 * Top-bar quick search over the pages available to the current user (their
 * RBAC nav). Type to filter, arrow/Enter or click to navigate. Purely
 * client-side — no search backend needed.
 */
export function SearchBox() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const ref = useRef<HTMLDivElement>(null);

  const persona = user ? roleToPersona(user.role) : 'admin';
  const pages = useMemo(() => NAV_BY_PERSONA[persona] ?? [], [persona]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return pages.filter((p) => t(p.label).toLowerCase().includes(q)).slice(0, 8);
  }, [pages, query, t]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const go = (path: string) => {
    navigate(path);
    setQuery('');
    setOpen(false);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (!results.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => (a + 1) % results.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => (a - 1 + results.length) % results.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = results[active] ?? results[0];
      if (item) go(item.path);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div className={styles.root} ref={ref}>
      <div className={styles.box}>
        <Search size={13} strokeWidth={2.5} color="var(--color-muted)" />
        <input
          className={styles.input}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
            setActive(0);
          }}
          onFocus={() => query && setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={t('Tìm kiếm trang...')}
          aria-label={t('Tìm kiếm trang')}
        />
      </div>

      {open && query.trim() ? (
        <div className={styles.panel} role="listbox">
          {results.length === 0 ? (
            <div className={styles.empty}>{t('Không có kết quả')}</div>
          ) : (
            results.map((p, i) => (
              <button
                key={p.key}
                type="button"
                role="option"
                aria-selected={i === active}
                className={`${styles.item} ${i === active ? styles.itemActive : ''}`}
                onMouseEnter={() => setActive(i)}
                onClick={() => go(p.path)}
              >
                <NavIcon name={p.icon} size={14} />
                <span>{t(p.label)}</span>
                {p.section ? <span className={styles.section}>{t(p.section)}</span> : null}
              </button>
            ))
          )}
        </div>
      ) : null}
    </div>
  );
}
