import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { I18nContext, type I18nContextValue } from './i18nContext';
import { translate, type Lang } from './messages';

const STORAGE_KEY = 'digishield.lang';



function hasStoredChoice(): boolean {
  return typeof localStorage !== 'undefined' && localStorage.getItem(STORAGE_KEY) != null;
}

function initialLang(): Lang {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null;
  return stored === 'en' ? 'en' : 'vi';
}

/** Normalize a BCP-47 locale (e.g. "en-US", "vi_VN") to a supported Lang. */
function localeToLang(locale: string | null | undefined): Lang {
  return (locale ?? '').toLowerCase().startsWith('en') ? 'en' : 'vi';
}

/** Provides the current language + translator. Persists the choice. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);

  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* ignore storage failures */
    }
    if (typeof document !== 'undefined') document.documentElement.lang = next;
  }, []);

  const applyProfileLocale = useCallback((locale: string | null | undefined) => {
    if (hasStoredChoice()) return; // an explicit switcher choice always wins
    const next = localeToLang(locale);
    setLangState(next);
    if (typeof document !== 'undefined') document.documentElement.lang = next;
  }, []);

  const value = useMemo<I18nContextValue>(
    () => ({ lang, setLang, applyProfileLocale, t: (key, vars) => translate(lang, key, vars) }),
    [lang, setLang, applyProfileLocale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
