import { useMemo, useState } from 'react';
import DOMPurify from 'dompurify';
import { Button, useToast } from '@/shared/ui';
import { Sparkles, Trash2, Paperclip, Plus, X } from 'lucide-react';
import {
  useTemplates,
  useGenerateTemplate,
  useCreateTemplate,
  useUpdateTemplate,
  useSubmitTemplate,
  useDeleteTemplate,
  type SimTemplate,
  type Attachment,
} from './api';
import { useT } from '@/shared/i18n/I18nProvider';

/**
 * ContentStudioPage — author, AI-generate, edit, submit and delete simulation
 * templates (phishing mail/SMS impersonating tax, government, insurance, banks…).
 *
 * Rich content: HTML or plain-text body, an impersonated brand logo, and
 * simulated attachments, with a live recipient preview (HTML sanitized with
 * DOMPurify before rendering).
 */

type Filter = 'all' | 'email' | 'sms';

const CHANNEL_LABELS: Record<string, string> = {
  email: 'Email',
  sms: 'SMS',
  zalo: 'Zalo',
  voice: 'Voice',
  qr: 'QR',
  usb: 'USB',
  teams: 'Teams',
};

const CHANNEL_OPTIONS = ['email', 'sms', 'zalo', 'voice', 'qr'] as const;
const DIFFICULTY_OPTIONS = ['easy', 'medium', 'hard'] as const;
const DIFFICULTY_DOTS: Record<string, string> = { easy: '●○○', medium: '●●○', hard: '●●●' };

interface Draft {
  id: string | null;
  channel: string;
  category: string;
  difficulty: string;
  subject: string;
  body: string;
  bodyFormat: 'text' | 'html';
  logoUrl: string;
  attachments: Attachment[];
  status: string;
}

const EMPTY_DRAFT: Draft = {
  id: null,
  channel: 'email',
  category: '',
  difficulty: 'medium',
  subject: '',
  body: '',
  bodyFormat: 'text',
  logoUrl: '',
  attachments: [],
  status: 'draft',
};

/** Load a backend template into the editor draft. */
function toDraft(dto: SimTemplate): Draft {
  return {
    id: dto.id,
    channel: dto.channel,
    category: dto.category ?? '',
    difficulty: dto.difficulty,
    subject: dto.subject,
    body: dto.body ?? '',
    bodyFormat: dto.body_format === 'html' ? 'html' : 'text',
    logoUrl: dto.logo_url ?? '',
    attachments: dto.attachments ?? [],
    status: dto.status,
  };
}

/** Sanitize author-supplied HTML for the preview (allow http(s)/data-image/mailto/tel links). */
function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_URI_REGEXP: /^(?:https?:|data:image\/|mailto:|tel:|\/)/i,
    ADD_ATTR: ['target'],
  });
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
};

export default function ContentStudioPage() {
  const t = useT();
  const toast = useToast();
  const { data, isLoading, isError } = useTemplates();
  const generate = useGenerateTemplate();
  const create = useCreateTemplate();
  const update = useUpdateTemplate();
  const submit = useSubmitTemplate();
  const remove = useDeleteTemplate();

  const [filter, setFilter] = useState<Filter>('all');
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);

  const templates = useMemo<SimTemplate[]>(() => data ?? [], [data]);
  const visible = templates.filter((tpl) => filter === 'all' || tpl.channel === filter);
  const busy = generate.isPending || create.isPending || update.isPending || submit.isPending || remove.isPending;
  const isMessaging = draft.channel === 'sms' || draft.channel === 'zalo';

  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

  const setAttachment = (i: number, patch: Partial<Attachment>) =>
    setDraft((d) => ({ ...d, attachments: d.attachments.map((a, j) => (j === i ? { ...a, ...patch } : a)) }));
  const addAttachment = () => setDraft((d) => ({ ...d, attachments: [...d.attachments, { name: '', mime: 'application/pdf' }] }));
  const removeAttachment = (i: number) =>
    setDraft((d) => ({ ...d, attachments: d.attachments.filter((_, j) => j !== i) }));

  /** Persist the current draft (create when new, update when editing). Returns the id. */
  const persistDraft = async (approved: boolean): Promise<string | null> => {
    if (!draft.subject.trim()) {
      toast.push({ msg: t('Vui lòng nhập tiêu đề mẫu'), variant: 'warning' });
      return null;
    }
    const payload = {
      channel: draft.channel,
      subject: draft.subject.trim(),
      body: draft.body,
      body_format: draft.bodyFormat,
      category: draft.category.trim() || null,
      logo_url: draft.logoUrl.trim() || null,
      attachments: draft.attachments.filter((a) => a.name.trim()),
      difficulty: draft.difficulty,
    };
    if (draft.id) {
      await update.mutateAsync({ id: draft.id, body: payload });
      if (approved) await submit.mutateAsync(draft.id);
      return draft.id;
    }
    const created = await create.mutateAsync({ ...payload, approved });
    set({ id: created.id, status: created.status });
    return created.id;
  };

  const handleSaveDraft = async () => {
    const id = await persistDraft(false).catch(() => null);
    if (id) toast.push({ msg: t('Đã lưu nháp'), variant: 'success' });
  };

  const handleSubmit = async () => {
    const id = await persistDraft(true).catch(() => null);
    if (id) {
      set({ status: 'approved' });
      toast.push({ msg: t('Đã gửi duyệt'), variant: 'success' });
    }
  };

  const handleGenerate = () => {
    generate.mutate(
      { channel: draft.channel, industry: draft.category.trim() || null },
      {
        onSuccess: (tpl) => {
          setDraft(toDraft(tpl));
          toast.push({ msg: t('Đã sinh mẫu bằng AI'), variant: 'success' });
        },
        onError: () => toast.push({ msg: t('Không sinh được mẫu'), variant: 'error' }),
      },
    );
  };

  const handleDelete = () => {
    if (!draft.id) {
      setDraft(EMPTY_DRAFT);
      return;
    }
    remove.mutate(draft.id, {
      onSuccess: () => {
        setDraft(EMPTY_DRAFT);
        toast.push({ msg: t('Đã xóa mẫu'), variant: 'success' });
      },
      onError: () => toast.push({ msg: t('Không xóa được mẫu'), variant: 'error' }),
    });
  };

  return (
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
          {t('Tạo, duyệt và quản lý mẫu mô phỏng (thuế, cơ quan nhà nước, bảo hiểm, ngân hàng…)')}
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
            <Button size="sm" variant="primary" onClick={() => setDraft(EMPTY_DRAFT)}>
              {t('+ Mới')}
            </Button>
          </div>

          <div style={{ display: 'flex', gap: 6, padding: '10px 12px', borderBottom: '1px solid var(--color-border)' }}>
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
            {visible.map((tpl) => {
              const sel = draft.id === tpl.id;
              const channelLabel = CHANNEL_LABELS[tpl.channel] ?? tpl.channel;
              const dots = DIFFICULTY_DOTS[tpl.difficulty] ?? '●○○';
              const approved = tpl.status === 'approved';
              return (
                <button
                  type="button"
                  key={tpl.id}
                  onClick={() => setDraft(toDraft(tpl))}
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
                    {tpl.subject}
                  </div>
                  <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                    <span
                      style={{
                        background: approved ? 'var(--pill-safe-bg)' : 'var(--pill-warning-bg)',
                        color: approved ? 'var(--pill-safe-fg)' : 'var(--pill-warning-fg)',
                        borderRadius: 99,
                        padding: '1px 7px',
                        fontSize: 10.5,
                        fontWeight: 600,
                      }}
                    >
                      {approved ? t('Đã duyệt') : t('Nháp')}
                    </span>
                    {tpl.category && (
                      <span style={{ fontSize: 10.5, color: 'var(--color-blue)', fontWeight: 500 }}>{tpl.category}</span>
                    )}
                    {tpl.attachments?.length > 0 && <Paperclip size={11} color="var(--color-muted)" />}
                    <span style={{ fontSize: 11, color: 'var(--color-muted)' }}>{`${channelLabel} · ${dots}`}</span>
                  </div>
                </button>
              );
            })}
            <button
              type="button"
              onClick={handleGenerate}
              disabled={busy}
              style={{
                width: '100%',
                textAlign: 'left',
                background: 'var(--color-bg)',
                borderRadius: 8,
                padding: 12,
                cursor: busy ? 'wait' : 'pointer',
                border: '1.5px dashed var(--color-border)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                opacity: busy ? 0.6 : 1,
              }}
            >
              <Sparkles size={14} color="var(--color-blue)" />
              <span style={{ fontSize: 13, color: 'var(--color-blue)', fontWeight: 500 }}>
                {generate.isPending ? t('Đang sinh…') : t('Sinh bằng AI')}
              </span>
            </button>
          </div>
        </div>

        {/* Editor + preview */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {/* Editor */}
          <div style={{ ...cardStyle, padding: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                {draft.id ? t('Sửa mẫu') : t('Soạn mẫu mới')}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {draft.id && (
                  <Button size="sm" variant="ghost" onClick={handleDelete} disabled={busy}>
                    <Trash2 size={13} style={{ marginRight: 4 }} />
                    {t('Xóa')}
                  </Button>
                )}
                <Button size="sm" variant="outline" onClick={handleSaveDraft} disabled={busy}>
                  {t('Lưu nháp')}
                </Button>
                <Button size="sm" variant="primary" onClick={handleSubmit} disabled={busy}>
                  {t('Gửi duyệt')}
                </Button>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr 1fr', gap: 12, marginBottom: 12 }}>
              <div>
                <label style={fieldLabel}>{t('Kênh')}</label>
                <select value={draft.channel} onChange={(e) => set({ channel: e.target.value })} style={inputStyle}>
                  {CHANNEL_OPTIONS.map((c) => (
                    <option key={c} value={c}>
                      {CHANNEL_LABELS[c]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label style={fieldLabel}>{t('Chủ đề (giả mạo)')}</label>
                <input
                  value={draft.category}
                  onChange={(e) => set({ category: e.target.value })}
                  placeholder={t('VD: Cơ quan thuế, Bảo hiểm xã hội…')}
                  style={inputStyle}
                />
              </div>
              <div>
                <label style={fieldLabel}>{t('Độ khó')}</label>
                <select value={draft.difficulty} onChange={(e) => set({ difficulty: e.target.value })} style={inputStyle}>
                  {DIFFICULTY_OPTIONS.map((d) => (
                    <option key={d} value={d}>
                      {d === 'easy' ? t('Dễ') : d === 'medium' ? t('Trung bình') : t('Khó')}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Logo + body format */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, marginBottom: 12, alignItems: 'end' }}>
              <div>
                <label style={fieldLabel}>{t('Logo thương hiệu (URL/data-URI)')}</label>
                <input
                  value={draft.logoUrl}
                  onChange={(e) => set({ logoUrl: e.target.value })}
                  placeholder="https://… / data:image/…"
                  style={inputStyle}
                />
              </div>
              {!isMessaging && (
                <div style={{ display: 'flex', gap: 4 }}>
                  {(['text', 'html'] as const).map((fmt) => {
                    const active = draft.bodyFormat === fmt;
                    return (
                      <button
                        key={fmt}
                        type="button"
                        onClick={() => set({ bodyFormat: fmt })}
                        style={{
                          padding: '8px 12px',
                          borderRadius: 8,
                          border: active ? '1.5px solid var(--color-blue)' : '1px solid var(--color-border)',
                          background: active ? 'rgba(37,102,235,.08)' : 'var(--color-bg)',
                          color: active ? 'var(--color-blue)' : 'var(--color-muted)',
                          fontSize: 12.5,
                          fontWeight: active ? 600 : 400,
                          cursor: 'pointer',
                        }}
                      >
                        {fmt === 'text' ? t('Văn bản') : 'HTML'}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <div style={{ marginBottom: 12 }}>
              <label style={fieldLabel}>{t('Tiêu đề')}</label>
              <input value={draft.subject} onChange={(e) => set({ subject: e.target.value })} style={inputStyle} />
            </div>
            <div style={{ marginBottom: 12 }}>
              <label style={fieldLabel}>
                {isMessaging ? t('Nội dung tin nhắn') : draft.bodyFormat === 'html' ? t('Nội dung HTML') : t('Nội dung email')}
              </label>
              <textarea
                rows={8}
                value={draft.body}
                onChange={(e) => set({ body: e.target.value })}
                placeholder={
                  draft.bodyFormat === 'html'
                    ? t('HTML — chèn ảnh bằng <img src="…">')
                    : t('Nội dung email/SMS lừa đảo mô phỏng…')
                }
                style={{ ...inputStyle, fontSize: 13.5, resize: 'vertical', lineHeight: 1.5, fontFamily: draft.bodyFormat === 'html' ? 'monospace' : 'inherit' }}
              />
            </div>

            {/* Attachments */}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <label style={{ ...fieldLabel, marginBottom: 0 }}>{t('Tệp đính kèm (mô phỏng)')}</label>
                <button
                  type="button"
                  onClick={addAttachment}
                  style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', color: 'var(--color-blue)', fontSize: 12, cursor: 'pointer' }}
                >
                  <Plus size={13} /> {t('Thêm')}
                </button>
              </div>
              {draft.attachments.length === 0 && (
                <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{t('Chưa có tệp đính kèm.')}</div>
              )}
              {draft.attachments.map((a, i) => (
                <div key={i} style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr auto', gap: 6, marginBottom: 6 }}>
                  <input
                    value={a.name}
                    onChange={(e) => setAttachment(i, { name: e.target.value })}
                    placeholder={t('Tên tệp (vd hoa_don.pdf)')}
                    style={{ ...inputStyle, fontSize: 12.5, padding: '6px 10px' }}
                  />
                  <input
                    value={a.mime ?? ''}
                    onChange={(e) => setAttachment(i, { mime: e.target.value })}
                    placeholder="application/pdf"
                    style={{ ...inputStyle, fontSize: 12.5, padding: '6px 10px' }}
                  />
                  <button
                    type="button"
                    onClick={() => removeAttachment(i)}
                    aria-label={t('Xóa tệp')}
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', borderRadius: 8, cursor: 'pointer', color: 'var(--color-muted)' }}
                  >
                    <X size={14} />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Preview */}
          <div style={{ ...cardStyle, padding: 0, overflow: 'hidden', position: 'sticky', top: 0 }}>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-border)', fontSize: 13, fontWeight: 600, color: 'var(--color-text)' }}>
              {t('Xem trước · người nhận thấy')}
            </div>
            <div style={{ padding: 16 }}>
              <Preview draft={draft} t={t} />
            </div>
          </div>
        </div>
      </div>

      <div style={{ ...cardStyle, padding: '12px 16px', marginTop: 14, fontSize: 12.5, color: 'var(--color-muted)' }}>
        {t('Đây là nội dung mô phỏng phục vụ huấn luyện nhận thức an toàn thông tin — không dùng cho mục đích khác.')}
      </div>
    </div>
  );
}

/** Recipient-eye preview: brand logo header, subject, rendered body, attachment chips. */
function Preview({ draft, t }: { draft: Draft; t: (s: string) => string }) {
  const isMessaging = draft.channel === 'sms' || draft.channel === 'zalo';

  if (isMessaging) {
    return (
      <div style={{ maxWidth: 300 }}>
        <div style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', borderRadius: 16, padding: '10px 14px', fontSize: 13.5, lineHeight: 1.5, color: 'var(--color-text)', whiteSpace: 'pre-wrap' }}>
          {draft.body || t('(nội dung tin nhắn)')}
        </div>
        <div style={{ fontSize: 10.5, color: 'var(--color-muted)', marginTop: 6 }}>
          {CHANNEL_LABELS[draft.channel]} · {draft.category || t('người gửi')}
        </div>
      </div>
    );
  }

  return (
    <div style={{ background: '#fff', color: '#111', border: '1px solid var(--color-border)', borderRadius: 10, overflow: 'hidden' }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #eee', display: 'flex', alignItems: 'center', gap: 10 }}>
        {draft.logoUrl ? (
          <img src={draft.logoUrl} alt="logo" style={{ height: 28, maxWidth: 160, objectFit: 'contain' }} />
        ) : (
          <div style={{ fontSize: 12, color: '#999' }}>{draft.category || t('Người gửi')}</div>
        )}
      </div>
      <div style={{ padding: 16 }}>
        <div style={{ fontSize: 15, fontWeight: 700, marginBottom: 10 }}>{draft.subject || t('(tiêu đề)')}</div>
        {draft.bodyFormat === 'html' ? (
          <div style={{ fontSize: 13.5, lineHeight: 1.55 }} dangerouslySetInnerHTML={{ __html: sanitize(draft.body) }} />
        ) : (
          <div style={{ fontSize: 13.5, lineHeight: 1.55, whiteSpace: 'pre-wrap' }}>{draft.body || t('(nội dung)')}</div>
        )}
        {draft.attachments.filter((a) => a.name.trim()).length > 0 && (
          <div style={{ marginTop: 14, borderTop: '1px solid #eee', paddingTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {draft.attachments
              .filter((a) => a.name.trim())
              .map((a, i) => (
                <span key={i} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: '#f3f4f6', borderRadius: 8, padding: '4px 9px', fontSize: 12, color: '#374151' }}>
                  <Paperclip size={12} /> {a.name}
                </span>
              ))}
          </div>
        )}
      </div>
    </div>
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
