import { Button, StatusPill, useToast } from '@/shared/ui';
import { Copy } from 'lucide-react';
import { useScimConfig, type ScimConfig } from './api';
import { useT } from '@/shared/i18n/I18nProvider';

/**
 * ScimConfigPage — Super Admin SCIM & SSO identity-provider configuration.
 * Pixel-matched to the design handoff "SCIM & SSO CONFIG" screen.
 *
 * Data comes from the live backend via `useScimConfig()` (`GET /super/scim`).
 * Loading/error states handled below; the attribute-mapping grid stays static
 * (the BE exposes no attribute-mapping endpoint).
 */

const FALLBACK_SCIM_ENDPOINT = 'https://api.digishield.vn/scim/v2';

const attributeMapping = [
  { from: 'Azure: displayName', to: 'full_name' },
  { from: 'Azure: department', to: 'department' },
  { from: 'Azure: jobTitle', to: 'role_hint' },
  { from: 'Azure: mail', to: 'email' },
];

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 20,
};
const readonlyField: React.CSSProperties = {
  background: 'var(--color-bg)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  padding: '9px 13px',
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: 12.5,
  color: 'var(--color-text)',
  wordBreak: 'break-all',
};

/** Renders the BE last-sync summary line ("Đồng bộ lần cuối: …"). */
function syncSummary(cfg: ScimConfig, t: (s: string, vars?: Record<string, string | number>) => string): string {
  if (!cfg.lastSyncAt) return t('Chưa đồng bộ lần nào');
  const when = new Date(cfg.lastSyncAt);
  const formatted = Number.isNaN(when.getTime())
    ? cfg.lastSyncAt
    : `${when.toLocaleDateString('vi-VN')} ${when.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
  const users = (cfg.syncedUserCount ?? 0).toLocaleString('vi-VN');
  const errors = cfg.syncErrorCount ?? 0;
  return t('Đồng bộ lần cuối: {formatted} · {users} user · {errors} lỗi', { formatted, users, errors });
}

export default function ScimConfigPage() {
  const t = useT();
  const toast = useToast();
  const { data, isLoading, isError, refetch } = useScimConfig();

  const endpoint = data?.scimEndpoint ?? FALLBACK_SCIM_ENDPOINT;

  const copyEndpoint = () => {
    void navigator.clipboard?.writeText(endpoint);
    toast.push({ msg: t('Đã sao chép SCIM endpoint'), variant: 'success' });
  };

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease', maxWidth: 760 }}>
        <div style={{ marginBottom: 20 }}>
          <div
            style={{
              fontFamily: "'Space Grotesk', system-ui",
              fontSize: 22,
              fontWeight: 700,
              color: 'var(--color-text)',
              letterSpacing: '-.02em',
              marginBottom: 4,
            }}
          >
            SCIM &amp; SSO Config
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
            {t('Kết nối Identity Provider và đồng bộ người dùng tự động')}
          </div>
        </div>

        {isLoading && <Message>{t('Đang tải cấu hình SCIM…')}</Message>}
        {!isLoading && isError && (
          <Message>
            <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được cấu hình SCIM. ')}</span>
            <button type="button" onClick={() => refetch()} style={inlineRetry}>
              {t('Thử lại')}
            </button>
          </Message>
        )}

        {!isLoading && !isError && data && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Connected IdP card */}
            <div style={cardStyle}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 16,
                }}
              >
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                  {data.idpName ?? 'Identity Provider'}
                </div>
                <StatusPill variant={data.connected ? 'safe' : 'neutral'} dot>
                  {data.connected ? t('Đã kết nối') : t('Chưa kết nối')}
                </StatusPill>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                <div>
                  <label style={fieldLabel}>Tenant ID</label>
                  <div style={readonlyField}>{data.idpTenantId ?? '—'}</div>
                </div>
                <div>
                  <label style={fieldLabel}>Client ID</label>
                  <div style={readonlyField}>{data.clientId ?? '—'}</div>
                </div>
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={fieldLabel}>SCIM Endpoint URL</label>
                <div style={{ display: 'flex', gap: 8 }}>
                  <div style={{ ...readonlyField, flex: 1, fontSize: 12 }}>{endpoint}</div>
                  <Button variant="outline" leftIcon={<Copy size={13} />} onClick={copyEndpoint}>
                    {t('Sao chép')}
                  </Button>
                </div>
              </div>

              {/* Sync status bar */}
              <div
                style={{
                  background: 'rgba(37,102,235,.08)',
                  border: '1px solid rgba(37,102,235,.2)',
                  borderRadius: 8,
                  padding: '12px 16px',
                  fontSize: 13,
                  color: 'var(--color-blue)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <div
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: data.syncStatus === 'error' ? 'var(--color-red)' : 'var(--color-teal)',
                    flexShrink: 0,
                  }}
                  aria-hidden="true"
                />
                {syncSummary(data, t)}
              </div>
            </div>

            {/* Attribute mapping grid (static — no BE endpoint) */}
            <div style={cardStyle}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
                {t('Ánh xạ thuộc tính · Attribute Mapping')}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {attributeMapping.map((m) => (
                  <div
                    key={m.from}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      background: 'var(--color-bg)',
                      borderRadius: 8,
                      padding: '10px 14px',
                    }}
                  >
                    <span style={{ fontSize: 12.5, color: 'var(--color-muted)', flex: 1 }}>{m.from}</span>
                    <span style={{ fontSize: 12, color: 'var(--color-muted)' }}>→</span>
                    <span style={{ fontSize: 12.5, color: 'var(--color-text)', flex: 1 }}>{m.to}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        ...cardStyle,
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

const fieldLabel: React.CSSProperties = {
  display: 'block',
  fontSize: 12,
  color: 'var(--color-muted)',
  fontWeight: 500,
  marginBottom: 5,
};
