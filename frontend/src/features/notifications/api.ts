import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Thin, hand-written typed fetchers + hooks for notifications (bell dropdown +
 * Alert Center).
 *
 * Mirrors `NotificationView` from the notification module
 * (`GET /notifications`). `NotificationView` is a plain record, so Jackson
 * serializes it as camelCase. Enum values arrive lowercase
 * (type: reminder|alert|system, channel: in_app|email|sms,
 * status: scheduled|sent|read).
 */

export type NotificationType = 'reminder' | 'alert' | 'system' | string;
export type NotificationChannel = 'in_app' | 'email' | 'sms' | string;
export type NotificationStatus = 'scheduled' | 'sent' | 'read' | string;

export interface Notification {
  id: string;
  userId: string | null;
  type: NotificationType | null;
  channel: NotificationChannel | null;
  status: NotificationStatus | null;
  title: string | null;
  body: string | null;
  /** ISO-8601 instant (BE field name is `scheduledAt`). */
  scheduledAt: string | null;
}

/** GET /notifications — notifications of the current tenant. */
export function fetchNotifications(signal?: AbortSignal): Promise<Notification[]> {
  return apiRequest<Notification[]>({
    url: '/notifications',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering the Alert Center + notification bell. */
export function useNotifications() {
  return useQuery({
    queryKey: queryKeys.notifications,
    queryFn: ({ signal }) => fetchNotifications(signal),
  });
}

/** Severity accepted by `POST /alerts/broadcast` (lowercase on the wire). */
export type BroadcastSeverity = 'info' | 'warning' | 'critical';

export interface BroadcastInput {
  message: string;
  severity: BroadcastSeverity;
}

/** Result of a broadcast: how many recipients it reached. */
export interface BroadcastResult {
  reach: number;
}

/** POST /alerts/broadcast — fan an alert out to every user in the tenant. */
export function broadcastAlert(body: BroadcastInput): Promise<BroadcastResult> {
  return apiRequest<BroadcastResult>({
    url: '/alerts/broadcast',
    method: 'POST',
    data: body,
  });
}

/** Mutation hook for the Alert Center composer; refreshes the history on success. */
export function useBroadcastAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: broadcastAlert,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.notifications });
    },
  });
}

/** POST /notifications/read-all — mark every notification of the tenant as read. */
export function markAllNotificationsRead(): Promise<{ updated: number }> {
  return apiRequest<{ updated: number }>({ url: '/notifications/read-all', method: 'POST' });
}

/** Mutation hook powering the bell's "Đánh dấu đã đọc"; refreshes the list. */
export function useMarkAllNotificationsRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.notifications });
    },
  });
}
