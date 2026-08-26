import { type ReactNode } from 'react';
import styles from './StatusPill.module.css';

export type StatusVariant = 'safe' | 'warning' | 'threat' | 'neutral' | 'info';

export interface StatusPillProps {
  variant: StatusVariant;
  children: ReactNode;
  /** Render a leading status dot. */
  dot?: boolean;
}

/** Compact status indicator. Uses semantic status colors from tokens. */
export function StatusPill({ variant, children, dot = false }: StatusPillProps) {
  return (
    <span className={`${styles.pill} ${styles[variant]}`}>
      {dot ? <span className={styles.dot} aria-hidden="true" /> : null}
      {children}
    </span>
  );
}
