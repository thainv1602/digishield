import { useCallback, useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useT } from '@/shared/i18n/i18nContext';
import {
  clearActingTenant,
  getActingTenant,
  onActingTenantChange,
  type ActingTenant,
} from '@/shared/api/actingTenant';

/**
 * Persistent warning shown while a platform super admin is acting inside another
 * tenant. Every page is showing someone else's data at that point, so the state
 * must never be invisible — hence a full-width bar above the content rather than
 * a badge tucked into the top bar.
 */
export function ActingTenantBanner() {
  const t = useT();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [acting, setActing] = useState<ActingTenant | null>(() => getActingTenant());

  useEffect(() => onActingTenantChange(() => setActing(getActingTenant())), []);

  const exit = useCallback(() => {
    clearActingTenant();
    // Everything cached was fetched as the other tenant — drop it all rather
    // than let a stale page show their data after leaving.
    queryClient.clear();
    navigate('/super/tenants');
  }, [navigate, queryClient]);

  if (!acting) return null;

  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 20px',
        background: 'rgba(217,119,6,.12)',
        borderBottom: '1px solid rgba(217,119,6,.35)',
        color: 'var(--color-text)',
        fontSize: 13,
      }}
    >
      <ShieldAlert size={16} color="var(--color-amber)" />
      <span>
        {t('Đang xem dữ liệu của tenant ')}
        <strong>{acting.name}</strong>
        {t('. Mọi thao tác được ghi vào nhật ký kiểm toán của tenant đó.')}
      </span>
      <button
        type="button"
        onClick={exit}
        style={{
          marginLeft: 'auto',
          background: 'none',
          border: '1px solid rgba(217,119,6,.5)',
          borderRadius: 6,
          padding: '4px 12px',
          fontSize: 12,
          color: 'var(--color-text)',
          cursor: 'pointer',
        }}
      >
        {t('Thoát khỏi tenant')}
      </button>
    </div>
  );
}
