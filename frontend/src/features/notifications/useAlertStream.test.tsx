import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The alert stream is the only long-lived socket in the app. Its reconnect loop
 * is the part worth pinning: a backoff that never resets hammers the server
 * after one blip, and a reconnect that fires after unmount keeps a dead
 * component's socket alive for the rest of the session.
 */

const auth = vi.hoisted(() => ({
  token: 'tok-1' as string | null,
  user: { tenantId: 'tenant-1' } as { tenantId: string } | null,
}));
const toast = vi.hoisted(() => vi.fn());

vi.mock('@/app/auth/useAuth', () => ({ useAuth: () => auth }));
vi.mock('@/shared/ui', () => ({ useToast: () => toast }));
vi.mock('@/shared/i18n/i18nContext', () => ({ useT: () => (s: string) => s }));

const { useAlertStream } = await import('./useAlertStream');
const { queryKeys } = await import('@/shared/api/queryKeys');

/** Records every socket the hook opens and lets a test drive its callbacks. */
class FakeSocket {
  static opened: FakeSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    FakeSocket.opened.push(this);
  }

  close() {
    this.closed = true;
    // Browsers fire close after an explicit close(); the hook relies on that.
    this.onclose?.();
  }
}

const latest = (): FakeSocket => {
  const socket = FakeSocket.opened[FakeSocket.opened.length - 1];
  if (!socket) throw new Error('no socket was opened');
  return socket;
};

const alertFrame = (title: string) =>
  JSON.stringify({ kind: 'alert', notification: { title } });

let queryClient: QueryClient;
/** Stands in for invalidateQueries so the assertion is on the call, not the cache. */
const invalidate = vi.fn();

const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

const mount = () => renderHook(() => useAlertStream(), { wrapper });

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.example.test/api/v1');
  FakeSocket.opened = [];
  toast.mockClear();
  auth.token = 'tok-1';
  auth.user = { tenantId: 'tenant-1' };
  vi.stubGlobal('WebSocket', FakeSocket);
  invalidate.mockClear();
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.invalidateQueries = invalidate as unknown as QueryClient['invalidateQueries'];
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('useAlertStream · connection', () => {
  it('opens one socket for the current tenant', () => {
    mount();

    expect(FakeSocket.opened).toHaveLength(1);
    const url = new URL(latest().url);
    expect(url.pathname).toBe('/ws/notifications');
    expect(url.searchParams.get('tenant')).toBe('tenant-1');
  });

  it('carries the token in the query string', () => {
    mount();

    // A browser cannot set an Authorization header on a WebSocket handshake,
    // so the token has to travel here or not at all.
    expect(new URL(latest().url).searchParams.get('access_token')).toBe('tok-1');
  });

  it('upgrades to wss when the API is served over https', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test/api/v1');

    mount();

    expect(latest().url.startsWith('wss://')).toBe(true);
  });

  it('uses ws for a plain-http API', () => {
    mount();

    expect(latest().url.startsWith('ws://')).toBe(true);
  });

  it('opens nothing when no API base URL is configured', () => {
    vi.stubEnv('VITE_API_BASE_URL', '');

    mount();

    expect(FakeSocket.opened).toHaveLength(0);
  });

  it('omits the token from the URL when there is none', () => {
    auth.token = null;

    mount();

    expect(new URL(latest().url).searchParams.has('access_token')).toBe(false);
  });
});

describe('useAlertStream · incoming frames', () => {
  it('refreshes notifications and raises a toast on an alert', () => {
    mount();

    act(() => latest().onmessage?.({ data: alertFrame('Cảnh báo lừa đảo') }));

    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.notifications });
    expect(toast).toHaveBeenCalledWith({ msg: 'Cảnh báo lừa đảo', variant: 'warning' });
  });

  it('falls back to the body when an alert carries no title', () => {
    mount();

    act(() =>
      latest().onmessage?.({
        data: JSON.stringify({ kind: 'alert', notification: { body: 'nội dung' } }),
      }),
    );

    expect(toast).toHaveBeenCalledWith({ msg: 'nội dung', variant: 'warning' });
  });

  it('ignores frames of another kind', () => {
    mount();

    act(() => latest().onmessage?.({ data: JSON.stringify({ kind: 'ping' }) }));

    expect(invalidate).not.toHaveBeenCalled();
    expect(toast).not.toHaveBeenCalled();
  });

  it('survives a frame that is not JSON', () => {
    mount();

    // A malformed frame must not throw inside the socket callback, where it
    // would tear down the handler and stop every later alert.
    expect(() => act(() => latest().onmessage?.({ data: 'not json' }))).not.toThrow();
    expect(toast).not.toHaveBeenCalled();
  });
});

describe('useAlertStream · reconnect', () => {
  it('reconnects after the base delay when the socket drops', () => {
    mount();
    expect(FakeSocket.opened).toHaveLength(1);

    act(() => latest().onclose?.());
    expect(FakeSocket.opened).toHaveLength(1);

    act(() => void vi.advanceTimersByTime(1_000));
    expect(FakeSocket.opened).toHaveLength(2);
  });

  it('doubles the delay on each successive failure', () => {
    mount();

    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(1_000));
    expect(FakeSocket.opened).toHaveLength(2);

    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(1_000));
    // Second wait is 2s, so 1s is not yet enough.
    expect(FakeSocket.opened).toHaveLength(2);

    act(() => void vi.advanceTimersByTime(1_000));
    expect(FakeSocket.opened).toHaveLength(3);
  });

  it('caps the delay so a long outage still retries every 30s', () => {
    mount();

    // Drive the backoff past its ceiling.
    for (let i = 0; i < 10; i += 1) {
      act(() => latest().onclose?.());
      act(() => void vi.advanceTimersByTime(60_000));
    }
    const before = FakeSocket.opened.length;

    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(30_000));

    expect(FakeSocket.opened.length).toBe(before + 1);
  });

  it('resets the backoff once a connection succeeds', () => {
    mount();

    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(1_000));
    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(2_000));
    expect(FakeSocket.opened).toHaveLength(3);

    // A successful open means the outage is over; the next drop should wait the
    // base delay again rather than the escalated one.
    act(() => latest().onopen?.());
    act(() => latest().onclose?.());
    act(() => void vi.advanceTimersByTime(1_000));

    expect(FakeSocket.opened).toHaveLength(4);
  });

  it('closes the socket on error and lets close drive the retry', () => {
    mount();
    const socket = latest();

    act(() => socket.onerror?.());

    expect(socket.closed).toBe(true);
  });
});

describe('useAlertStream · teardown', () => {
  it('closes the socket when the component unmounts', () => {
    const { unmount } = mount();
    const socket = latest();

    unmount();

    expect(socket.closed).toBe(true);
  });

  it('does not reconnect after unmounting', () => {
    const { unmount } = mount();

    unmount();
    act(() => void vi.advanceTimersByTime(60_000));

    // Closing during teardown fires onclose; without the closedByUs guard that
    // would schedule a reconnect for a component that no longer exists.
    expect(FakeSocket.opened).toHaveLength(1);
  });

  it('cancels a retry that was already scheduled', () => {
    const { unmount } = mount();

    act(() => latest().onclose?.());
    unmount();
    act(() => void vi.advanceTimersByTime(60_000));

    expect(FakeSocket.opened).toHaveLength(1);
  });
});
