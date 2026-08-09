import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  getAuth,
  handleUnauthorized,
  setAuthAccessor,
  setUnauthorizedHandler,
} from './authBridge';

/**
 * The bridge is the only seam between React state and the axios layer. Its
 * contract is narrow but load-bearing: the axios interceptors have no way to
 * read a hook, so if the bridge hands back a stale token every authenticated
 * request goes out wrong, and a 401 either fails to log the user out or logs
 * them out when it should not.
 */
describe('authBridge', () => {
  afterEach(() => {
    setAuthAccessor(() => ({ token: null, tenantId: null }));
    setUnauthorizedHandler(null);
  });

  it('reports no session before a provider registers one', () => {
    expect(getAuth()).toEqual({ token: null, tenantId: null });
  });

  it('returns whatever the registered accessor currently holds', () => {
    setAuthAccessor(() => ({ token: 'tok-1', tenantId: 'tenant-1' }));

    expect(getAuth()).toEqual({ token: 'tok-1', tenantId: 'tenant-1' });
  });

  it('reads the accessor on every call rather than caching its first answer', () => {
    // AuthProvider re-registers an accessor closing over fresh state on every
    // render. If getAuth cached, a token refresh would never reach the wire.
    let token = 'first';
    setAuthAccessor(() => ({ token, tenantId: null }));

    expect(getAuth().token).toBe('first');

    token = 'second';

    expect(getAuth().token).toBe('second');
  });

  it('replaces the accessor rather than accumulating them', () => {
    setAuthAccessor(() => ({ token: 'old', tenantId: null }));
    setAuthAccessor(() => ({ token: 'new', tenantId: null }));

    expect(getAuth().token).toBe('new');
  });

  it('invokes the registered unauthorized handler', () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    handleUnauthorized();

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does nothing when no unauthorized handler is registered', () => {
    setUnauthorizedHandler(null);

    // A 401 arriving before the app mounts its handler must not throw inside an
    // axios interceptor, where the error would surface as an opaque failure.
    expect(() => handleUnauthorized()).not.toThrow();
  });

  it('stops calling a handler once it is unregistered', () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setUnauthorizedHandler(null);

    handleUnauthorized();

    expect(handler).not.toHaveBeenCalled();
  });
});
