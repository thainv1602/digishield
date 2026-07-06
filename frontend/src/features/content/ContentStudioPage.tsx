import { useMemo, useState } from 'react';
import { Button, useToast } from '@/shared/ui';
import { Sparkles, Trash2 } from 'lucide-react';
import {
  useTemplates,
  useGenerateTemplate,
  useCreateTemplate,
  useUpdateTemplate,
  useSubmitTemplate,
  useDeleteTemplate,
  type SimTemplate,
} from './api';
import { useT } from '@/shared/i18n/I18nProvider';

/**
 * ContentStudioPage — author, AI-generate, edit, submit and delete simulation
 * templates (phishing mail/SMS impersonating tax, government, insurance, banks…).
 *
 * The library loads from `GET /ai/templates`; selecting a card loads it into the
 * editor. Save persists via create/update, "Gửi duyệt" submits (draft→approved),
 * "Sinh bằng AI" generates + persists a draft, and delete removes it.
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
  status: string;
}

const EMPTY_DRAFT: Draft = {
  id: null,
  channel: 'email',
  category: '',
  difficulty: 'medium',
  subject: '',
  body: '',
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
    status: dto.status,
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

  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

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
      category: draft.category.trim() || null,
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

  const bodyLabel = draft.channel === 'sms' || draft.channel === 'zalo' ? t('Nội dung tin nhắn') : t('Nội dung email');

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

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 14, alignItems: 'start' }}>
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

          {/* Filter pills */}
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

        {/* Editor */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
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

            {/* Channel · Category · Difficulty */}
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

            <div style={{ marginBottom: 12 }}>
              <label style={fieldLabel}>{t('Tiêu đề')}</label>
              <input value={draft.subject} onChange={(e) => set({ subject: e.target.value })} style={inputStyle} />
            </div>
            <div>
              <label style={fieldLabel}>{bodyLabel}</label>
              <textarea
                rows={8}
                value={draft.body}
                onChange={(e) => set({ body: e.target.value })}
                placeholder={t('Nội dung email/SMS lừa đảo mô phỏng…')}
                style={{ ...inputStyle, fontSize: 13.5, resize: 'vertical', lineHeight: 1.5 }}
              />
            </div>
          </div>

          <div style={{ ...cardStyle, padding: 16, fontSize: 12.5, color: 'var(--color-muted)' }}>
            {t('Đây là nội dung mô phỏng phục vụ huấn luyện nhận thức an toàn thông tin — không dùng cho mục đích khác.')}
          </div>
        </div>
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
