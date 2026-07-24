import { useEffect, useState } from 'react';
import { Button, Input, Select, StatusPill, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useAuth } from '@/app/auth/useAuth';
import { useTenant, useUpdateTenant } from '@/features/super/api';
import {
  useFeatureFlags,
  useTenantSettings,
  useBusinessThresholds,
  useUpdateThresholds,
  type BusinessThresholds,
} from './api';

const REGION_OPTIONS = ['in-country', 'on-prem', 'cloud'];

/**
 * OrgSettingsPage — tenant configuration, data region, business thresholds.
 * Pixel-matched to the design handoff "ORG SETTINGS" screen.
 *
 * Tenant SCIM/SSO settings and feature flags come from the live backend via
 * `useTenantSettings()` (`GET /tenants/{tenantId}/settings`) and
 * `useFeatureFlags()` (`GET /tenants/{tenantId}/feature-flags`) for the demo
 * tenant. The business-threshold sliders load from `useBusinessThresholds()`
 * (`GET /tenants/{tenantId}/thresholds`) and save via `useUpdateThresholds()`.
 */

type ThresholdKey = keyof BusinessThresholds;

const THRESHOLD_META: {
  key: ThresholdKey;
  title: string;
  desc: string;
  min: number;
  max: number;
  color: string;
  format: (v: number) => string;
}[] = [
  {
    key: 'risk_alert_score',
    title: 'Ngưỡng Risk Score cảnh báo',
    desc: 'Khi Risk Score vượt ngưỡng → gửi cảnh báo cho Admin',
    min: 0,
    max: 100,
    color: 'var(--color-amber)',
    format: (v) => String(v),
  },
  {
    key: 'pass_score_pct',
    title: 'Điểm đạt bài kiểm tra (%)',
    desc: 'Điểm tối thiểu để cấp chứng chỉ',
    min: 50,
    max: 100,
    color: 'var(--color-blue)',
    format: (v) => `${v}%`,
  },
  {
    key: 'min_campaigns_per_quarter',
    title: 'Tần suất chiến dịch tối thiểu',
    desc: 'Số chiến dịch mô phỏng tối thiểu mỗi quý',
    min: 1,
    max: 12,
    color: 'var(--color-text)',
    format: (v) => `${v}/Q`,
  },
];

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 24,
};

export default function OrgSettingsPage() {
  const t = useT();
  const toast = useToast();
  const settings = useTenantSettings();
  const flags = useFeatureFlags();
  const thresholdsQuery = useBusinessThresholds();
  const updateThresholds = useUpdateThresholds();

  // Org info (name + data region) — backed by GET/PATCH /tenants/{id}.
  const { user } = useAuth();
  const tenantId = user?.tenantId ?? null;
  const tenantQuery = useTenant(tenantId);
  const updateTenant = useUpdateTenant();
  const [orgName, setOrgName] = useState('');
  const [region, setRegion] = useState('in-country');
  useEffect(() => {
    const tn = tenantQuery.data;
    if (!tn) return;
    setOrgName(tn.name ?? '');
    setRegion(tn.dataRegion && REGION_OPTIONS.includes(tn.dataRegion) ? tn.dataRegion : 'in-country');
  }, [tenantQuery.data]);

  function saveOrgInfo() {
    if (!tenantId) return;
    if (!orgName.trim()) {
      toast.push({ msg: t('Vui lòng nhập tên tổ chức.'), variant: 'warning' });
      return;
    }
    updateTenant.mutate(
      { id: tenantId, body: { name: orgName.trim(), dataRegion: region } },
      {
        onSuccess: () => toast.push({ msg: t('Đã lưu thông tin tổ chức'), variant: 'success' }),
        onError: () => toast.push({ msg: t('Không lưu được, thử lại'), variant: 'error' }),
      },
    );
  }

  // Editable copy of the thresholds, seeded once the query resolves.
  const [form, setForm] = useState<BusinessThresholds | null>(null);
  useEffect(() => {
    if (thresholdsQuery.data) setForm(thresholdsQuery.data);
  }, [thresholdsQuery.data]);

  function setThreshold(key: ThresholdKey, value: number) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  function saveThresholds() {
    if (!form) return;
    updateThresholds.mutate(form, {
      onSuccess: () => toast.push({ msg: t('Đã lưu ngưỡng nghiệp vụ'), variant: 'success' }),
      onError: () => toast.push({ msg: t('Không lưu được, thử lại'), variant: 'error' }),
    });
  }

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease', maxWidth: 700 }}>
        <div style={{ marginBottom: 24 }}>
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
            {t('Cài đặt tổ chức · Org Settings')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
            {t('Cấu hình tenant, vùng dữ liệu và ngưỡng nghiệp vụ')}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {/* Org info */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Thông tin tổ chức')}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <Field label={t('Tên tổ chức')}>
                <Input
                  value={orgName}
                  onChange={(e) => setOrgName(e.target.value)}
                  aria-label={t('Tên tổ chức')}
                />
              </Field>
              <Field label={t('Tên miền')}>
                <Input defaultValue="abc.gov.vn" disabled aria-label={t('Tên miền')} />
              </Field>
              <Field label={t('Loại tổ chức')}>
                <Select aria-label={t('Loại tổ chức')} defaultValue="gov">
                  <option value="gov">{t('Cơ quan nhà nước (gov)')}</option>
                  <option value="edu">{t('Giáo dục (edu)')}</option>
                  <option value="enterprise">{t('Doanh nghiệp (enterprise)')}</option>
                </Select>
              </Field>
              <Field label={t('Vùng lưu trữ dữ liệu')}>
                <Select
                  aria-label={t('Vùng lưu trữ dữ liệu')}
                  value={region}
                  onChange={(e) => setRegion(e.target.value)}
                >
                  <option value="in-country">{t('In-country (Việt Nam)')}</option>
                  <option value="on-prem">On-premises</option>
                  <option value="cloud">Cloud (Singapore)</option>
                </Select>
              </Field>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}>
              <Button
                variant="primary"
                disabled={!tenantId || updateTenant.isPending}
                onClick={saveOrgInfo}
              >
                {updateTenant.isPending ? t('Đang lưu…') : t('Lưu thông tin')}
              </Button>
            </div>
            <div
              style={{
                background: 'rgba(37,102,235,.06)',
                border: '1px solid rgba(37,102,235,.15)',
                borderRadius: 8,
                padding: '12px 16px',
                fontSize: 13,
                color: 'var(--pill-info-fg)',
              }}
            >
              {t('Dữ liệu được lưu trữ tại datacenter trong lãnh thổ Việt Nam, tuân thủ Nghị định 13/2023/NĐ-CP.')}
            </div>
          </div>

          {/* Identity provider / SCIM connection (live: GET /tenants/{id}/settings) */}
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
                {t('Định danh & Đồng bộ · Identity Provider')}
              </div>
              {settings.data && (
                <StatusPill variant={settings.data.connected ? 'safe' : 'neutral'} dot>
                  {settings.data.connected ? t('Đã kết nối') : t('Chưa kết nối')}
                </StatusPill>
              )}
            </div>
            {settings.isLoading && <InlineMessage>{t('Đang tải cấu hình tenant…')}</InlineMessage>}
            {!settings.isLoading && settings.isError && (
              <InlineMessage>
                <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được cấu hình tenant. ')}</span>
                <button type="button" onClick={() => settings.refetch()} style={inlineRetry}>
                  {t('Thử lại')}
                </button>
              </InlineMessage>
            )}
            {!settings.isLoading && !settings.isError && settings.data && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <ReadField label="Identity Provider" value={settings.data.idpName} />
                <ReadField label="SCIM Endpoint" value={settings.data.scimEndpoint} mono />
                <ReadField
                  label={t('Người dùng đã đồng bộ')}
                  value={settings.data.syncedUserCount != null ? settings.data.syncedUserCount.toLocaleString('vi-VN') : null}
                />
                <ReadField label={t('Trạng thái đồng bộ')} value={settings.data.syncStatus} />
              </div>
            )}
          </div>

          {/* Feature flags (live: GET /tenants/{id}/feature-flags) */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Tính năng · Feature Flags')}
            </div>
            {flags.isLoading && <InlineMessage>{t('Đang tải feature flags…')}</InlineMessage>}
            {!flags.isLoading && flags.isError && (
              <InlineMessage>
                <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được feature flags. ')}</span>
                <button type="button" onClick={() => flags.refetch()} style={inlineRetry}>
                  {t('Thử lại')}
                </button>
              </InlineMessage>
            )}
            {!flags.isLoading && !flags.isError && (flags.data?.length ?? 0) === 0 && (
              <InlineMessage>{t('Chưa có feature flag nào.')}</InlineMessage>
            )}
            {!flags.isLoading && !flags.isError && (flags.data?.length ?? 0) > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(flags.data ?? []).map((f) => (
                  <div
                    key={f.key}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: 10,
                      background: 'var(--color-bg)',
                      borderRadius: 8,
                    }}
                  >
                    <span
                      style={{
                        fontSize: 13,
                        fontFamily: "'JetBrains Mono', monospace",
                        color: 'var(--color-text)',
                      }}
                    >
                      {f.key}
                    </span>
                    <StatusPill variant={f.enabled ? 'safe' : 'neutral'} dot>
                      {f.enabled ? t('Bật') : t('Tắt')}
                    </StatusPill>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Business parameters (live: GET/PATCH /tenants/{id}/thresholds) */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Ngưỡng nghiệp vụ · Business Parameters')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {!form && (
                <div style={{ fontSize: 12.5, color: 'var(--color-muted)' }}>
                  {thresholdsQuery.isError ? t('Không tải được ngưỡng.') : t('Đang tải ngưỡng…')}
                </div>
              )}
              {form &&
                THRESHOLD_META.map((m) => {
                  const value = form[m.key];
                  return (
                <div
                  key={m.key}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}
                >
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--color-text)' }}>{t(m.title)}</div>
                    <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{t(m.desc)}</div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <input
                      type="range"
                      min={m.min}
                      max={m.max}
                      value={value}
                      onChange={(e) => setThreshold(m.key, Number(e.target.value))}
                      style={{ width: 100 }}
                      aria-label={t(m.title)}
                    />
                    <span
                      style={{
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: 14,
                        fontWeight: 600,
                        color: m.color,
                        width: 36,
                      }}
                    >
                      {m.format(value)}
                    </span>
                  </div>
                </div>
                  );
                })}
            </div>
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <Button
              variant="outline"
              onClick={() => thresholdsQuery.data && setForm(thresholdsQuery.data)}
            >
              {t('Hủy')}
            </Button>
            <Button
              variant="primary"
              disabled={!form || updateThresholds.isPending}
              onClick={saveThresholds}
            >
              {updateThresholds.isPending ? t('Đang lưu…') : t('Lưu cài đặt')}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label
        style={{
          display: 'block',
          fontSize: 12.5,
          fontWeight: 500,
          color: 'var(--color-muted)',
          marginBottom: 5,
        }}
      >
        {label}
      </label>
      {children}
    </div>
  );
}

function ReadField({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 12.5, fontWeight: 500, color: 'var(--color-muted)', marginBottom: 5 }}>
        {label}
      </label>
      <div
        style={{
          background: 'var(--color-bg)',
          border: '1px solid var(--color-border)',
          borderRadius: 8,
          padding: '9px 13px',
          fontSize: mono ? 12 : 13,
          fontFamily: mono ? "'JetBrains Mono', monospace" : undefined,
          color: 'var(--color-text)',
          wordBreak: 'break-all',
        }}
      >
        {value ?? '—'}
      </div>
    </div>
  );
}

function InlineMessage({ children }: { children: React.ReactNode }) {
  return <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{children}</div>;
}

const inlineRetry: React.CSSProperties = {
  background: 'none',
  border: 'none',
  color: 'var(--color-blue)',
  fontWeight: 600,
  fontSize: 13,
  cursor: 'pointer',
  padding: 0,
};
