import { createContext, useContext } from 'react';
import type { Lang } from './messages';

export interface I18nContextValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
  /**
   * Apply the language from the signed-in user's profile locale. Only takes
   * effect when the user has NOT made an explicit choice via the switcher
   * (nothing persisted yet), and is not persisted itself — so an explicit pick
   * always wins and the profile is re-applied each session until then.
   */
  applyProfileLocale: (locale: string | null | undefined) => void;
  /** Translate a Vietnamese source string (with optional `{name}` vars). */
  t: (key: string, vars?: Record<string, string | number>) => string;
}

/**
 * Context and hooks live apart from `I18nProvider` so that the provider file
 * exports components only: a file mixing components with other exports loses
 * Vite fast refresh, remounting the tree on every edit.
 */
export const I18nContext = createContext<I18nContextValue | null>(null);

/** Access the translator and current language. */
export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within <I18nProvider>');
  return ctx;
}

/** Shorthand: just the `t` function. */
export function useT(): I18nContextValue['t'] {
  return useI18n().t;
}
