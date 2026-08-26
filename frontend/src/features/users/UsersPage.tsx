import { useMemo, useState } from 'react';
import { Button, DataTable, Input, Select, StatusPill, riskToVariant, useToast } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { Search } from 'lucide-react';
import { useT } from '@/shared/i18n/i18nContext';
import { ROLES } from '@/app/auth/roles';
import { useUsers, useDeleteUser, useSetUserSuspension, type UserRow } from './api';
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
  const del = useDeleteUser();
  const suspension = useSetUserSuspension();
  const toast = useToast();

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
  const doSuspension = (u: UserRow) => {
    const suspended = (u.status ?? '').toLowerCase() === 'suspended';
    const label = u.email || u.name;
    // Spelled out because suspending reaches past this table: the person cannot
    // sign in at all afterwards, including into the training that suspended
    // them, so restoring here is the only way back.
    const question = suspended
      ? t('Mở khoá "{name}"? Họ sẽ đăng nhập lại được ngay.', { name: label })
      : t('Khoá "{name}"? Họ sẽ không đăng nhập được nữa, kể cả để làm bài đào tạo — chỉ admin mở lại được.', { name: label });
    if (!window.confirm(question)) {
      return;
    }
    suspension.mutate(
      { id: u.id, suspended: !suspended },
      {
        onSuccess: () =>
          toast({
            msg: suspended ? t('Đã mở khoá người dùng.') : t('Đã khoá người dùng.'),
            variant: 'success',
          }),
        onError: () =>
          toast({
            msg: suspended ? t('Mở khoá thất bại, thử lại.') : t('Khoá thất bại, thử lại.'),
            variant: 'error',
          }),
      },
    );
  };
  const doDelete = (u: UserRow) => {
    // Spelled out because this reaches further than the table: the backend
    // removes their sign-in account as well, so it is not undone by re-adding
    // the address — that creates a new account with a new invitation.
    const label = u.email || u.name;
    if (!window.confirm(t('Xóa "{name}" và tài khoản đăng nhập của họ? Hành động này không thể hoàn tác.', { name: label }))) {
      return;
    }
    del.mutate(u.id, {
      onSuccess: () => toast({ msg: t('Đã xóa người dùng.'), variant: 'success' }),
      onError: () => toast({ msg: t('Xóa người dùng thất bại, thử lại.'), variant: 'error' }),
    });
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
      id: 'status',
      header: t('Trạng thái'),
      cell: (u) => {
        const suspended = (u.status ?? '').toLowerCase() === 'suspended';
        return (
          <span style={{ fontSize: 12, color: suspended ? 'var(--color-red)' : 'var(--color-muted)' }}>
            {suspended ? t('Đã khoá') : t('Hoạt động')}
          </span>
        );
      },
      width: '100px',
    },
    {
      id: 'actions',
      header: '',
      cell: (u) => (
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={() => openEdit(u)}
            style={{ fontSize: 12, color: 'var(--color-blue)', cursor: 'pointer', background: 'none', border: 'none' }}
          >
            {t('Sửa')}
          </button>
          <button
            type="button"
            onClick={() => doSuspension(u)}
            disabled={suspension.isPending}
            style={{
              fontSize: 12,
              color: 'var(--color-muted)',
              cursor: suspension.isPending ? 'default' : 'pointer',
              background: 'none',
              border: 'none',
              opacity: suspension.isPending ? 0.5 : 1,
            }}
          >
            {(u.status ?? '').toLowerCase() === 'suspended' ? t('Mở khoá') : t('Khoá')}
          </button>
          <button
            type="button"
            onClick={() => doDelete(u)}
            disabled={del.isPending}
            style={{
              fontSize: 12,
              color: 'var(--color-red)',
              cursor: del.isPending ? 'default' : 'pointer',
              background: 'none',
              border: 'none',
              opacity: del.isPending ? 0.5 : 1,
            }}
          >
            {t('Xóa')}
          </button>
        </div>
      ),
      width: '170px',
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
