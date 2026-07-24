import { useState } from 'react';
import { Button, DataTable, StatusPill, useToast } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useGroups, useEvaluateGroup, type GroupDto } from './api';
import { GroupFormDrawer } from './GroupFormDrawer';

/** Short human summary of a smart group's rule_json. */
function ruleSummary(rule: Record<string, unknown> | null): string {
  if (!rule || Object.keys(rule).length === 0) return '—';
  return Object.entries(rule)
    .map(([k, v]) => `${k}: ${String(v)}`)
    .join(', ');
}

/**
 * GroupsPage — manage tenant groups (static + smart). Data from `useGroups()`
 * (`GET /groups`); create via a drawer (`POST /groups`); smart groups can be
 * re-evaluated in place (`POST /groups/{id}/evaluate`).
 */
export default function GroupsPage() {
  const t = useT();
  const toast = useToast();
  const { data: groups, isLoading, isError, refetch } = useGroups();
  const evaluate = useEvaluateGroup();
  const [formOpen, setFormOpen] = useState(false);
  const [evaluatingId, setEvaluatingId] = useState<string | null>(null);

  const rows = groups ?? [];

  const runEvaluate = (g: GroupDto) => {
    setEvaluatingId(g.id);
    evaluate.mutate(g.id, {
      onSuccess: (r) => {
        toast({ msg: t('Đã đánh giá lại: {n} thành viên', { n: r.member_count }), variant: 'success' });
      },
      onError: () => toast({ msg: t('Đánh giá lại thất bại, thử lại.'), variant: 'error' }),
      onSettled: () => setEvaluatingId(null),
    });
  };

  const columns: ColumnDef<GroupDto>[] = [
    {
      id: 'name',
      header: t('Nhóm'),
      cell: (g) => (
        <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--color-text)' }}>
          {g.name ?? '—'}
        </div>
      ),
    },
    {
      id: 'type',
      header: t('Loại'),
      cell: (g) =>
        g.rule_json ? (
          <StatusPill variant="info" dot>
            {t('Thông minh')}
          </StatusPill>
        ) : (
          <StatusPill variant="neutral" dot={false}>
            {t('Tĩnh')}
          </StatusPill>
        ),
      width: '120px',
    },
    {
      id: 'rule',
      header: t('Điều kiện'),
      cell: (g) => (
        <span style={{ fontSize: 12, color: 'var(--color-muted)' }}>{ruleSummary(g.rule_json)}</span>
      ),
    },
    {
      id: 'members',
      header: t('Thành viên'),
      cell: (g) => <span style={{ color: 'var(--color-muted)' }}>{g.member_count ?? 0}</span>,
      width: '100px',
    },
    {
      id: 'action',
      header: '',
      cell: (g) =>
        g.rule_json ? (
          <button
            type="button"
            onClick={() => runEvaluate(g)}
            disabled={evaluate.isPending && evaluatingId === g.id}
            style={{
              fontSize: 12,
              color: 'var(--color-blue)',
              cursor: 'pointer',
              background: 'none',
              border: 'none',
            }}
          >
            {evaluate.isPending && evaluatingId === g.id ? t('Đang…') : t('Đánh giá lại')}
          </button>
        ) : null,
      width: '110px',
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
              {t('Nhóm')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {t('{n} nhóm', { n: rows.length })}
            </div>
          </div>
          <Button variant="primary" onClick={() => setFormOpen(true)}>
            {t('+ Tạo nhóm')}
          </Button>
        </div>

        <div
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          {isLoading && <TableMessage>{t('Đang tải nhóm…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
                {t('Không tải được nhóm.')}{' '}
              </span>
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && rows.length === 0 && (
            <TableMessage>{t('Chưa có nhóm nào.')}</TableMessage>
          )}
          {!isLoading && !isError && rows.length > 0 && (
            <DataTable<GroupDto> columns={columns} data={rows} rowKey={(g) => g.id} />
          )}
        </div>
      </div>

      <GroupFormDrawer open={formOpen} onClose={() => setFormOpen(false)} />
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
