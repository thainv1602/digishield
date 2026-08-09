import type { ReactNode } from 'react';
import type { User } from 'oidc-client-ts';
import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ROLES } from './roles';
import type * as CognitoModule from './cognito';

/**
 * The Cognito branch of AuthProvider — everything gated behind `cognitoEnabled`.
 * It is inert in dev, CI and E2E (no VITE_COGNITO_* set), which is exactly why
 * it needs unit tests: the deployed build is the only place it runs, and a
 * mistake there locks every user out of the real environment.
 */

/** Controllable UserManager double, shared with the hoisted module mock. */
const cognito = vi.hoisted(() => {
  const listeners = new Set<(u: unknown) => void>();
  return {
    listeners,
    signinRedirectCallback: vi.fn(),
    getUser: vi.fn(),
    removeUser: vi.fn(),
    signinRedirect: vi.fn(),
    addUserLoaded: vi.fn((cb: (u: unknown) => void) => void listeners.add(cb)),
    removeUserLoaded: vi.fn((cb: (u: unknown) => void) => void listeners.delete(cb)),
  };
});

vi.mock('./cognito', async () => {
  const actual = await vi.importActual<typeof CognitoModule>('./cognito');
  return {
    // Force the branch on: the real module reads import.meta.env, which is
    // deliberately unset everywhere tests run.
    cognitoEnabled: true,
    userManager: {
      signinRedirectCallback: cognito.signinRedirectCallback,
      getUser: cognito.getUser,
      removeUser: cognito.removeUser,
      signinRedirect: cognito.signinRedirect,
      events: {
        addUserLoaded: cognito.addUserLoaded,
        removeUserLoaded: cognito.removeUserLoaded,
      },
    },
    // The real mapping — this file is about the flow, not the claim mapping.
    toCurrentUser: actual.toCurrentUser,
  };
});

const { AuthProvider } = await import('./AuthContext');
const { useAuth } = await import('./useAuth');

/** Minimal stand-in for an oidc-client-ts User. */
const oidcUser = (over: Partial<User> = {}): User =>
  ({
    profile: { sub: 'sub-1', email: 'a@b.com', 'cognito:groups': ['org_admin'] },
    access_token: 'tok-cognito',
    expired: false,
    ...over,
  }) as unknown as User;

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;
const renderAuth = () => renderHook(() => useAuth(), { wrapper });

/** Put the browser at `url` without navigating. */
const at = (url: string) => window.history.replaceState({}, '', url);

beforeEach(() => {
  vi.clearAllMocks();
  cognito.listeners.clear();
  cognito.getUser.mockResolvedValue(null);
  cognito.signinRedirectCallback.mockResolvedValue(oidcUser());
  cognito.removeUser.mockResolvedValue(undefined);
  cognito.signinRedirect.mockResolvedValue(undefined);
  at('/');
});

afterEach(() => {
  at('/');
});

describe('AuthProvider · first load', () => {
  it('blocks the app while the session is being restored', () => {
    // Rendering children first would flash the login screen at a user who is
    // already signed in, then swap it out.
    cognito.getUser.mockReturnValue(new Promise(() => {}));

    render(
      <AuthProvider>
        <div>app content</div>
      </AuthProvider>,
    );

    expect(screen.queryByText('app content')).not.toBeInTheDocument();
    expect(screen.getByText('Đang tải…')).toBeInTheDocument();
  });

  it('restores a stored session without a redirect', async () => {
    cognito.getUser.mockResolvedValue(oidcUser());

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(cognito.signinRedirectCallback).not.toHaveBeenCalled();
    expect(result.current.token).toBe('tok-cognito');
    expect(result.current.user?.role).toBe(ROLES.ORG_ADMIN);
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('ignores a stored session that has expired', async () => {
    cognito.getUser.mockResolvedValue(oidcUser({ expired: true }));

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('signs nobody in when there is no stored session', async () => {
    cognito.getUser.mockResolvedValue(null);

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(result.current.user).toBeNull();
  });
});

describe('AuthProvider · hosted-UI redirect callback', () => {
  it('completes the code exchange when Cognito redirects back', async () => {
    at('/?code=auth-code&state=xyz');

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(cognito.signinRedirectCallback).toHaveBeenCalledTimes(1);
    expect(cognito.getUser).not.toHaveBeenCalled();
    expect(result.current.token).toBe('tok-cognito');
  });

  it('scrubs the authorization code from the address bar', async () => {
    at('/?code=auth-code&state=xyz');

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    // The code is single-use and ends up in history, bookmarks and referrers if
    // it is left there.
    expect(window.location.search).toBe('');
  });

  it('takes the restore path when only one redirect parameter is present', async () => {
    at('/?code=auth-code');

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(cognito.signinRedirectCallback).not.toHaveBeenCalled();
    expect(cognito.getUser).toHaveBeenCalledTimes(1);
  });

  it('falls through to the login screen when the exchange fails', async () => {
    at('/?code=bad-code&state=xyz');
    cognito.signinRedirectCallback.mockRejectedValue(new Error('invalid_grant'));

    const { result } = renderAuth();

    // The important part is that initializing still clears: a failed exchange
    // must not leave the app stuck on the loading screen forever.
    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('clears the loading screen even when restoring throws', async () => {
    cognito.getUser.mockRejectedValue(new Error('storage unavailable'));

    const { result } = renderAuth();

    await waitFor(() => expect(result.current.initializing).toBe(false));
    expect(result.current.isAuthenticated).toBe(false);
  });
});

describe('AuthProvider · silent renew', () => {
  it('adopts the token from a userLoaded event', async () => {
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.initializing).toBe(false));

    act(() => {
      cognito.listeners.forEach((cb) => cb(oidcUser({ access_token: 'tok-renewed' })));
    });

    // automaticSilentRenew replaces the token behind the app's back; without
    // this listener the app would keep sending the expired one until a 401.
    expect(result.current.token).toBe('tok-renewed');
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('stops listening once the provider unmounts', async () => {
    const { result, unmount } = renderAuth();
    await waitFor(() => expect(result.current.initializing).toBe(false));

    unmount();

    expect(cognito.removeUserLoaded).toHaveBeenCalledTimes(1);
    expect(cognito.listeners.size).toBe(0);
  });
});

describe('AuthProvider · sign-in and sign-out', () => {
  it('forces re-authentication instead of reusing the hosted-UI cookie', async () => {
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.initializing).toBe(false));

    act(() => result.current.signinRedirect());

    // Without prompt=login, signing out and back in silently reuses Cognito's
    // session cookie and never asks for credentials.
    expect(cognito.signinRedirect).toHaveBeenCalledWith({
      extraQueryParams: { prompt: 'login' },
    });
  });

  it('drops the stored Cognito session on logout', async () => {
    cognito.getUser.mockResolvedValue(oidcUser());
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

    act(() => result.current.logout());

    expect(cognito.removeUser).toHaveBeenCalledTimes(1);
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('still clears local state when removing the stored session fails', async () => {
    cognito.getUser.mockResolvedValue(oidcUser());
    cognito.removeUser.mockRejectedValue(new Error('storage gone'));
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

    act(() => result.current.logout());

    // A logout that leaves the user signed in because storage misbehaved is
    // worse than one that leaves a stale entry behind.
    expect(result.current.user).toBeNull();
    expect(result.current.token).toBeNull();
  });
});
