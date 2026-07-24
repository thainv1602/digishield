import { useMemo, useState } from 'react';
import { Button, Drawer, Select, useToast } from '@/shared/ui';
import { X } from 'lucide-react';
import { useT } from '@/shared/i18n/I18nProvider';
import { useUsers } from '@/features/users/api';
import {
  useGroupMembers,
  useAddGroupMember,
  useRemoveGroupMember,
  type GroupDto,
} from './api';

/**
 * Group members drawer: lists the group's members (resolved against the user
 * list), removes them, and adds a user that is not yet a member. Smart groups
 * can also be re-materialised from the page via "Đánh giá lại".
 */
export function GroupMembersDrawer({
  group,
  onClose,
}: {
  group: GroupDto | null;
  onClose: () => void;
}) {
  const t = useT();
  const toast = useToast();
  const open = group != null;
  const groupId = group?.id ?? null;

  const { data: memberIds, isLoading } = useGroupMembers(groupId);
  const { data: users } = useUsers();
  const add = useAddGroupMember(groupId ?? '');
  const remove = useRemoveGroupMember(groupId ?? '');
  const [toAdd, setToAdd] = useState('');

  const usersById = useMemo(() => new Map((users ?? []).map((u) => [u.id, u])), [users]);
  const memberSet = useMemo(() => new Set(memberIds ?? []), [memberIds]);
  const nonMembers = useMemo(
    () => (users ?? []).filter((u) => !memberSet.has(u.id)),
    [users, memberSet],
  );

  const doAdd = () => {
    if (!toAdd) return;
    add.mutate(toAdd, {
      onSuccess: () => {
        toast({ msg: t('Đã thêm thành viên.'), variant: 'success' });
        setToAdd('');
      },
      onError: () => toast({ msg: t('Thêm thất bại, thử lại.'), variant: 'error' }),
    });
  };

  const doRemove = (userId: string) => {
    remove.mutate(userId, {
      onError: () => toast({ msg: t('Xóa thất bại, thử lại.'), variant: 'error' }),
    });
  };

  return (
    <Drawer open={open} onClose={onClose} title={t('Thành viên · {name}', { name: group?.name ?? '' })}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        {group?.rule_json && Object.keys(group.rule_json).length > 0 && (
          <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>
            {t('Đây là nhóm thông minh — “Đánh giá lại” sẽ tính lại thành viên theo điều kiện và ghi đè thay đổi thủ công.')}
          </div>
        )}

        {/* Add member */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
          <div style={{ flex: 1 }}>
            <Select
              label={t('Thêm thành viên')}
              value={toAdd}
              onChange={(e) => setToAdd(e.target.value)}
            >
              <option value="">{t('Chọn người dùng…')}</option>
              {nonMembers.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} — {u.email}
                </option>
              ))}
            </Select>
          </div>
          <Button variant="primary" onClick={doAdd} disabled={!toAdd || add.isPending}>
            {t('Thêm')}
          </Button>
        </div>

        {/* Member list */}
        <div>
          <div style={{ fontSize: 12, color: 'var(--color-muted)', marginBottom: 8 }}>
            {t('{n} thành viên', { n: memberIds?.length ?? 0 })}
          </div>
          {isLoading && (
            <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{t('Đang tải…')}</div>
          )}
          {!isLoading && (memberIds?.length ?? 0) === 0 && (
            <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
              {t('Chưa có thành viên nào.')}
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(memberIds ?? []).map((id) => {
              const u = usersById.get(id);
              return (
                <div
                  key={id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 10px',
                    border: '1px solid var(--color-border)',
                    borderRadius: 8,
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--color-text)' }}>
                      {u?.name ?? id}
                    </div>
                    {u?.email ? (
                      <div style={{ fontSize: 11.5, color: 'var(--color-muted)' }}>{u.email}</div>
                    ) : null}
                  </div>
                  <button
                    type="button"
                    aria-label={t('Xóa khỏi nhóm')}
                    onClick={() => doRemove(id)}
                    disabled={remove.isPending}
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      color: 'var(--color-muted)',
                      display: 'flex',
                    }}
                  >
                    <X size={15} />
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </Drawer>
  );
}
