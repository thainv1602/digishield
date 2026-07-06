import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios';
import { getAuth, handleUnauthorized } from './authBridge';
import { DEMO_TENANT_ID } from './tenant';

/**
 * Hand-written axios instance used by every generated orval hook (configured as
 * the orval "mutator"). Centralizes baseURL, auth headers, and 401 handling.
 */
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30_000,
});

// ---- Request interceptor: attach auth + tenant headers ----
axiosInstance.interceptors.request.use((config) => {
  const { token, tenantId } = getAuth();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  // Prefer the logged-in user's tenant; in dev, fall back to the seeded demo
  // tenant so the live backend returns its sample data even before login.
  const effectiveTenantId = tenantId ?? (import.meta.env.DEV ? DEMO_TENANT_ID : null);
  if (effectiveTenantId) {
    config.headers.set('X-Tenant-Id', effectiveTenantId);
  }
  // Tell the backend which language to render its own text in (notifications,
  // AIDA summaries, intervention messages…). Mirrors the app's language switch,
  // which persists the choice under `digishield.lang`; defaults to Vietnamese.
  const lang = (typeof localStorage !== 'undefined' && localStorage.getItem('digishield.lang')) || 'vi';
  config.headers.set('Accept-Language', lang);
  return config;
});

// ---- Response interceptor: 401 handling ----
axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token missing/expired/invalid — clear auth and bounce to login.
      handleUnauthorized();
    }
    return Promise.reject(error);
  },
);

/**
 * Orval mutator. Generated hooks call `apiRequest<T>(config)` and expect the
 * response *data* (unwrapped) back. A cancel/abort signal is forwarded.
 */
export const apiRequest = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const source = axios.CancelToken.source();
  const promise = axiosInstance({
    ...config,
    ...options,
    cancelToken: source.token,
  }).then((response: AxiosResponse<T>) => response.data);

  // Allow TanStack Query to cancel in-flight requests.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (promise as any).cancel = () => {
    source.cancel('Query was cancelled by TanStack Query');
  };

  return promise;
};

export default apiRequest;

/** Convenience error type re-export for consumers. */
export type ApiError = AxiosError<{ title?: string; detail?: string; status?: number }>;
