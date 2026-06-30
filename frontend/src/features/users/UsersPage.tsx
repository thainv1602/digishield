import { Button, DataTable, Input, Select, StatusPill, riskToVariant } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { Search } from 'lucide-react';
import { useT } from '@/shared/i18n/I18nProvider';
import { useUsers, type UserRow } from './api';

/**
 * UsersPage — users & smart groups management.
 * Pixel-matched to the design handoff "USERS & GROUPS" screen.
 *
 * Data comes from the live backend via `useUsers()` (`GET /users`). The hook
 * maps the BE `department`/`riskScore` fields onto the FE `dept`/`risk` shape.
 * Loading/error/empty states handled inline below.
 */

export default function UsersPage() {
  const t = useT();
  const { data: users, isLoading, isError, refetch } = useUsers();
  const rows = users ?? [];
  const columns: ColumnDef<UserRow>[] = [
    {
      id: 'user',
      header: t('Người dùng'),
      cell: (u) => (
        <div>
          <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--color-text)' }}>{u.name}</div>
          <div style={{ fontSize: 11.5, color: 'var(--color-muted)' }}>{u.email}</div>
        </div>
      ),
    },
    {
      id: 'role',
      header: t('Vai trò'),
      cell: (u) => <span style={{ color: 'var(--color-muted)' }}>{u.role}</span>,
      width: '120px',
    },
    {
      id: 'dept',
      header: t('Phòng ban'),
      cell: (u) => <span style={{ color: 'var(--color-muted)' }}>{u.dept}</span>,
      width: '120px',
    },
    {
      id: 'risk',
      header: 'Risk',
      cell: (u) => (
        <StatusPill variant={riskToVariant(u.risk)} dot={false}>
          {u.risk}
        </StatusPill>
      ),
      width: '80px',
    },
    {
      id: 'edit',
      header: '',
      cell: () => (
        <button
          type="button"
          style={{ fontSize: 12, color: 'var(--color-blue)', cursor: 'pointer', background: 'none', border: 'none' }}
        >
          {t('Sửa')}
        </button>
      ),
      width: '60px',
      align: 'right',
    },
  ];

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 20,
          }}
        >
          <div>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui",
                fontSize: 22,
                fontWeight: 700,
                color: 'var(--color-text)',
                letterSpacing: '-.02em',
              }}
            >
              {t('Người dùng & Nhóm')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {t('{n} người dùng · 8 nhóm thông minh', { n: rows.length })}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <Button variant="outline">{t('Nhập CSV / SCIM')}</Button>
            <Button variant="primary">{t('+ Thêm người dùng')}</Button>
          </div>
        </div>

        <div
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          {/* Filter bar */}
          <div
            style={{
              padding: '14px 20px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <div style={{ flex: 1, position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Search
                size={13}
                color="var(--color-muted)"
                style={{ position: 'absolute', left: 12, pointerEvents: 'none' }}
                aria-hidden="true"
              />
              <Input
                placeholder={t('Tìm kiếm...')}
                aria-label={t('Tìm kiếm người dùng')}
                style={{ paddingLeft: 32, width: '100%' }}
              />
            </div>
            <Select aria-label={t('Lọc theo vai trò')} defaultValue="">
              <option value="">{t('Vai trò')}</option>
              <option>{t('Nhân viên')}</option>
              <option>{t('Trưởng phòng')}</option>
              <option>Admin IT</option>
            </Select>
            <Select aria-label={t('Lọc theo phòng ban')} defaultValue="">
              <option value="">{t('Phòng ban')}</option>
              <option>{t('Kế toán')}</option>
              <option>{t('Kinh doanh')}</option>
              <option>IT</option>
            </Select>
          </div>

          {isLoading && <TableMessage>{t('Đang tải người dùng…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
                {t('Không tải được người dùng.')}{' '}
              </span>
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <TableMessage>{t('Không có người dùng nào.')}</TableMessage>
          )}
          {!isLoading && !isError && rows.length > 0 && (
            <DataTable<UserRow> columns={columns} data={rows} rowKey={(u) => u.id} />
          )}
        </div>
      </div>
    </>
  );
}

function TableMessage({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        padding: '28px 16px',
        textAlign: 'center',
        fontSize: 13.5,
        color: 'var(--color-muted)',
      }}
    >
      {children}
    </div>
  );
}

const inlineRetry: React.CSSProperties = {
  background: 'none',
  border: 'none',
  color: 'var(--color-blue)',
  fontWeight: 600,
  fontSize: 13.5,
  cursor: 'pointer',
  padding: 0,
};
