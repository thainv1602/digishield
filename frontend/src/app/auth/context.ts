import { createContext } from 'react';
import type { AuthContextValue } from './AuthContext';

/**
 * Auth context object. It lives apart from the provider so that
 * `AuthContext.tsx` exports components only — a file mixing the two breaks
 * Vite's fast refresh, which then remounts the tree and drops auth state on
 * every edit.
 */
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
