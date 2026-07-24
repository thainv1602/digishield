import { useEffect, useState } from 'react';
import { Button, Drawer, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useImportUsers, type UserUpsert } from './api';

/** Parse pasted CSV lines of `email,role` (role optional → learner). */
function parseCsv(text: string): UserUpsert[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.toLowerCase().startsWith('email'))
    .map((line) => {
      const parts = line.split(/[,;]/).map((c) => c.trim());
      return { email: parts[0] ?? '', role: parts[1] || 'learner' } satisfies UserUpsert;
    })
    .filter((u) => u.email.length > 0);
}

/** Bulk-import users by pasting `email,role` rows (POST /users/import). */
export function ImportDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useT();
  const toast = useToast();
  const importUsers = useImportUsers();
  const [text, setText] = useState('');

  useEffect(() => {
    if (open) setText('');
  }, [open]);

  const rows = parseCsv(text);

  const submit = () => {
    if (rows.length === 0) {
      toast({ msg: t('Chưa có dòng hợp lệ nào (email,role).'), variant: 'warning' });
      return;
    }
    importUsers.mutate(rows, {
      onSuccess: () => {
        toast({ msg: t('Đã nhập {n} người dùng.', { n: rows.length }), variant: 'success' });
        onClose();
      },
      onError: () => toast({ msg: t('Nhập thất bại, thử lại.'), variant: 'error' }),
    });
  };

  return (
    <Drawer open={open} onClose={onClose} title={t('Nhập người dùng (CSV)')}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 20 }}>
        <p style={{ fontSize: 13, color: 'var(--color-muted)', margin: 0 }}>
          {t('Mỗi dòng một người dùng theo định dạng')} <code>email,role</code>.{' '}
          {t('Vai trò để trống sẽ mặc định là learner.')}
        </p>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={10}
          placeholder={'an@company.com,analyst\nbinh@company.com,learner'}
          spellCheck={false}
          style={{
            width: '100%',
            fontFamily: 'monospace',
            fontSize: 13,
            padding: 12,
            borderRadius: 8,
            border: '1px solid var(--color-border)',
            background: 'var(--color-input-bg)',
            color: 'var(--color-text)',
            resize: 'vertical',
          }}
        />
        <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>
          {t('{n} dòng hợp lệ', { n: rows.length })}
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Button variant="outline" onClick={onClose} disabled={importUsers.isPending}>
            {t('Hủy')}
          </Button>
          <Button variant="primary" onClick={submit} disabled={importUsers.isPending}>
            {importUsers.isPending ? t('Đang nhập…') : t('Nhập')}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
