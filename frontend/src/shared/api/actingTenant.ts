/**
 * Acting tenant — the tenant a platform super admin has stepped into.
 *
 * While set, every request carries it as `X-Acting-Tenant`. The backend honours
 * that header **only** when the validated JWT carries `ROLE_SUPER_ADMIN`
 * (`TenantFilter`), so this is a UI convenience, not the security boundary:
 * setting it by hand in localStorage gains an ordinary user nothing.
 *
 * Kept outside React (the axios interceptor is not a component) with a change
 * event so the shell banner and the console can re-render on switch.
 */

const STORAGE_KEY = 'digishield.actingTenant';
const CHANGE_EVENT = 'digishield:acting-tenant-changed';

export interface ActingTenant {
  id: string;
  /** Organisation name, for the banner. */
  name: string;
}

export function getActingTenant(): ActingTenant | null {
  if (typeof localStorage === 'undefined') return null;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as ActingTenant;
    return parsed && typeof parsed.id === 'string' ? parsed : null;
  } catch {
    // Corrupted entry: drop it rather than wedging every request.
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function setActingTenant(tenant: ActingTenant): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tenant));
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT));
}

export function clearActingTenant(): void {
  localStorage.removeItem(STORAGE_KEY);
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT));
}

/** Subscribes to switches; returns an unsubscribe function. */
export function onActingTenantChange(listener: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, listener);
  // `storage` fires when another tab switches tenant — keep them consistent.
  window.addEventListener('storage', listener);
  return () => {
    window.removeEventListener(CHANGE_EVENT, listener);
    window.removeEventListener('storage', listener);
  };
}
