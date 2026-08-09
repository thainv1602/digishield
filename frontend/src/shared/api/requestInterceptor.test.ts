import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { axiosInstance } from './client';
import { setAuthAccessor } from './authBridge';
import { clearActingTenant, setActingTenant } from './actingTenant';
import { DEMO_TENANT_ID } from './tenant';

/**
 * The request interceptor decides what every authenticated call actually
 * carries. client.test.ts covers the response side — when a 401 costs the user
 * their session — but nothing covered the outbound half, where the token is
 * attached in the first place. A token that never reaches the wire produces the
 * same 401 as an expired one, and the response interceptor would then log the
 * user out for a bug on our side.
 */
describe('axios request interceptor', () => {
  const originalAdapter = axiosInstance.defaults.adapter;
  let sent: InternalAxiosRequestConfig | null = null;

  /** Succeed every request, keeping the config the interceptor produced. */
  const captureRequest = () => {
    axiosInstance.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
      sent = config;
      return {
        data: {},
        status: 200,
        statusText: 'OK',
        headers: new AxiosHeaders(),
        config,
      };
    };
  };

  /** Header value the interceptor set, or undefined when it set none. */
  const header = (name: string): string | undefined => {
    const value = sent?.headers?.get(name);
    return value == null ? undefined : String(value);
  };

  beforeEach(() => {
    sent = null;
    localStorage.clear();
    setAuthAccessor(() => ({ token: null, tenantId: null }));
    captureRequest();
  });

  afterEach(() => {
    if (originalAdapter) {
      axiosInstance.defaults.adapter = originalAdapter;
    } else {
      delete axiosInstance.defaults.adapter;
    }
    localStorage.clear();
    setAuthAccessor(() => ({ token: null, tenantId: null }));
  });

  it('sends the current token as a bearer credential', async () => {
    setAuthAccessor(() => ({ token: 'tok-abc', tenantId: 'tenant-1' }));

    await axiosInstance.get('/api/v1/orgs');

    expect(header('Authorization')).toBe('Bearer tok-abc');
  });

  it('omits Authorization entirely when there is no token', async () => {
    setAuthAccessor(() => ({ token: null, tenantId: 'tenant-1' }));

    await axiosInstance.get('/api/v1/orgs');

    // Not "Bearer null" and not an empty header — absent, so the backend treats
    // the call as anonymous rather than as a malformed credential.
    expect(header('Authorization')).toBeUndefined();
  });

  it('picks up a token that appeared after an earlier anonymous request', async () => {
    await axiosInstance.get('/api/v1/orgs');
    expect(header('Authorization')).toBeUndefined();

    setAuthAccessor(() => ({ token: 'tok-after-login', tenantId: null }));
    await axiosInstance.get('/api/v1/orgs');

    expect(header('Authorization')).toBe('Bearer tok-after-login');
  });

  it("scopes the request to the signed-in user's tenant", async () => {
    setAuthAccessor(() => ({ token: 'tok', tenantId: 'tenant-42' }));

    await axiosInstance.get('/api/v1/users');

    expect(header('X-Tenant-Id')).toBe('tenant-42');
  });

  it('prefers the real tenant over the demo fallback', async () => {
    setAuthAccessor(() => ({ token: 'tok', tenantId: 'tenant-42' }));

    await axiosInstance.get('/api/v1/users');

    expect(header('X-Tenant-Id')).not.toBe(DEMO_TENANT_ID);
  });

  it('adds X-Acting-Tenant only while a super admin has stepped into a tenant', async () => {
    setAuthAccessor(() => ({ token: 'tok', tenantId: 'tenant-1' }));

    await axiosInstance.get('/api/v1/users');
    expect(header('X-Acting-Tenant')).toBeUndefined();

    setActingTenant({ id: 'tenant-9', name: 'Acme' });
    await axiosInstance.get('/api/v1/users');
    expect(header('X-Acting-Tenant')).toBe('tenant-9');

    clearActingTenant();
    await axiosInstance.get('/api/v1/users');
    expect(header('X-Acting-Tenant')).toBeUndefined();
  });

  it('defaults Accept-Language to Vietnamese', async () => {
    await axiosInstance.get('/api/v1/orgs');

    expect(header('Accept-Language')).toBe('vi');
  });

  it("follows the app's stored language choice", async () => {
    localStorage.setItem('digishield.lang', 'en');

    await axiosInstance.get('/api/v1/orgs');

    expect(header('Accept-Language')).toBe('en');
  });
});
