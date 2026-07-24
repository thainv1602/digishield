import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import styles from './NotificationBell.module.css';

interface Notification {
  id: number;
  title: string;
  meta: string;
  critical?: boolean;
}

const DEFAULT_NOTIFICATIONS: Notification[] = [
  { id: 1, title: 'Cảnh báo: chiến dịch SMS lừa đảo mới', meta: '2 phút trước · critical', critical: true },
  { id: 2, title: 'Nhắc: Bài kiểm tra Phishing đến hạn 30/06', meta: '1 giờ trước' },
  { id: 3, title: 'Bạn nhận huy hiệu "Thợ săn phishing"', meta: 'Hôm qua' },
];

/** Topbar notification bell with unread badge + dropdown panel. */
export function NotificationBell({
  count = 3,
  notifications = DEFAULT_NOTIFICATIONS,
  onMarkRead,
}: {
  count?: number;
  notifications?: Notification[];
  onMarkRead?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  return (
    <div className={styles.root} ref={ref}>
      <button
        type="button"
        className={styles.button}
        aria-label="Thông báo"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Bell size={15} strokeWidth={2} color="var(--color-muted)" />
        {count > 0 ? <span className={styles.badge}>{count}</span> : null}
      </button>

      {open ? (
        <div className={styles.panel} role="menu">
          <div className={styles.panelHead}>
            <span className={styles.panelTitle}>Thông báo</span>
            <button
              type="button"
              className={styles.panelAction}
              style={{ background: 'none', border: 'none', cursor: 'pointer', font: 'inherit' }}
              onClick={() => {
                onMarkRead?.();
                setOpen(false);
              }}
            >
              Đánh dấu đã đọc
            </button>
          </div>
          <div className={styles.list}>
            {notifications.map((n) => (
              <div
                key={n.id}
                className={`${styles.item} ${n.critical ? styles.itemCritical : ''}`}
              >
                <div className={styles.itemTitle}>{n.title}</div>
                <div className={styles.itemMeta}>{n.meta}</div>
              </div>
            ))}
          </div>
          {notifications.length === 0 ? (
            <div className={styles.panelFoot}>Không có thông báo</div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
