import { useParams } from 'react-router-dom';
import { Button, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCertificate } from '../learning/api';

/**
 * CertificatePage — completion certificate (`/certificates/:id`).
 *
 * Bordered card (2px blue border + top gradient stripe), shield icon + course
 * title + recipient + 3 metadata tiles + QR code SVG + serial number
 * (JetBrains Mono) + Download PDF / Share buttons.
 *
 * Data comes from the live backend via `useCertificate(id)`
 * (`GET /certificates/{id}`). Loading/error states handled below.
 */

/** Render an ISO timestamp as dd/MM/yyyy (BE emits `Instant` ISO strings). */
function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()}`;
}

/** Strip the scheme from a verification URL for compact display. */
function displayUrl(url: string | null | undefined): string {
  if (!url) return '';
  return url.replace(/^https?:\/\//, '');
}

export default function CertificatePage() {
  const { id } = useParams<{ id: string }>();
  const { data: cert, isLoading, isError, refetch } = useCertificate(id);
  const toast = useToast();
  const t = useT();

  if (isLoading) {
    return <StatusBlock>{t('Đang tải chứng chỉ…')}</StatusBlock>;
  }
  if (isError || !cert) {
    return (
      <StatusBlock>
        <span style={{ color: 'var(--red)', fontWeight: 600 }}>{t('Không tải được chứng chỉ. ')}</span>
        <button
          type="button"
          onClick={() => refetch()}
          style={{ all: 'unset', color: 'var(--blue)', cursor: 'pointer', fontWeight: 600 }}
        >
          {t('Thử lại')}
        </button>
      </StatusBlock>
    );
  }

  const issuedAt = formatDate(cert.issuedAt);
  const validUntil = formatDate(cert.validUntil);
  const scoreLabel = cert.score != null ? `${cert.score}/100` : '—';
  const serial = cert.serial ?? '—';
  const verifyUrl = displayUrl(cert.verifyUrl);
  const courseTitle = cert.courseTitle ?? t('Chứng chỉ');
  const recipient = cert.recipient ?? '—';

  // No dedicated PDF/share endpoint: print to PDF via the browser, and share the
  // verification link by copying it to the clipboard.
  function onDownload() {
    window.print();
  }

  async function onShare() {
    const text = cert?.verifyUrl ?? `${courseTitle} — ${recipient} — ${t('Mã xác minh: {serial}', { serial })}`;
    try {
      await navigator.clipboard.writeText(text);
      toast(t('Đã sao chép liên kết xác minh'));
    } catch {
      toast(t('Không sao chép được, vui lòng thử lại'));
    }
  }

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 680, margin: '0 auto' }}>
        <header style={{ marginBottom: 24 }}>
          <h1
            style={{
              fontFamily: "'Space Grotesk', system-ui",
              fontSize: 22,
              fontWeight: 700,
              color: 'var(--text)',
              letterSpacing: '-0.02em',
              margin: '0 0 4px',
            }}
          >
            {t('Chứng chỉ của tôi · My Certificates')}
          </h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>
            {t('Tải PDF hoặc chia sẻ nội bộ để xác minh')}
          </p>
        </header>

        {/* Certificate card */}
        <article
          aria-label={t('Chứng chỉ {id}', { id: id ?? '' })}
          style={{
            background: 'var(--surface)',
            border: '2px solid var(--blue)',
            borderRadius: 16,
            padding: 40,
            marginBottom: 16,
            textAlign: 'center',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Top gradient stripe */}
          <div
            aria-hidden="true"
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: 6,
              background: 'linear-gradient(90deg, #2566EB, #4D86F7, #2566EB)',
            }}
          />

          {/* Shield icon */}
          <div
            aria-hidden="true"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'rgba(37,102,235,.1)',
              border: '2px solid rgba(37,102,235,.3)',
              marginBottom: 16,
            }}
          >
            <svg width="28" height="28" viewBox="0 0 36 36" role="img" aria-hidden="true">
              <path
                d="M18 2L4 8v10c0 9.4 5.8 17.2 14 20.2C26.2 35.2 32 27.4 32 18V8z"
                fill="var(--blue)"
              />
              <path
                d="M12 18l4 4 8-8"
                fill="none"
                stroke="#fff"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>

          <div
            style={{
              fontSize: 11,
              fontWeight: 700,
              color: 'var(--muted)',
              letterSpacing: '.1em',
              textTransform: 'uppercase',
              marginBottom: 8,
            }}
          >
            {t('Chứng chỉ hoàn thành · Certificate of Completion')}
          </div>
          <div
            style={{
              fontFamily: "'Space Grotesk', system-ui",
              fontSize: 24,
              fontWeight: 700,
              color: 'var(--text)',
              letterSpacing: '-0.01em',
              marginBottom: 8,
            }}
          >
            {courseTitle}
          </div>
          <div style={{ fontSize: 14, color: 'var(--muted)', marginBottom: 20 }}>
            {t('Cấp cho:')} <strong style={{ color: 'var(--text)' }}>{recipient}</strong>
          </div>

          {/* 3 metadata tiles */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 24,
              marginBottom: 24,
            }}
          >
            <MetaTile label={t('Ngày cấp')} value={issuedAt} />
            <div style={{ width: 1, height: 36, background: 'var(--border)' }} />
            <MetaTile label={t('Điểm đạt')} value={scoreLabel} valueColor="var(--teal)" />
            <div style={{ width: 1, height: 36, background: 'var(--border)' }} />
            <MetaTile label={t('Hiệu lực')} value={validUntil} />
          </div>

          {/* QR + serial */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 20,
            }}
          >
            <div
              style={{
                width: 72,
                height: 72,
                background: 'var(--bg)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <svg width="52" height="52" viewBox="0 0 52 52" role="img" aria-label={t('Mã QR xác minh')}>
                <rect x="4" y="4" width="16" height="16" fill="none" stroke="var(--text)" strokeWidth="3" />
                <rect x="8" y="8" width="8" height="8" fill="var(--text)" />
                <rect x="32" y="4" width="16" height="16" fill="none" stroke="var(--text)" strokeWidth="3" />
                <rect x="36" y="8" width="8" height="8" fill="var(--text)" />
                <rect x="4" y="32" width="16" height="16" fill="none" stroke="var(--text)" strokeWidth="3" />
                <rect x="8" y="36" width="8" height="8" fill="var(--text)" />
                <rect x="32" y="32" width="4" height="4" fill="var(--text)" />
                <rect x="40" y="32" width="4" height="4" fill="var(--text)" />
                <rect x="32" y="40" width="4" height="4" fill="var(--text)" />
                <rect x="40" y="40" width="8" height="8" fill="var(--text)" />
              </svg>
            </div>
            <div style={{ textAlign: 'left' }}>
              <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4 }}>{t('Mã xác minh')}</div>
              <div
                style={{
                  fontFamily: "'JetBrains Mono', monospace",
                  fontSize: 13,
                  fontWeight: 600,
                  color: 'var(--text)',
                }}
              >
                {serial}
              </div>
              {verifyUrl && (
                <div style={{ fontSize: 11.5, color: 'var(--blue)', marginTop: 4 }}>
                  {t('Xác minh tại {verifyUrl} →', { verifyUrl })}
                </div>
              )}
            </div>
          </div>
        </article>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 12 }}>
          <Button type="button" variant="primary" fullWidth onClick={onDownload}>
            {t('Tải PDF')}
          </Button>
          <Button type="button" variant="secondary" fullWidth onClick={onShare}>
            {t('Chia sẻ nội bộ')}
          </Button>
        </div>
    </div>
  );
}

function StatusBlock({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        animation: 'fadeUp .3s ease',
        maxWidth: 680,
        margin: '0 auto',
        padding: '48px 16px',
        textAlign: 'center',
        fontSize: 14,
        color: 'var(--muted)',
      }}
    >
      {children}
    </div>
  );
}

function MetaTile({
  label,
  value,
  valueColor = 'var(--text)',
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 14, fontWeight: 600, color: valueColor }}>{value}</div>
    </div>
  );
}
