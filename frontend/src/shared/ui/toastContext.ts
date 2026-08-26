import { createContext, useContext } from 'react';
import type { ToastApi } from './Toast';

/**
 * Context and hook live apart from `ToastProvider` so that `Toast.tsx` exports
 * components only; a mixed file loses Vite fast refresh for the whole app,
 * since the provider sits at the root.
 */
export const ToastContext = createContext<ToastApi | undefined>(undefined);

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within a <ToastProvider>.');
  return ctx;
}
