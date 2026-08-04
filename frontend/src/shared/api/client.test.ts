import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosInstance } from './client';
import { setAuthAccessor, setUnauthorizedHandler } from './authBridge';

/**
 * The response interceptor decides when a failed request costs the user their
 * session. It used to log out on every 401, so an API that could not reach its
 * identity provider — answering 401 to a perfectly good token — bounced every
 * sign-in straight back to /login.
 */
describe('axios response interceptor', () => {
  const onUnauthorized = vi.fn();
  const originalAdapter = axiosInstance.defaults.adapter;

  /** Make every request fail with `status`, the way a real server response would. */
  const failWith = (status: number) => {
    axiosInstance.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
      throw new AxiosError(`HTTP ${status}`, String(status), config, null, {
        data: {},
        status,
        statusText: '',
        headers: new AxiosHeaders(),
        config,
      });
    };
  };

  beforeEach(() => {
    onUnauthorized.mockClear();
    setUnauthorizedHandler(onUnauthorized);
    setAuthAccessor(() => ({ token: 'a-token', tenantId: null }));
  });

  afterEach(() => {
    if (originalAdapter) {
      axiosInstance.defaults.adapter = originalAdapter;
    } else {
      delete axiosInstance.defaults.adapter;
    }
    setUnauthorizedHandler(null);
    setAuthAccessor(() => ({ token: null, tenantId: null }));
  });

  it('logs out when a request carrying a token is answered 401', async () => {
    failWith(401);

    await expect(axiosInstance.get('/api/v1/orgs')).rejects.toThrow();

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('keeps the session when the API cannot validate tokens (503)', async () => {
    failWith(503);

    await expect(axiosInstance.get('/api/v1/orgs')).rejects.toThrow();

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('ignores a 401 on a request that presented no token', async () => {
    setAuthAccessor(() => ({ token: null, tenantId: null }));
    failWith(401);

    await expect(axiosInstance.get('/api/v1/orgs')).rejects.toThrow();

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('leaves other failures alone', async () => {
    failWith(500);

    await expect(axiosInstance.get('/api/v1/orgs')).rejects.toThrow();

    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
