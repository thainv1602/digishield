import { useEffect, useState } from 'react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCreateGroup, useUpdateGroup, type GroupDto, type GroupUpsert } from './api';

/**
 * Create/edit group drawer. `group === null` is create mode (POST /groups);
 * otherwise edit mode (PATCH /groups/{id}). A "smart" group carries a
 * `rule_json` built from the documented keys (`risk_score_gte`, `department`);
 * a "static" group is created/updated with `rule_json = {}` (cleared).
 */
export function GroupFormDrawer({
  open,
  onClose,
  group,
}: {
  open: boolean;
  onClose: () => void;
  group?: GroupDto | null;
}) {
  const t = useT();
  const toast = useToast();
  const create = useCreateGroup();
  const update = useUpdateGroup();
  const editing = group != null;

  const [name, setName] = useState('');
  const [smart, setSmart] = useState(false);
  const [riskGte, setRiskGte] = useState('');
  const [department, setDepartment] = useState('');

  useEffect(() => {
    if (!open) return;
    const rule = group?.rule_json ?? null;
    setName(group?.name ?? '');
    setSmart(Boolean(rule && Object.keys(rule).length > 0));
    setRiskGte(rule && rule.risk_score_gte != null ? String(rule.risk_score_gte) : '');
    setDepartment(rule && typeof rule.department === 'string' ? rule.department : '');
  }, [open, group]);

  const pending = create.isPending || update.isPending;

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      toast({ msg: t('Vui lòng nhập tên nhóm.'), variant: 'warning' });
      return;
    }
    const rule: Record<string, unknown> = {};
    if (smart) {
      const r = Number(riskGte);
      if (riskGte.trim() && !Number.isNaN(r)) rule.risk_score_gte = r;
      if (department.trim()) rule.department = department.trim();
    }
    // Static groups (and edits that clear the rule) send an empty object so the
    // backend clears rule_json; smart groups send the built rule.
    const body: GroupUpsert = { name: trimmed, rule_json: rule };
    const onDone = {
      onSuccess: () => {
        toast({ msg: editing ? t('Đã cập nhật nhóm.') : t('Đã tạo nhóm.'), variant: 'success' });
        onClose();
      },
      onError: () => toast({ msg: t('Thao tác thất bại, thử lại.'), variant: 'error' }),
    };
    if (editing && group) {
      update.mutate({ id: group.id, body }, onDone);
    } else {
      create.mutate(body, onDone);
    }
  };

  return (
    <Drawer open={open} onClose={onClose} title={editing ? t('Sửa nhóm') : t('Tạo nhóm')}>
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
              placeholder={t('Tên phòng ban (khớp chính xác)')}
              hint={t('Để trống nếu không lọc theo phòng ban.')}
            />
          </>
        )}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={pending}>
            {pending ? t('Đang lưu…') : editing ? t('Lưu') : t('Tạo nhóm')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
