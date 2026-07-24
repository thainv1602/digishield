import { useMemo, useState } from 'react';
import { Button, DataTable, Input, Select, StatusPill, riskToVariant } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { Search } from 'lucide-react';
import { useT } from '@/shared/i18n/I18nProvider';
import { ROLES } from '@/app/auth/roles';
import { useUsers, type UserRow } from './api';
import { UserFormDrawer } from './UserFormDrawer';
import { ImportDrawer } from './ImportDrawer';

const ROLE_OPTIONS = Object.values(ROLES);

/**
 * UsersPage — users & smart groups management.
 *
 * Data comes from the live backend via `useUsers()` (`GET /users`). The toolbar
 * buttons and the per-row "Sửa" action are wired to the real create/update/
 * import endpoints (drawers below); the filter bar filters the loaded rows
 * client-side.
 */
export default function UsersPage() {
  const t = useT();
  const { data: users, isLoading, isError, refetch } = useUsers();
  const rows = useMemo(() => users ?? [], [users]);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<UserRow | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [deptFilter, setDeptFilter] = useState('');

  const deptOptions = useMemo(
    () => Array.from(new Set(rows.map((u) => u.dept).filter(Boolean))).sort(),
    [rows],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((u) => {
      if (q && !`${u.name} ${u.email}`.toLowerCase().includes(q)) return false;
      if (roleFilter && u.role !== roleFilter) return false;
      if (deptFilter && u.dept !== deptFilter) return false;
      return true;
    });
  }, [rows, search, roleFilter, deptFilter]);

  const openAdd = () => {
    setEditing(null);
    setFormOpen(true);
  };
  const openEdit = (u: UserRow) => {
    setEditing(u);
    setFormOpen(true);
  };

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
      cell: (u) => (
        <button
          type="button"
          onClick={() => openEdit(u)}
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
              {t('Người dùng')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {t('{n} người dùng', { n: rows.length })}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <Button variant="outline" onClick={() => setImportOpen(true)}>
              {t('Nhập CSV / SCIM')}
            </Button>
            <Button variant="primary" onClick={openAdd}>
              {t('+ Thêm người dùng')}
            </Button>
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
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                style={{ paddingLeft: 32, width: '100%' }}
              />
            </div>
            <Select
              aria-label={t('Lọc theo vai trò')}
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="">{t('Vai trò')}</option>
              {ROLE_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </Select>
            <Select
              aria-label={t('Lọc theo phòng ban')}
              value={deptFilter}
              onChange={(e) => setDeptFilter(e.target.value)}
            >
              <option value="">{t('Phòng ban')}</option>
              {deptOptions.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
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
          {!isLoading && !isError && rows.length > 0 && filtered.length === 0 && (
            <TableMessage>{t('Không có kết quả khớp bộ lọc.')}</TableMessage>
          )}
          {!isLoading && !isError && filtered.length > 0 && (
            <DataTable<UserRow> columns={columns} data={filtered} rowKey={(u) => u.id} />
          )}
        </div>
      </div>

      <UserFormDrawer open={formOpen} onClose={() => setFormOpen(false)} user={editing} />
      <ImportDrawer open={importOpen} onClose={() => setImportOpen(false)} />
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
