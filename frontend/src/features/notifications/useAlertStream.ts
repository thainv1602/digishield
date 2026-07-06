import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/app/auth/useAuth';
import { queryKeys } from '@/shared/api/queryKeys';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';
import { useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import type { Notification } from './api';

/** Envelope pushed by the backend `WebSocketRealtimeNotifier`. */
interface AlertEnvelope {
  kind: string;
  notification: Notification;
}

/** Reconnect backoff (ms): grows on repeated failures, capped. */
const BASE_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;

/**
 * Derive the WebSocket URL for the alert stream from the REST base URL, swapping
 * the scheme (http→ws, https→wss) and pointing at `/ws/notifications` on the same
 * host. Auth travels in the query string because a browser cannot set an
 * `Authorization` header on a WebSocket handshake: `access_token` (validated in
 * prod) and `tenant` (used by the dev handshake).
 */
function buildStreamUrl(token: string | null, tenantId: string): string | null {
  const base = import.meta.env.VITE_API_BASE_URL;
  if (!base) return null;
  let api: URL;
  try {
    api = new URL(base, window.location.origin);
  } catch {
    return null;
  }
  const proto = api.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = new URL(`${proto}//${api.host}/ws/notifications`);
  url.searchParams.set('tenant', tenantId);
  if (token) url.searchParams.set('access_token', token);
  return url.toString();
}

/**
 * Opens a WebSocket to the backend alert stream and, on each pushed alert,
 * refreshes the notifications query (bell + Alert Center) and raises a toast — so
 * a broadcast surfaces in real time instead of on the next poll. Auto-reconnects
 * with capped backoff. Side-effect only; mount once inside the authenticated shell.
 */
export function useAlertStream(): void {
  const { token, user } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const t = useT();

  const tenantId = user?.tenantId ?? (import.meta.env.DEV ? DEMO_TENANT_ID : null);

  // Keep the latest callbacks in refs so the socket effect doesn't tear down and
  // reconnect on every render (only on token/tenant change).
  const handlersRef = useRef({ qc, toast, t });
  handlersRef.current = { qc, toast, t };

  useEffect(() => {
    if (!tenantId) return;
    // In production the stream requires a real token; skip until we have one.
    if (!import.meta.env.DEV && !token) return;

    const url = buildStreamUrl(token, tenantId);
    if (!url) return;

    let socket: WebSocket | null = null;
    let retryMs = BASE_RETRY_MS;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let closedByUs = false;

    const connect = () => {
      socket = new WebSocket(url);

      socket.onopen = () => {
        retryMs = BASE_RETRY_MS;
      };

      socket.onmessage = (event) => {
        let envelope: AlertEnvelope | null = null;
        try {
          envelope = JSON.parse(event.data as string) as AlertEnvelope;
        } catch {
          return;
        }
        if (envelope?.kind !== 'alert') return;
        const { qc: queryClient, toast: showToast, t: translate } = handlersRef.current;
        void queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
        const message =
          envelope.notification?.title ?? envelope.notification?.body ?? translate('Cảnh báo mới');
        showToast({ msg: message, variant: 'warning' });
      };

      socket.onclose = () => {
        if (closedByUs) return;
        retryTimer = setTimeout(connect, retryMs);
        retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
      };

      // On error, let onclose drive the reconnect (browsers fire close after error).
      socket.onerror = () => socket?.close();
    };

    connect();

    return () => {
      closedByUs = true;
      if (retryTimer) clearTimeout(retryTimer);
      socket?.close();
    };
  }, [token, tenantId]);
}
