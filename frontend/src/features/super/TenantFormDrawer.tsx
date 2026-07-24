import { useEffect, useState } from 'react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCreateTenant, useUpdateTenant, type Tenant } from './api';

const TIERS = ['POOL', 'BRIDGE', 'SILO'];
const STATUSES = ['provisioning', 'active', 'suspended', 'deactivated'];

/**
 * Create/manage a tenant. `tenant === null` is create mode (POST /tenants, needs
 * a name); otherwise manage mode (PATCH /tenants/{id}) — tier / status / data
 * region only, since the update command does not carry the name.
 */
export function TenantFormDrawer({
  open,
  onClose,
  tenant,
}: {
  open: boolean;
  onClose: () => void;
  tenant: Tenant | null;
}) {
  const t = useT();
  const toast = useToast();
  const create = useCreateTenant();
  const update = useUpdateTenant();
  const editing = tenant != null;

  const [name, setName] = useState('');
  const [tier, setTier] = useState('POOL');
  const [status, setStatus] = useState('active');
  const [region, setRegion] = useState('in-country');

  useEffect(() => {
    if (!open) return;
    setName(tenant?.name ?? '');
    setTier(tenant?.tier ?? 'POOL');
    setStatus(tenant?.status ?? 'active');
    setRegion(tenant?.dataRegion ?? 'in-country');
  }, [open, tenant]);

  const pending = create.isPending || update.isPending;

  const submit = () => {
    if (!name.trim()) {
      toast({ msg: t('Vui lòng nhập tên tổ chức.'), variant: 'warning' });
      return;
    }
    const onDone = {
      onSuccess: () => {
        toast({ msg: editing ? t('Đã cập nhật tổ chức.') : t('Đã tạo tổ chức.'), variant: 'success' });
        onClose();
      },
      onError: () => toast({ msg: t('Thao tác thất bại, thử lại.'), variant: 'error' }),
    };
    if (editing && tenant) {
      update.mutate({ id: tenant.id, body: { name: name.trim(), tier, status, dataRegion: region.trim() } }, onDone);
    } else {
      create.mutate({ name: name.trim(), tier, dataRegion: region.trim() }, onDone);
    }
  };

  return (
    <Drawer open={open} onClose={onClose} title={editing ? t('Quản lý tổ chức') : t('Tạo tổ chức')}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <Input
          label={t('Tên tổ chức')}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <Select label={t('Mức cô lập (tier)')} value={tier} onChange={(e) => setTier(e.target.value)}>
          {TIERS.map((x) => (
            <option key={x} value={x}>
              {x}
            </option>
          ))}
        </Select>
        {editing && (
          <Select label={t('Trạng thái')} value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </Select>
        )}
        <Input
          label={t('Vùng dữ liệu')}
          value={region}
          onChange={(e) => setRegion(e.target.value)}
          placeholder="in-country"
        />

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={pending}>
            {pending ? t('Đang lưu…') : editing ? t('Lưu') : t('Tạo')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
