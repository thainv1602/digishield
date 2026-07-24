import { useEffect, useState } from 'react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCreateBadge } from './api';

const ICONS = ['shield', 'target', 'zap', 'award'];

/** Create a badge definition in the tenant's catalog (POST /gamification/badges). */
export function BadgeFormDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useT();
  const toast = useToast();
  const create = useCreateBadge();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [iconRef, setIconRef] = useState('shield');

  useEffect(() => {
    if (!open) return;
    setName('');
    setDescription('');
    setIconRef('shield');
  }, [open]);

  const submit = () => {
    if (!name.trim()) {
      toast({ msg: t('Vui lòng nhập tên huy hiệu.'), variant: 'warning' });
      return;
    }
    create.mutate(
      { name: name.trim(), description: description.trim() || null, iconRef },
      {
        onSuccess: () => {
          toast({ msg: t('Đã tạo huy hiệu.'), variant: 'success' });
          onClose();
        },
        onError: () => toast({ msg: t('Tạo huy hiệu thất bại, thử lại.'), variant: 'error' }),
      },
    );
  };

  return (
    <Drawer open={open} onClose={onClose} title={t('Tạo huy hiệu')}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <Input
          label={t('Tên huy hiệu')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('VD: Thợ săn phishing')}
        />
        <Input
          label={t('Mô tả')}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('Điều kiện đạt huy hiệu')}
        />
        <Select label={t('Biểu tượng')} value={iconRef} onChange={(e) => setIconRef(e.target.value)}>
          {ICONS.map((ic) => (
            <option key={ic} value={ic}>
              {ic}
            </option>
          ))}
        </Select>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={create.isPending}>
            {create.isPending ? t('Đang tạo…') : t('Tạo')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
