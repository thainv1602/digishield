import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type CurrentUser } from './AuthContext';
import { useAuth } from './useAuth';
import { ROLES } from './roles';
import { getAuth, setAuthAccessor } from '@/shared/api/authBridge';

const alice: CurrentUser = {
  id: 'user-1',
  tenantId: 'tenant-1',
  role: ROLES.ORG_ADMIN,
  name: 'Alice',
  email: 'alice@example.com',
};

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

const renderAuth = () => renderHook(() => useAuth(), { wrapper });

describe('AuthProvider', () => {
  afterEach(() => {
    setAuthAccessor(() => ({ token: null, tenantId: null }));
  });

  it('starts with no session', () => {
    const { result } = renderAuth();

    expect(result.current.user).toBeNull();
    expect(result.current.token).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('renders children immediately when Cognito is not configured', () => {
    // With no VITE_COGNITO_* set there is no session to restore, so the provider
    // must not sit on the blocking "Đang tải…" screen forever.
    const { result } = renderAuth();

    expect(result.current.initializing).toBe(false);
  });

  it('holds the principal and token after login', () => {
    const { result } = renderAuth();

    act(() => result.current.login(alice, 'tok-1'));

    expect(result.current.user).toEqual(alice);
    expect(result.current.token).toBe('tok-1');
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('clears both principal and token on logout', () => {
    const { result } = renderAuth();
    act(() => result.current.login(alice, 'tok-1'));

    act(() => result.current.logout());

    expect(result.current.user).toBeNull();
    expect(result.current.token).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('replaces only the token on a silent refresh', () => {
    const { result } = renderAuth();
    act(() => result.current.login(alice, 'tok-1'));

    act(() => result.current.setToken('tok-2'));

    expect(result.current.token).toBe('tok-2');
    // The principal survives: a refresh renews the credential, it does not
    // re-identify the user.
    expect(result.current.user).toEqual(alice);
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('is not authenticated on a token alone', () => {
    const { result } = renderAuth();

    act(() => result.current.setToken('tok-orphan'));

    // No principal means no tenant to scope requests to, so this is not a
    // usable session even though a credential exists.
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('is not authenticated once the token is dropped', () => {
    const { result } = renderAuth();
    act(() => result.current.login(alice, 'tok-1'));

    act(() => result.current.setToken(null));

    expect(result.current.user).toEqual(alice);
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('accepts seeded state so screens can be tested signed in', () => {
    const { result } = renderHook(() => useAuth(), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <AuthProvider initialState={{ user: alice, token: 'seed' }}>{children}</AuthProvider>
      ),
    });

    expect(result.current.isAuthenticated).toBe(true);
  });
});

/**
 * The provider publishes its state to the non-React axios layer on every
 * render. This is the seam the request interceptor reads: if it goes stale, the
 * app looks signed in while every call goes out anonymous — and the response
 * interceptor then answers the resulting 401 by signing the user out.
 */
describe('AuthProvider → axios bridge', () => {
  afterEach(() => {
    setAuthAccessor(() => ({ token: null, tenantId: null }));
  });

  it('publishes nothing while signed out', () => {
    renderAuth();

    expect(getAuth()).toEqual({ token: null, tenantId: null });
  });

  it('publishes the token and tenant as soon as login happens', () => {
    const { result } = renderAuth();

    act(() => result.current.login(alice, 'tok-1'));

    expect(getAuth()).toEqual({ token: 'tok-1', tenantId: 'tenant-1' });
  });

  it('publishes the new token after a refresh', () => {
    const { result } = renderAuth();
    act(() => result.current.login(alice, 'tok-1'));

    act(() => result.current.setToken('tok-2'));

    expect(getAuth().token).toBe('tok-2');
  });

  it('stops publishing credentials after logout', () => {
    const { result } = renderAuth();
    act(() => result.current.login(alice, 'tok-1'));

    act(() => result.current.logout());

    expect(getAuth()).toEqual({ token: null, tenantId: null });
  });
});

describe('useAuth', () => {
  it('refuses to run outside a provider', () => {
    // React logs the throw as a component error before it propagates. That is
    // the behaviour under test, so silence the report rather than let an
    // expected stack trace sit in every green test run.
    const reported = vi.spyOn(console, 'error').mockImplementation(() => {});

    try {
      expect(() => renderHook(() => useAuth())).toThrow(
        /must be used within an <AuthProvider>/,
      );
    } finally {
      reported.mockRestore();
    }
  });
});
