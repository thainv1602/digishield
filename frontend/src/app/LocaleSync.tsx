import { useEffect } from 'react';
import { useAuth } from './auth/useAuth';
import { useI18n } from '@/shared/i18n/i18nContext';

/**
 * Bridges the signed-in user's profile locale into the i18n language. Renders
 * nothing. Lives inside AuthProvider (to read the user) and under I18nProvider
 * (to set the language). A manual switcher choice always wins — see
 * {@link useI18n}'s `applyProfileLocale`.
 */
export function LocaleSync() {
  const { user } = useAuth();
  const { applyProfileLocale } = useI18n();
  const locale = user?.locale;

  useEffect(() => {
    applyProfileLocale(locale);
  }, [locale, applyProfileLocale]);

  return null;
}
