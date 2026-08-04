import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios';
import { getActingTenant } from './actingTenant';
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
  // A super admin who stepped into a tenant acts inside it for every request.
  // The backend accepts this only for a validated SUPER_ADMIN, so sending it
  // as anyone else is a no-op rather than a privilege escalation.
  const acting = getActingTenant();
  if (acting) {
    config.headers.set('X-Acting-Tenant', acting.id);
  }
  // Tell the backend which language to render its own text in (notifications,
  // AIDA summaries, intervention messages…). Mirrors the app's language switch,
  // which persists the choice under `digishield.lang`; defaults to Vietnamese.
  const lang = (typeof localStorage !== 'undefined' && localStorage.getItem('digishield.lang')) || 'vi';
  config.headers.set('Accept-Language', lang);
  return config;
});

/**
 * True when the failing request actually presented a bearer token.
 *
 * <p>A 401 on a request that carried no token says nothing about the session —
 * it is just an anonymous call to a protected endpoint. Logging out on those
 * would drag a user who is signing in, or who is sitting on a public page, off
 * to /login for a request that was never theirs.
 */
function carriedToken(config: AxiosRequestConfig | undefined): boolean {
  // AxiosHeaders normalizes case and exposes a case-insensitive get(); a plain
  // object (hand-built configs, tests) has neither, so read it both ways.
  const headers = config?.headers as
    | { get?: (name: string) => unknown; Authorization?: unknown; authorization?: unknown }
    | undefined;
  const auth =
    typeof headers?.get === 'function'
      ? headers.get('Authorization')
      : (headers?.Authorization ?? headers?.authorization);
  return typeof auth === 'string' && auth.startsWith('Bearer ');
}

// ---- Response interceptor: 401 handling ----
axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // 401 means *this token* was rejected — expired, malformed, wrong issuer.
    // It deliberately does not cover "the API cannot validate tokens right now":
    // that answers 503 (JwtUnavailableEntryPoint), which TanStack Query retries
    // with the session left intact. Conflating the two is what turned a Cognito
    // outage into "sign in, get bounced straight back to the login screen".
    if (error.response?.status === 401 && carriedToken(error.config)) {
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
