import { useMemo, useState } from 'react';
import { AlertTriangle, PenLine } from 'lucide-react';
import { Drawer, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import {
  usePhishingReports,
  useTriageReport,
  useConvertReportToTraining,
  type PhishingReport,
  type TriageDecision,
} from './api';

/**
 * SocInboxPage — SOC analyst triage queue (`/soc/inbox`).
 *
 * Filter tabs (Tất cả / THREAT / SPAM / CLEAN with counts); table grid
 * `32px 1fr 110px 80px 44px` (checkbox / report info / AI label pill /
 * confidence % + mini bar / time-ago). Row click opens a right Drawer (480px).
 * Checkbox toggles bulk-action selection (stopPropagation). Toast on actions.
 *
 * Data comes from the live backend via `usePhishingReports()`
 * (`GET /reports/phishing`). Loading/error/empty states handled below.
 */

type AiLabel = 'threat' | 'spam' | 'clean';

interface Report {
  id: string;
  sender: string;
  subject: string;
  aiLabel: AiLabel; // 'clean' | 'spam' | 'threat'
  aiConfidence: number; // 0..1
  time: string;
  reasoning: string;
  blacklistMatch: boolean;
  channel: string | null;
}

// label pill colors keyed by AI label (display labels are uppercase EN per design)
const LABEL_META: Record<AiLabel, { text: string; bg: string; color: string }> = {
  threat: { text: 'THREAT', bg: '#FBE0DF', color: '#BE2A2F' },
  spam: { text: 'SPAM', bg: '#FCEBCF', color: '#C0720A' },
  clean: { text: 'CLEAN', bg: '#DDF3E6', color: '#0F7A4A' },
};

/** Coerce an unknown AI label string to one of the three known variants. */
function normalizeLabel(value: string | null | undefined): AiLabel {
  return value === 'threat' || value === 'spam' || value === 'clean' ? value : 'clean';
}

/** Map a backend `PhishingReportDto` onto the table/drawer view model. */
function toReport(dto: PhishingReport): Report {
  return {
    id: dto.id,
    sender: dto.reporter ?? dto.sender ?? '—',
    subject: dto.subject ?? '',
    aiLabel: normalizeLabel(dto.aiLabel),
    aiConfidence: typeof dto.aiConfidence === 'number' ? dto.aiConfidence : 0,
    time: dto.ageLabel ?? '',
    reasoning: dto.reasoning ?? '',
    blacklistMatch: Boolean(dto.blacklistMatch),
    channel: dto.channel ?? null,
  };
}

const GRID = '32px 1fr 110px 80px 44px';
type Tab = 'all' | 'threat' | 'spam' | 'clean';

function confColor(label: AiLabel): string {
  if (label === 'threat') return 'var(--red)';
  if (label === 'spam') return 'var(--amber)';
  return 'var(--teal)';
}

/** Bulk-action button labels → the triage decision they map to. */
const BULK_DECISIONS: Record<string, TriageDecision> = {
  'Xác nhận đe dọa': 'confirm_threat',
  'Đánh dấu sạch': 'dismiss',
  'Cách ly': 'quarantine',
};

export default function SocInboxPage() {
  const t = useT();
  const toast = useToast();
  const { data, isLoading, isError, refetch } = usePhishingReports();
  const triage = useTriageReport();
  const convertToTraining = useConvertReportToTraining();

  const reports = useMemo<Report[]>(() => (data ?? []).map(toReport), [data]);

  const [tab, setTab] = useState<Tab>('all');
  const [selected, setSelected] = useState<string[]>([]);
  const [openId, setOpenId] = useState<string | null>(null);

  const counts = useMemo(
    () => ({
      all: reports.length,
      threat: reports.filter((r) => r.aiLabel === 'threat').length,
      spam: reports.filter((r) => r.aiLabel === 'spam').length,
      clean: reports.filter((r) => r.aiLabel === 'clean').length,
    }),
    [reports],
  );

  const visible = useMemo(
    () => (tab === 'all' ? reports : reports.filter((r) => r.aiLabel === tab)),
    [reports, tab],
  );

  const activeReport = openId != null ? reports.find((r) => r.id === openId) ?? null : null;

  function toggleRow(id: string) {
    setSelected((sel) => (sel.includes(id) ? sel.filter((x) => x !== id) : [...sel, id]));
  }

  function bulkAction(label: string) {
    const decision = BULK_DECISIONS[label];
    if (!decision || selected.length === 0) return;
    selected.forEach((id) => triage.mutate({ id, decision }));
    toast(`${t(label)}: ${t('{n} báo cáo', { n: selected.length })}`);
    setSelected([]);
  }

  function drawerAction(label: string) {
    const report = activeReport;
    setOpenId(null);
    if (!report) return;
    const onSuccess = () => toast(t(label));
    const onError = () => toast(t('Thao tác thất bại, thử lại'));
    if (label.startsWith('ThreatFlip')) {
      convertToTraining.mutate(report.id, { onSuccess, onError });
    } else if (label.includes('blacklist')) {
      triage.mutate({ id: report.id, decision: 'confirm_threat', addToBlacklist: true }, { onSuccess, onError });
    } else {
      triage.mutate({ id: report.id, decision: 'confirm_threat' }, { onSuccess, onError });
    }
  }

  const tabs: { id: Tab; label: string; count: number; threatStyle?: boolean }[] = [
    { id: 'all', label: t('Tất cả'), count: counts.all },
    { id: 'threat', label: 'THREAT', count: counts.threat, threatStyle: true },
    { id: 'spam', label: 'SPAM', count: counts.spam },
    { id: 'clean', label: 'CLEAN', count: counts.clean },
  ];

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 20,
          }}
        >
          <div>
            <h1 style={pageTitle}>{t('Hộp xử lý báo cáo · SOC Inbox')}</h1>
            <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
              {t('{n} báo cáo mới · AI triage enabled', { n: counts.all })}
            </p>
          </div>
        </header>

        {/* Bulk action bar */}
        {selected.length > 0 && (
          <div
            role="group"
            aria-label={t('Thao tác hàng loạt')}
            style={{
              background: 'rgba(37,102,235,.1)',
              border: '1px solid rgba(37,102,235,.25)',
              borderRadius: 10,
              padding: '12px 16px',
              marginBottom: 14,
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <span style={{ fontSize: 13.5, fontWeight: 500, color: '#1A4FD0' }}>
              {t('Đã chọn {n} báo cáo', { n: selected.length })}
            </span>
            <div style={{ display: 'flex', gap: 8, marginLeft: 'auto' }}>
              <button type="button" onClick={() => bulkAction('Xác nhận đe dọa')} style={bulkBtn('threat')}>
                {t('Xác nhận đe dọa')}
              </button>
              <button type="button" onClick={() => bulkAction('Đánh dấu sạch')} style={bulkBtn('clean')}>
                {t('Đánh dấu sạch')}
              </button>
              <button type="button" onClick={() => bulkAction('Cách ly')} style={bulkBtn('neutral')}>
                {t('Cách ly')}
              </button>
            </div>
          </div>
        )}

        {/* Table card */}
        <div
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          {/* Filter tabs */}
          <div
            role="tablist"
            aria-label={t('Lọc báo cáo')}
            style={{
              padding: '12px 20px',
              borderBottom: '1px solid var(--border)',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
            }}
          >
            {tabs.map((t) => {
              const active = tab === t.id;
              const threat = t.threatStyle;
              return (
                <button
                  key={t.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(t.id)}
                  style={{
                    fontSize: 12.5,
                    fontWeight: 600,
                    borderRadius: 7,
                    padding: '5px 12px',
                    cursor: 'pointer',
                    color: threat ? 'var(--red)' : active ? 'var(--text)' : 'var(--muted)',
                    background: threat
                      ? 'rgba(221,59,64,.08)'
                      : active
                        ? 'var(--surface)'
                        : 'var(--bg)',
                    border: threat
                      ? '1px solid rgba(221,59,64,.2)'
                      : active
                        ? '1px solid var(--blue)'
                        : '1px solid var(--border)',
                  }}
                >
                  {t.id === 'all' ? t.label : `${t.label} (${t.count})`}
                </button>
              );
            })}
          </div>

          {/* Column headers */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: GRID,
              padding: '10px 16px',
              borderBottom: '1px solid var(--border)',
              background: 'var(--bg)',
            }}
          >
            <div />
            {[t('Báo cáo'), t('Nhãn AI'), t('Tin cậy'), t('Khi')].map((h) => (
              <div key={h} style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--muted)' }}>
                {h}
              </div>
            ))}
          </div>

          {/* States */}
          {isLoading && <TableMessage>{t('Đang tải báo cáo…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              <span style={{ color: 'var(--red)', fontWeight: 600 }}>{t('Không tải được báo cáo. ')}</span>
              <button type="button" onClick={() => refetch()} style={inlineRetry}>
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && visible.length === 0 && (
            <TableMessage>{t('Không có báo cáo nào.')}</TableMessage>
          )}

          {/* Rows */}
          {!isLoading && !isError && visible.map((r) => {
            const meta = LABEL_META[r.aiLabel];
            const isSelected = selected.includes(r.id);
            const cColor = confColor(r.aiLabel);
            return (
              <div
                key={r.id}
                role="button"
                tabIndex={0}
                onClick={() => setOpenId(r.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    setOpenId(r.id);
                  }
                }}
                style={{
                  display: 'grid',
                  gridTemplateColumns: GRID,
                  alignItems: 'center',
                  padding: '12px 16px',
                  borderBottom: '1px solid var(--border)',
                  cursor: 'pointer',
                  background: isSelected ? 'rgba(37,102,235,.04)' : 'transparent',
                }}
              >
                {/* Checkbox */}
                <div
                  role="checkbox"
                  aria-checked={isSelected}
                  aria-label={t('Chọn báo cáo từ {sender}', { sender: r.sender })}
                  tabIndex={0}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleRow(r.id);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      e.stopPropagation();
                      toggleRow(r.id);
                    }
                  }}
                  style={{
                    width: 18,
                    height: 18,
                    borderRadius: 4,
                    border: isSelected ? '1px solid var(--blue)' : '1.5px solid var(--border)',
                    background: isSelected ? 'var(--blue)' : 'var(--surface)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    flexShrink: 0,
                  }}
                >
                  {isSelected && (
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  )}
                </div>

                {/* Report info */}
                <div style={{ minWidth: 0 }}>
                  <div style={ellipsis(13, 500, 'var(--text)')}>{r.sender}</div>
                  <div style={ellipsis(11.5, 400, 'var(--muted)')}>{r.subject}</div>
                </div>

                {/* AI label pill */}
                <div>
                  <span
                    style={{
                      borderRadius: 999,
                      padding: '3px 9px',
                      fontSize: 11,
                      fontWeight: 700,
                      background: meta.bg,
                      color: meta.color,
                    }}
                  >
                    {meta.text}
                  </span>
                </div>

                {/* Confidence */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div
                    style={{ flex: 1, background: 'var(--bg)', borderRadius: 999, height: 5 }}
                    aria-hidden="true"
                  >
                    <div
                      style={{
                        height: 5,
                        borderRadius: 999,
                        width: `${Math.round(r.aiConfidence * 100)}%`,
                        background: cColor,
                      }}
                    />
                  </div>
                  <span
                    style={{
                      fontSize: 12,
                      fontFamily: "'JetBrains Mono', monospace",
                      color: cColor,
                      fontWeight: 700,
                      flexShrink: 0,
                    }}
                  >
                    {Math.round(r.aiConfidence * 100)}%
                  </span>
                </div>

                {/* Time ago */}
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>{r.time}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Report detail drawer */}
      <Drawer
        open={activeReport != null}
        onClose={() => setOpenId(null)}
        title={activeReport?.sender ?? ''}
        width={480}
      >
        {activeReport && (
          <ReportDrawerBody report={activeReport} onAction={drawerAction} onClose={() => setOpenId(null)} />
        )}
      </Drawer>
    </>
  );
}

function ReportDrawerBody({
  report,
  onAction,
  onClose,
}: {
  report: Report;
  onAction: (label: string) => void;
  onClose: () => void;
}) {
  const t = useT();
  const meta = LABEL_META[report.aiLabel];
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: 'var(--muted)',
          letterSpacing: '.08em',
          textTransform: 'uppercase',
          marginBottom: 12,
        }}
      >
        {t('Chi tiết báo cáo · Report Detail')}
      </div>

      {/* Channel the learner reported from + relative age */}
      <div style={{ fontSize: 12.5, color: 'var(--muted)', marginBottom: 12 }}>
        {t('Kênh:')}{' '}
        <span style={{ color: 'var(--text)', fontWeight: 600 }}>
          {report.channel === 'sms' ? t('SMS') : report.channel === 'email' ? t('Email') : t('Không rõ')}
        </span>
        {report.time ? ` · ${report.time}` : ''}
      </div>

      {/* AI judgment */}
      <section
        style={{
          background: 'rgba(221,59,64,.06)',
          border: '1px solid var(--border)',
          borderRadius: 10,
          padding: 16,
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
          <span
            style={{
              background: meta.bg,
              color: meta.color,
              borderRadius: 999,
              padding: '4px 12px',
              fontSize: 12,
              fontWeight: 700,
            }}
          >
            {meta.text}
          </span>
          <span
            style={{
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--red)',
            }}
          >
            {Math.round(report.aiConfidence * 100)}%
          </span>
          <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>{t('tin cậy')}</span>
        </div>
        <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5 }}>
          {t('Lý do AI:')} {report.reasoning || t('Không có giải thích từ AI.')}
        </div>
        {/* Blacklist match indicator (amber) */}
        {report.blacklistMatch && (
          <div
            style={{
              marginTop: 10,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 12.5,
            }}
          >
            <AlertTriangle size={13} color="var(--amber)" aria-hidden="true" />
            <span style={{ color: 'var(--amber)', fontWeight: 500 }}>
              {t('Đối chiếu blacklist: TRÙNG nguồn đe dọa đã biết')}
            </span>
          </div>
        )}
      </section>

      {/* Sanitized email preview (monospace, dark bg) */}
      <section style={{ marginBottom: 20 }}>
        <div
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: 'var(--muted)',
            letterSpacing: '.06em',
            textTransform: 'uppercase',
            marginBottom: 12,
          }}
        >
          {t('Nội dung email (đã làm sạch)')}
        </div>
        <div
          style={{
            background: 'var(--navy)',
            border: '1px solid var(--border)',
            borderRadius: 8,
            padding: 14,
            fontSize: 13,
            lineHeight: 1.6,
            color: '#9AACBD',
          }}
        >
          <div
            style={{
              marginBottom: 10,
              paddingBottom: 10,
              borderBottom: '1px solid rgba(255,255,255,.08)',
            }}
          >
            <div style={{ color: '#69788F' }}>
              From:{' '}
              <code style={{ fontFamily: "'JetBrains Mono', monospace", color: '#F87171', fontSize: 12 }}>
                no-reply@bank-vn.support
              </code>
            </div>
            <div style={{ color: '#69788F' }}>
              Subject: <span style={{ color: '#ECF1F8', fontWeight: 500 }}>{report.subject}</span>
            </div>
            <div style={{ color: '#69788F' }}>
              {t('Ngày:')} <span style={{ color: '#9AACBD' }}>27/06/2026 08:43</span>
            </div>
          </div>
          <p style={{ margin: '0 0 8px' }}>{t('Kính gửi Quý khách,')}</p>
          <p style={{ margin: '0 0 8px' }}>
            {t('Tài khoản của bạn có dấu hiệu bất thường. Để bảo vệ tài sản, vui lòng xác minh thông tin ngay.')}
          </p>
          <p style={{ margin: 0, color: '#F87171', fontWeight: 500 }}>
            {t('[LINK ĐÃ VÔ HIỆU HÓA] — hệ thống đã ngắt kết nối')}
          </p>
        </div>
      </section>

      {/* Actions */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: 'var(--muted)',
            letterSpacing: '.06em',
            textTransform: 'uppercase',
          }}
        >
          {t('Hành động')}
        </div>
        <button
          type="button"
          onClick={() => onAction('Đã xác nhận đe dọa & phát cảnh báo')}
          style={{
            background: 'var(--red)',
            color: '#fff',
            border: 'none',
            borderRadius: 9,
            padding: '12px 16px',
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <AlertTriangle size={15} aria-hidden="true" />
          {t('Xác nhận đe dọa & Phát cảnh báo')}
        </button>
        <button
          type="button"
          onClick={() => onAction('ThreatFlip — đã lật thành bài học')}
          style={{
            background: 'rgba(37,102,235,.15)',
            border: '1px solid rgba(37,102,235,.3)',
            color: '#1A4FD0',
            borderRadius: 9,
            padding: '12px 16px',
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <PenLine size={15} aria-hidden="true" />
          {t('ThreatFlip — Lật thành bài học')}
        </button>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <button
            type="button"
            onClick={() => onAction('Đã thêm vào blacklist')}
            style={{
              background: 'var(--bg)',
              border: '1px solid var(--border)',
              color: 'var(--text)',
              borderRadius: 9,
              padding: 10,
              fontSize: 13.5,
              cursor: 'pointer',
            }}
          >
            {t('Thêm blacklist')}
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'var(--bg)',
              border: '1px solid var(--border)',
              color: 'var(--muted)',
              borderRadius: 9,
              padding: 10,
              fontSize: 13.5,
              cursor: 'pointer',
            }}
          >
            {t('Đóng — sạch')}
          </button>
        </div>
      </div>
    </div>
  );
}

function ellipsis(size: number, weight: number, color: string): React.CSSProperties {
  return {
    fontSize: size,
    fontWeight: weight,
    color,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  };
}

function bulkBtn(kind: 'threat' | 'clean' | 'neutral'): React.CSSProperties {
  const base: React.CSSProperties = {
    borderRadius: 8,
    padding: '7px 14px',
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    border: 'none',
    color: '#fff',
  };
  if (kind === 'threat') return { ...base, background: 'var(--red)' };
  if (kind === 'clean') return { ...base, background: 'var(--teal)' };
  return {
    ...base,
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    color: 'var(--text)',
    fontWeight: 500,
  };
}

function TableMessage({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        padding: '28px 16px',
        textAlign: 'center',
        fontSize: 13.5,
        color: 'var(--muted)',
      }}
    >
      {children}
    </div>
  );
}

const inlineRetry: React.CSSProperties = {
  background: 'none',
  border: 'none',
  color: 'var(--blue)',
  fontWeight: 600,
  fontSize: 13.5,
  cursor: 'pointer',
  padding: 0,
};

const pageTitle: React.CSSProperties = {
  fontFamily: "'Space Grotesk', system-ui",
  fontSize: 22,
  fontWeight: 700,
  color: 'var(--text)',
  letterSpacing: '-0.02em',
  margin: 0,
};
