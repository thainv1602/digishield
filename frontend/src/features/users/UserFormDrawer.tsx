import { useEffect, useState } from 'react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { ROLES } from '@/app/auth/roles';
import { useCreateUser, useUpdateUser, type UserRow, type UserUpsert } from './api';

const ROLE_OPTIONS = Object.values(ROLES);

/**
 * Create/edit user form in a right-hand drawer. `user === null` is create mode
 * (POST /users); otherwise edit mode (PATCH /users/{id}). The edit path carries
 * the row's departmentId/locale through so a PATCH does not clear them.
 */
export function UserFormDrawer({
  open,
  onClose,
  user,
}: {
  open: boolean;
  onClose: () => void;
  user: UserRow | null;
}) {
  const t = useT();
  const toast = useToast();
  const create = useCreateUser();
  const update = useUpdateUser();
  const editing = user !== null;

  const [email, setEmail] = useState('');
  const [role, setRole] = useState<string>(ROLES.LEARNER);
  const [locale, setLocale] = useState('vi');

  // Reset the form whenever the drawer (re)opens for a different target.
  useEffect(() => {
    if (!open) return;
    setEmail(user?.email ?? '');
    setRole(user?.role || ROLES.LEARNER);
    setLocale(user?.locale ?? 'vi');
  }, [open, user]);

  const pending = create.isPending || update.isPending;

  const submit = () => {
    const trimmed = email.trim();
    if (!trimmed) {
      toast({ msg: t('Vui lòng nhập email.'), variant: 'warning' });
      return;
    }
    const body: UserUpsert = {
      email: trimmed,
      role,
      locale,
      department_id: user?.departmentId ?? null,
    };
    const onDone = {
      onSuccess: () => {
        toast({
          msg: editing ? t('Đã cập nhật người dùng.') : t('Đã thêm người dùng.'),
          variant: 'success',
        });
        onClose();
      },
      onError: () =>
        toast({ msg: t('Thao tác thất bại, thử lại.'), variant: 'error' }),
    };
    if (editing && user) {
      update.mutate({ id: user.id, body }, onDone);
    } else {
      create.mutate(body, onDone);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={editing ? t('Sửa người dùng') : t('Thêm người dùng')}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <Input
          label={t('Email')}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="name@company.com"
          disabled={editing}
          hint={editing ? t('Không đổi được email của người dùng.') : undefined}
        />
        <Select label={t('Vai trò')} value={role} onChange={(e) => setRole(e.target.value)}>
          {ROLE_OPTIONS.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </Select>
        <Select
          label={t('Ngôn ngữ')}
          value={locale}
          onChange={(e) => setLocale(e.target.value)}
        >
          <option value="vi">Tiếng Việt</option>
          <option value="en">English</option>
        </Select>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={pending}>
            {pending ? t('Đang lưu…') : editing ? t('Lưu') : t('Thêm')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
