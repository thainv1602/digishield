import { useMemo, useState } from 'react';
import { Button, useToast } from '@/shared/ui';
import { Check, Sparkles } from 'lucide-react';
import { useGenerateTemplate, useTemplates, type SimTemplate } from './api';
import { useT } from '@/shared/i18n/I18nProvider';

/**
 * ContentStudioPage — AI-assisted template authoring.
 * Pixel-matched to the design handoff "CONTENT STUDIO" screen.
 *
 * The "Sinh bằng AI" action posts to the live backend via
 * `useGenerateTemplate()` (`POST /ai/templates/generate`) and fills the editor
 * with the returned draft. The template library is loaded from the live backend
 * via `useTemplates()` (`GET /ai/templates`). Save/Submit remain UI-only.
 */

type Filter = 'all' | 'email' | 'sms';

type Template = {
  id: string;
  title: string;
  status: 'Approved' | 'Draft';
  meta: string;
  channel: string;
};

const CHANNEL_LABELS: Record<string, string> = {
  email: 'Email',
  sms: 'SMS',
  zalo: 'Zalo',
  voice: 'Voice',
  qr: 'QR',
  usb: 'USB',
  teams: 'Teams',
  slack: 'Slack',
};

/** Render difficulty as filled/empty dots, matching the design's `●●○`. */
const DIFFICULTY_DOTS: Record<string, string> = {
  easy: '●○○',
  medium: '●●○',
  hard: '●●●',
};

/** Map a backend `SimTemplate` onto the library card view model. */
function toTemplate(dto: SimTemplate): Template {
  const channelLabel = CHANNEL_LABELS[dto.channel] ?? dto.channel;
  const dots = DIFFICULTY_DOTS[dto.difficulty] ?? '●○○';
  return {
    id: dto.id,
    title: dto.subject,
    status: dto.status === 'approved' ? 'Approved' : 'Draft',
    meta: `${channelLabel} · ${dots}`,
    channel: dto.channel,
  };
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};

export default function ContentStudioPage() {
  const t = useT();
  const toast = useToast();
  const generate = useGenerateTemplate();
  const { data, isLoading, isError } = useTemplates();
  const [filter, setFilter] = useState<Filter>('all');
  const [selected, setSelected] = useState('');
  const [title, setTitle] = useState(t('Thông báo hoàn học phí học kỳ II'));
  const [body, setBody] = useState(
    t('Kính gửi sinh viên/học viên,\n\nTheo quy định Bộ GD&ĐT, nhà trường sẽ hoàn học phí HK2 cho các trường hợp đủ điều kiện. Vui lòng xác nhận thông tin ngân hàng tại đây trước 30/06/2026.'),
  );

  const templates = useMemo<Template[]>(() => (data ?? []).map(toTemplate), [data]);
  const visible = templates.filter((t) => filter === 'all' || t.channel === filter);

  const handleGenerate = () => {
    const prompt = `${title}\n\n${body}`.trim();
    generate.mutate(
      { prompt },
      {
        onSuccess: (draft) => {
          if (draft.subject) setTitle(draft.subject);
          if (draft.body) setBody(draft.body);
          toast.push({ msg: t('Đã sinh mẫu bằng AI'), variant: 'success' });
        },
        onError: () => {
          toast.push({ msg: t('Không sinh được mẫu'), variant: 'error' });
        },
      },
    );
  };

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease' }}>
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
            {t('Content Studio · Soạn mẫu')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
            {t('Tạo, duyệt và quản lý mẫu mô phỏng bằng AI')}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 14, alignItems: 'start' }}>
          {/* Template library */}
          <div style={{ ...cardStyle, overflow: 'hidden' }}>
            <div
              style={{
                padding: '12px 16px',
                borderBottom: '1px solid var(--color-border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <span style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-text)' }}>{t('Thư viện mẫu')}</span>
              <Button size="sm" variant="primary">
                {t('+ Mới')}
              </Button>
            </div>

            {/* Filter pills */}
            <div
              style={{
                display: 'flex',
                gap: 6,
                padding: '10px 12px',
                borderBottom: '1px solid var(--color-border)',
              }}
            >
              {(['all', 'email', 'sms'] as Filter[]).map((f) => {
                const active = filter === f;
                return (
                  <button
                    type="button"
                    key={f}
                    onClick={() => setFilter(f)}
                    style={{
                      background: active ? 'rgba(37,102,235,.15)' : 'var(--color-bg)',
                      color: active ? 'var(--color-blue)' : 'var(--color-muted)',
                      borderRadius: 99,
                      padding: '3px 10px',
                      fontSize: 11.5,
                      fontWeight: active ? 600 : 400,
                      cursor: 'pointer',
                      border: 'none',
                    }}
                  >
                    {f === 'all' ? t('Tất cả') : f === 'email' ? 'Email' : 'SMS'}
                  </button>
                );
              })}
            </div>

            <div style={{ padding: 8 }}>
              {(isLoading || isError || visible.length === 0) && (
                <div style={{ padding: '16px 8px', fontSize: 12.5, color: 'var(--color-muted)' }}>
                  {isLoading
                    ? t('Đang tải thư viện…')
                    : isError
                      ? t('Không tải được thư viện.')
                      : t('Chưa có mẫu nào trong thư viện.')}
                </div>
              )}
              {visible.map((t) => {
                const sel = selected === t.id;
                return (
                  <button
                    type="button"
                    key={t.id}
                    onClick={() => setSelected(t.id)}
                    style={{
                      display: 'block',
                      width: '100%',
                      textAlign: 'left',
                      borderRadius: 8,
                      padding: 12,
                      marginBottom: 6,
                      cursor: 'pointer',
                      background: sel ? 'rgba(37,102,235,.06)' : 'var(--color-bg)',
                      border: sel ? '1.5px solid var(--color-blue)' : '1.5px solid transparent',
                    }}
                    aria-pressed={sel}
                  >
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text)', marginBottom: 3 }}>
                      {t.title}
                    </div>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <span
                        style={{
                          background: t.status === 'Approved' ? 'var(--pill-safe-bg)' : 'var(--pill-warning-bg)',
                          color: t.status === 'Approved' ? 'var(--pill-safe-fg)' : 'var(--pill-warning-fg)',
                          borderRadius: 99,
                          padding: '1px 7px',
                          fontSize: 10.5,
                          fontWeight: 600,
                        }}
                      >
                        {t.status}
                      </span>
                      <span style={{ fontSize: 11, color: 'var(--color-muted)' }}>{t.meta}</span>
                    </div>
                  </button>
                );
              })}
              <button
                type="button"
                onClick={handleGenerate}
                disabled={generate.isPending}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  background: 'var(--color-bg)',
                  borderRadius: 8,
                  padding: 12,
                  cursor: generate.isPending ? 'wait' : 'pointer',
                  border: '1.5px dashed var(--color-border)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  opacity: generate.isPending ? 0.6 : 1,
                }}
              >
                <Sparkles size={14} color="var(--color-blue)" />
                <span style={{ fontSize: 13, color: 'var(--color-blue)', fontWeight: 500 }}>
                  {generate.isPending ? t('Đang sinh…') : t('Sinh bằng AI')}
                </span>
              </button>
            </div>
          </div>

          {/* Editor + moderation */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ ...cardStyle, padding: 20 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 16,
                }}
              >
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>{t('Soạn mẫu')}</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => toast.push({ msg: t('Đã lưu nháp') })}
                  >
                    {t('Lưu nháp')}
                  </Button>
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={() => toast.push({ msg: t('Đã gửi duyệt'), variant: 'success' })}
                  >
                    {t('Gửi duyệt')}
                  </Button>
                </div>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={fieldLabel}>{t('Tiêu đề')}</label>
                <input value={title} onChange={(e) => setTitle(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={fieldLabel}>{t('Nội dung email')}</label>
                <textarea
                  rows={5}
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  style={{ ...inputStyle, fontSize: 13.5, resize: 'none', lineHeight: 1.5 }}
                />
              </div>
            </div>

            {/* AI moderation pass banner */}
            <div style={{ ...cardStyle, padding: 20 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 12 }}>
                {t('Kiểm duyệt AI · Moderation')}
              </div>
              <div
                style={{
                  background: 'var(--pill-safe-bg)',
                  border: '1px solid rgba(24,147,92,.2)',
                  borderRadius: 8,
                  padding: '12px 16px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                }}
              >
                <Check size={16} color="var(--color-teal)" strokeWidth={2.5} />
                <span style={{ fontSize: 13, color: 'var(--pill-safe-fg)', fontWeight: 500 }}>
                  {t('PASS — Không phát hiện nội dung vi phạm')}
                </span>
              </div>
              <div style={{ marginTop: 10, fontSize: 12.5, color: 'var(--color-muted)' }}>
                {t('Kiểm tra lần cuối: 27/06/2026 09:31 · Độ tin cậy: 0.97')}
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

const fieldLabel: React.CSSProperties = {
  display: 'block',
  fontSize: 12.5,
  fontWeight: 500,
  color: 'var(--color-muted)',
  marginBottom: 5,
};
const inputStyle: React.CSSProperties = {
  width: '100%',
  background: 'var(--color-bg)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  padding: '9px 13px',
  color: 'var(--color-text)',
  fontSize: 14,
  fontFamily: 'inherit',
  boxSizing: 'border-box',
};
