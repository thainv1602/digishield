import { useEffect, useState } from 'react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCreateGroup, type GroupUpsert } from './api';

/**
 * Create-group drawer. A "smart" group carries a `rule_json` built from the
 * documented rule keys (`risk_score_gte`, `department`); a "static" group is
 * created with `rule_json = null`. There is no update endpoint, so this is
 * create-only.
 */
export function GroupFormDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useT();
  const toast = useToast();
  const create = useCreateGroup();

  const [name, setName] = useState('');
  const [smart, setSmart] = useState(false);
  const [riskGte, setRiskGte] = useState('');
  const [department, setDepartment] = useState('');

  useEffect(() => {
    if (!open) return;
    setName('');
    setSmart(false);
    setRiskGte('');
    setDepartment('');
  }, [open]);

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      toast({ msg: t('Vui lòng nhập tên nhóm.'), variant: 'warning' });
      return;
    }
    let rule: Record<string, unknown> | null = null;
    if (smart) {
      rule = {};
      const r = Number(riskGte);
      if (riskGte.trim() && !Number.isNaN(r)) rule.risk_score_gte = r;
      if (department.trim()) rule.department = department.trim();
    }
    const body: GroupUpsert = { name: trimmed, rule_json: rule };
    create.mutate(body, {
      onSuccess: () => {
        toast({ msg: t('Đã tạo nhóm.'), variant: 'success' });
        onClose();
      },
      onError: () => toast({ msg: t('Tạo nhóm thất bại, thử lại.'), variant: 'error' }),
    });
  };

  return (
    <Drawer open={open} onClose={onClose} title={t('Tạo nhóm')}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <Input
          label={t('Tên nhóm')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('VD: Nhân sự rủi ro cao')}
        />
        <Select
          label={t('Loại nhóm')}
          value={smart ? 'smart' : 'static'}
          onChange={(e) => setSmart(e.target.value === 'smart')}
        >
          <option value="static">{t('Nhóm tĩnh')}</option>
          <option value="smart">{t('Nhóm thông minh (tự động theo điều kiện)')}</option>
        </Select>

        {smart && (
          <>
            <Input
              label={t('Điểm rủi ro tối thiểu')}
              type="number"
              min={0}
              max={100}
              value={riskGte}
              onChange={(e) => setRiskGte(e.target.value)}
              placeholder="70"
              hint={t('Thành viên có điểm rủi ro ≥ giá trị này.')}
            />
            <Input
              label={t('Phòng ban')}
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              placeholder="ke-toan"
              hint={t('Để trống nếu không lọc theo phòng ban.')}
            />
          </>
        )}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={create.isPending}>
            {create.isPending ? t('Đang tạo…') : t('Tạo nhóm')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
