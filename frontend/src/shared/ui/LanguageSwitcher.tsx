import { useI18n } from '@/shared/i18n/i18nContext';
import type { Lang } from '@/shared/i18n/messages';

/** Compact VI/EN toggle for the top bar. */
export function LanguageSwitcher() {
  const { lang, setLang } = useI18n();
  const options: { value: Lang; label: string }[] = [
    { value: 'vi', label: 'VI' },
    { value: 'en', label: 'EN' },
  ];
  return (
    <div
      role="group"
      aria-label="Language"
      style={{ display: 'inline-flex', border: '1px solid var(--color-border)', borderRadius: 8, overflow: 'hidden' }}
    >
      {options.map((o) => {
        const active = lang === o.value;
        return (
          <button
            key={o.value}
            type="button"
            onClick={() => setLang(o.value)}
            aria-pressed={active}
            style={{
              all: 'unset',
              cursor: 'pointer',
              padding: '5px 9px',
              fontSize: 11.5,
              fontWeight: 700,
              color: active ? '#fff' : 'var(--color-muted)',
              background: active ? 'var(--color-blue)' : 'transparent',
            }}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
