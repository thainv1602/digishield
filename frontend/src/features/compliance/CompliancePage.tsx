import { Button, ProgressBar } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import {
  useComplianceStatus,
  useCompliancePolicies,
  type CompliancePolicy,
} from './api';

/**
 * CompliancePage — mandatory-policy completion tracking.
 * Pixel-matched to the design handoff "COMPLIANCE" screen.
 *
 * Data comes from the live backend via `useComplianceStatus()`
 * (`GET /compliance/status`) and `useCompliancePolicies()`
 * (`GET /compliance/policies`). Loading/error/empty states handled inline.
 */

type PolicyRow = {
  id: string;
  name: string;
  deadline: string;
  pct: number;
  color: string;
  pillBg: string;
  pillColor: string;
  pillSuffix: string;
};

/** Map a backend policy onto the FE row, deriving pill styling from completion. */
function toPolicyRow(p: CompliancePolicy): PolicyRow {
  const ok = p.completionPct >= 80;
  return {
    id: p.id,
    name: p.name ?? '—',
    deadline: p.dueRule ?? '',
    pct: p.completionPct,
    color: ok ? 'var(--color-teal)' : 'var(--color-amber)',
    pillBg: ok ? 'var(--pill-safe-bg)' : 'var(--pill-warning-bg)',
    pillColor: ok ? 'var(--pill-safe-fg)' : 'var(--pill-warning-fg)',
    pillSuffix: ok ? '✓' : '!',
  };
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};
const labelStyle: React.CSSProperties = {
  fontSize: 10.5,
  fontWeight: 600,
  color: 'var(--color-muted)',
  letterSpacing: '.08em',
  textTransform: 'uppercase',
};

export default function CompliancePage() {
  const t = useT();
  const statusQuery = useComplianceStatus();
  const policiesQuery = useCompliancePolicies();

  const status = statusQuery.data;
  const policies = (policiesQuery.data ?? []).map(toPolicyRow);

  const kpis = [
    {
      label: t('Tỉ lệ tuân thủ'),
      value: status ? `${Math.round(status.compliantPct)}%` : '—',
      sub: status
        ? t('{a}/{b} người', { a: status.compliantCount, b: status.totalCount })
        : '',
      color: 'var(--color-teal)',
    },
    {
      label: t('Đã hoàn thành'),
      value: status ? String(status.completedCount) : '—',
      sub: status ? t('/ {n} chính sách', { n: status.policyCount }) : '',
      color: 'var(--color-text)',
    },
    {
      label: t('Sắp đến hạn'),
      value: status ? String(status.dueSoonCount) : '—',
      sub: t('chính sách'),
      color: 'var(--color-amber)',
    },
  ];

  const policiesLoading = policiesQuery.isLoading;
  const policiesError = policiesQuery.isError;

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
              Compliance
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
              {t('Theo dõi việc hoàn thành các chính sách bắt buộc')}
            </div>
          </div>
          <Button variant="outline" onClick={() => window.print()}>
            {t('Xuất báo cáo PDF')}
          </Button>
        </div>

        {/* KPI tiles */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 14,
            marginBottom: 20,
          }}
        >
          {kpis.map((k) => (
            <div key={k.label} style={{ ...cardStyle, padding: 20, textAlign: 'center' }}>
              <div style={{ ...labelStyle, marginBottom: 12 }}>{k.label}</div>
              <div
                style={{
                  fontFamily: "'Space Grotesk', system-ui",
                  fontSize: 42,
                  fontWeight: 700,
                  color: k.color,
                  letterSpacing: '-.02em',
                }}
              >
                {k.value}
              </div>
              <div style={{ fontSize: 12, color: 'var(--color-muted)', marginTop: 4 }}>{k.sub}</div>
            </div>
          ))}
        </div>

        {/* Policy list */}
        <div style={{ ...cardStyle, overflow: 'hidden' }}>
          <div style={{ padding: '14px 20px', borderBottom: '1px solid var(--color-border)' }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>{t('Danh sách chính sách')}</div>
          </div>
          {policiesLoading && <ListMessage>{t('Đang tải chính sách…')}</ListMessage>}
          {!policiesLoading && policiesError && (
            <ListMessage>
              <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
                {t('Không tải được chính sách.')}{' '}
              </span>
              <button type="button" onClick={() => policiesQuery.refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </ListMessage>
          )}
          {!policiesLoading && !policiesError && policies.length === 0 && (
            <ListMessage>{t('Không có chính sách nào.')}</ListMessage>
          )}
          {!policiesLoading && !policiesError && policies.map((p, i) => (
            <div
              key={p.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                padding: '14px 20px',
                borderBottom: i < policies.length - 1 ? '1px solid var(--color-border)' : undefined,
              }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--color-text)', marginBottom: 3 }}>
                  {p.name}
                </div>
                <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{p.deadline}</div>
              </div>
              <div style={{ width: 120 }}>
                <ProgressBar value={p.pct} max={100} color={p.color} />
              </div>
              <span
                style={{
                  background: p.pillBg,
                  color: p.pillColor,
                  borderRadius: 99,
                  padding: '3px 10px',
                  fontSize: 11.5,
                  fontWeight: 600,
                  flexShrink: 0,
                }}
              >
                {p.pct}% {p.pillSuffix}
              </span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

function ListMessage({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        padding: '28px 20px',
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
