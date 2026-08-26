import { type CSSProperties, type ReactNode } from 'react';

/** Full-screen auth background wrapper (#F0F4FF). */
export function AuthScreen({ children }: { children: ReactNode }) {
  return (
    <main
      style={{
        minHeight: '100vh',
        background: 'var(--color-bg-auth)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      {children}
    </main>
  );
}

/** White auth card (16px radius, auth shadow). */
export function AuthCard({
  children,
  style,
}: {
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <div
      style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 16,
        padding: 28,
        boxShadow: 'var(--shadow-auth)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export function AuthFooter({ children }: { children: ReactNode }) {
  return (
    <div style={{ textAlign: 'center', marginTop: 20, fontSize: 12, color: 'var(--color-muted)' }}>
      {children}
    </div>
  );
}
