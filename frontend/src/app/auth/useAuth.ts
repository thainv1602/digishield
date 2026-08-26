import { useContext } from 'react';
import { AuthContext } from './context';
import type { AuthContextValue } from './AuthContext';

/** Access the current auth state + helpers. Must be used within <AuthProvider>. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>.');
  }
  return ctx;
}
