import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import { ToastContext } from './toastContext';

export type ToastVariant = 'info' | 'success' | 'warning' | 'error';

interface ToastItem {
  id: number;
  msg: ReactNode;
  variant: ToastVariant;
}

/** Argument accepted by the toast push function: a plain string or an options object. */
export type ToastInput = string | { msg: ReactNode; variant?: ToastVariant };

/**
 * The value returned by useToast() is callable — `toast('message')` or
 * `toast({ msg, variant })` — and also exposes an explicit `.push(...)`.
 */
export interface ToastApi {
  (input: ToastInput): void;
  push: (input: ToastInput) => void;
}

const DOT_COLOR: Record<ToastVariant, string> = {
  info: 'var(--color-blue)',
  success: 'var(--color-teal)',
  warning: 'var(--color-amber)',
  error: 'var(--color-red)',
};

/** Toast host — renders bottom-right, auto-dismiss after 4.5s. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const seq = useRef(0);

  const push = useCallback((input: ToastInput) => {
    const { msg, variant = 'info' } =
      typeof input === 'string' ? { msg: input, variant: 'info' as ToastVariant } : input;
    const id = ++seq.current;
    setToasts((prev) => [...prev, { id, msg, variant: variant ?? 'info' }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const api = useMemo<ToastApi>(() => {
    const fn = ((input: ToastInput) => push(input)) as ToastApi;
    fn.push = push;
    return fn;
  }, [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        style={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          zIndex: 10000,
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          pointerEvents: 'none',
        }}
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            style={{
              background: '#1e293b',
              border: '1px solid rgba(255,255,255,.1)',
              borderRadius: 10,
              padding: '12px 16px',
              minWidth: 280,
              maxWidth: 380,
              pointerEvents: 'all',
              animation: 'toastIn .25s ease',
              boxShadow: '0 8px 32px rgba(0,0,0,.35)',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
            }}
          >
            <div
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: DOT_COLOR[t.variant],
                flexShrink: 0,
              }}
            />
            <span style={{ fontSize: 13.5, color: '#ecf1f8', lineHeight: 1.4 }}>{t.msg}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
