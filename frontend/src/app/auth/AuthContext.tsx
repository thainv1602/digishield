import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { User } from 'oidc-client-ts';
import type { Role } from './roles';
import { setAuthAccessor } from '@/shared/api/authBridge';
import { cognitoEnabled, userManager, toCurrentUser } from './cognito';

/** Authenticated principal derived from the JWT (org-scoped). */
export interface CurrentUser {
  id: string;
  /** Tenant / organization id — sent as X-Tenant-Id and embedded in the JWT. */
  tenantId: string;
  role: Role;
  /** Optional display fields populated from /auth/me. */
  name?: string;
  email?: string;
}

export interface AuthState {
  user: CurrentUser | null;
  /** Access token kept in memory only (never persisted to localStorage). */
  token: string | null;
  isAuthenticated: boolean;
  /** True while the Cognito session is being restored on first load. */
  initializing: boolean;
}

export interface AuthContextValue extends AuthState {
  /** Store the principal + token after a successful login. */
  login: (user: CurrentUser, token: string) => void;
  /** Clear all auth state. */
  logout: () => void;
  /** Replace just the token (e.g. after a silent refresh). */
  setToken: (token: string | null) => void;
  /** Start the Cognito hosted-UI login (no-op when Cognito is disabled). */
  signinRedirect: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
  /** Optional initial state, useful for tests. */
  initialState?: Partial<AuthState>;
}

export function AuthProvider({ children, initialState }: AuthProviderProps) {
  const [user, setUser] = useState<CurrentUser | null>(initialState?.user ?? null);
  const [token, setTokenState] = useState<string | null>(initialState?.token ?? null);
  // Block the app on first load only while a real Cognito session is restored.
  const [initializing, setInitializing] = useState<boolean>(cognitoEnabled);

  const login = useCallback((nextUser: CurrentUser, nextToken: string) => {
    setUser(nextUser);
    setTokenState(nextToken);
  }, []);

  const logout = useCallback(() => {
    setUser(null);
    setTokenState(null);
    if (cognitoEnabled && userManager) {
      userManager.removeUser().catch(() => {});
    }
  }, []);

  const setToken = useCallback((nextToken: string | null) => {
    setTokenState(nextToken);
  }, []);

  const signinRedirect = useCallback(() => {
    userManager?.signinRedirect().catch(() => {});
  }, []);

  // On first load: complete the hosted-UI redirect, or restore a stored session.
  useEffect(() => {
    if (!cognitoEnabled || !userManager) return;
    const um = userManager;
    let active = true;

    const apply = (u: User | null) => {
      if (active && u && !u.expired) {
        setUser(toCurrentUser(u));
        setTokenState(u.access_token);
      }
    };

    (async () => {
      try {
        const params = new URLSearchParams(window.location.search);
        if (params.has('code') && params.has('state')) {
          const u = await um.signinRedirectCallback();
          window.history.replaceState({}, '', '/');
          apply(u);
        } else {
          apply(await um.getUser());
        }
      } catch {
        // fall through to the login screen
      } finally {
        if (active) setInitializing(false);
      }
    })();

    const onLoaded = (u: User) => {
      if (!active) return;
      setUser(toCurrentUser(u));
      setTokenState(u.access_token);
    };
    um.events.addUserLoaded(onLoaded);

    return () => {
      active = false;
      um.events.removeUserLoaded(onLoaded);
    };
  }, []);

  // Expose a read-only accessor to the (non-React) axios layer so request
  // interceptors can attach Authorization / X-Tenant-Id without prop drilling.
  setAuthAccessor(() => ({ token, tenantId: user?.tenantId ?? null }));

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(user && token),
      initializing,
      login,
      logout,
      setToken,
      signinRedirect,
    }),
    [user, token, initializing, login, logout, setToken, signinRedirect],
  );

  if (initializing) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--color-muted)',
          fontSize: 14,
        }}
      >
        Đang tải…
      </div>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
