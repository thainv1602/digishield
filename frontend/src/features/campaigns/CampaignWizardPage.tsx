import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail, MessageSquare, QrCode, Smartphone, Sparkles } from 'lucide-react';
import { Button, Stepper, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCreateCampaign, useGroups, type SmartGroup } from './api';
import { useTemplates, type SimTemplate } from '../content/api';

/** Translator function threaded into Vietnamese-string-building helpers. */
type TFn = (key: string, vars?: Record<string, string | number>) => string;

/**
 * CampaignWizardPage — 5-step simulation campaign wizard.
 * Pixel-matched to the design handoff "CAMPAIGN WIZARD" screen.
 *
 * The final launch step posts to the live backend via `useCreateCampaign()`
 * (`POST /sim/campaigns`); on success it toasts and navigates to the results
 * page. The template step loads `GET /ai/templates` and the audience step loads
 * `GET /groups`. The channel list stays a fixed enum (it is the `Channel` enum,
 * not tenant data).
 */

/** Map the wizard channel id onto the backend `Channel` enum (UPPERCASE). */
const CHANNEL_ENUM: Record<string, string> = {
  email: 'EMAIL',
  sms: 'SMS',
  qr: 'QR',
  zalo: 'ZALO',
};

const STEPS = [
  { label: 'Kênh' },
  { label: 'Mẫu' },
  { label: 'Đối tượng' },
  { label: 'Lịch' },
  { label: 'Xem trước' },
];

const channels = [
  { id: 'email', icon: Mail, title: 'Email Phishing', desc: 'SMTP relay, DKIM, tùy chỉnh domain' },
  { id: 'sms', icon: MessageSquare, title: 'SMS / Smishing', desc: 'Brandname giả, OTP giả, link rút gọn' },
  { id: 'qr', icon: QrCode, title: 'QR Code (Quishing)', desc: 'Mã QR nhúng trong email/tài liệu in' },
  { id: 'zalo', icon: Smartphone, title: 'Zalo / Teams / Viber', desc: 'Tin nhắn nội bộ giả qua chat platform' },
] as const;

interface TemplateCard {
  id: string;
  title: string;
  diff: string;
  diffDots: string;
  diffColor: string;
}

/** Localized difficulty label / dots / colour for a backend difficulty. */
function difficultyOf(difficulty: string, t: TFn): { diff: string; diffDots: string; diffColor: string } {
  switch (difficulty) {
    case 'hard':
      return { diff: t('Khó'), diffDots: '●●●', diffColor: 'var(--color-red)' };
    case 'medium':
      return { diff: t('Trung bình'), diffDots: '●●○', diffColor: 'var(--color-amber)' };
    default:
      return { diff: t('Dễ'), diffDots: '●○○', diffColor: 'var(--color-teal)' };
  }
}

/** Map a backend `SimTemplate` onto the template-step card. */
function toTemplateCard(dto: SimTemplate, t: TFn): TemplateCard {
  return { id: dto.id, title: dto.subject, ...difficultyOf(dto.difficulty, t) };
}

interface GroupCard {
  id: string;
  title: string;
  sub: string;
  smart: boolean;
}

/** Map a backend `Group` onto the audience-step card. */
function toGroupCard(dto: SmartGroup, t: TFn): GroupCard {
  const smart = dto.rule_json != null;
  return {
    id: dto.id,
    title: dto.name ?? '—',
    sub: smart ? t('Nhóm thông minh · tự cập nhật theo điều kiện') : t('Nhóm tĩnh'),
    smart,
  };
}

const sendSpeeds = [
  'Ngay lập tức (gửi tất cả cùng lúc)',
  'Rải đều trong 4 giờ (tự nhiên hơn)',
  'AI-timed: gửi lúc mỗi người hay mở email nhất',
];

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

export default function CampaignWizardPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const t = useT();
  const createCampaign = useCreateCampaign();
  const templatesQuery = useTemplates();
  const groupsQuery = useGroups();

  const templates = useMemo<TemplateCard[]>(
    () => (templatesQuery.data ?? []).map((dto) => toTemplateCard(dto, t)),
    [templatesQuery.data, t],
  );
  const groups = useMemo<GroupCard[]>(
    () => (groupsQuery.data ?? []).map((dto) => toGroupCard(dto, t)),
    [groupsQuery.data, t],
  );

  const [step, setStep] = useState(1);
  const [channel, setChannel] = useState<string | null>(null);
  const [template, setTemplate] = useState<string | null>(null);
  const [group, setGroup] = useState<string | null>(null);
  const [speed, setSpeed] = useState(0);
  const [date, setDate] = useState('2026-07-05');
  const [time, setTime] = useState('08:30');

  const canNext = useMemo(() => {
    if (step === 1) return channel !== null;
    if (step === 2) return template !== null;
    if (step === 3) return group !== null;
    return true;
  }, [step, channel, template, group]);

  const launch = () => {
    const enumChannel = channel ? CHANNEL_ENUM[channel] ?? 'EMAIL' : 'EMAIL';
    // POST /sim/campaigns only accepts channel + templateId today; the selected
    // audience group is captured in the UI but not yet part of the create payload.
    createCampaign.mutate(
      { channel: enumChannel, templateId: template },
      {
        onSuccess: (created) => {
          toast.push({ msg: t('Đã phát động chiến dịch mô phỏng'), variant: 'success' });
          navigate(`/campaigns/${created.id}`);
        },
        onError: () => {
          toast.push({ msg: t('Không phát động được chiến dịch'), variant: 'error' });
        },
      },
    );
  };

  const selectedTemplate = templates.find((t) => t.id === template);
  const selectedChannel = channels.find((c) => c.id === channel);
  const selectedGroup = groups.find((g) => g.id === group);

  return (
    <>
      <div style={{ animation: 'fadeUp .3s ease', maxWidth: 900 }}>
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
            {t('Tạo chiến dịch mô phỏng')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>Simulation Campaign Wizard</div>
        </div>

        <div style={{ ...cardStyle, padding: 20, marginBottom: 20 }}>
          <Stepper steps={STEPS.map((s) => ({ ...s, label: t(s.label) }))} current={step} />
        </div>

        {/* Step 1 — Channel */}
        {step === 1 && (
          <div style={{ ...cardStyle, padding: 28, marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text)', marginBottom: 6 }}>
              {t('Chọn kênh tấn công · Select attack channel')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 24 }}>
              {t('Kênh xác định loại mô phỏng và payload sẽ dùng')}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {channels.map((c) => {
                const sel = channel === c.id;
                const Icon = c.icon;
                return (
                  <button
                    type="button"
                    key={c.id}
                    onClick={() => setChannel(c.id)}
                    style={{
                      textAlign: 'left',
                      cursor: 'pointer',
                      borderRadius: 10,
                      padding: 20,
                      border: sel ? '1.5px solid var(--color-blue)' : '1.5px solid var(--color-border)',
                      background: sel ? 'rgba(37,102,235,.06)' : 'var(--color-surface)',
                    }}
                    aria-pressed={sel}
                  >
                    <Icon size={22} color={sel ? 'var(--color-blue)' : 'var(--color-muted)'} style={{ marginBottom: 10 }} />
                    <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text)', marginBottom: 4 }}>
                      {c.title}
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{t(c.desc)}</div>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Step 2 — Template */}
        {step === 2 && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1.4fr',
              gap: 14,
              marginBottom: 16,
            }}
          >
            <div style={{ ...cardStyle, padding: 20 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 4 }}>
                {t('Chọn mẫu mô phỏng')}
              </div>
              <div style={{ fontSize: 12, color: 'var(--color-muted)', marginBottom: 16 }}>
                Select simulation template
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(templatesQuery.isLoading || (!templatesQuery.isLoading && templates.length === 0)) && (
                  <div style={{ fontSize: 12.5, color: 'var(--color-muted)', padding: '4px 2px' }}>
                    {templatesQuery.isLoading ? t('Đang tải mẫu…') : t('Chưa có mẫu nào — tạo bằng AI bên dưới.')}
                  </div>
                )}
                {templates.map((t) => {
                  const sel = template === t.id;
                  return (
                    <button
                      type="button"
                      key={t.id}
                      onClick={() => setTemplate(t.id)}
                      style={{
                        textAlign: 'left',
                        cursor: 'pointer',
                        borderRadius: 9,
                        padding: 14,
                        border: sel ? '1.5px solid var(--color-blue)' : '1.5px solid var(--color-border)',
                        background: sel ? 'rgba(37,102,235,.06)' : 'var(--color-surface)',
                      }}
                      aria-pressed={sel}
                    >
                      <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-text)', marginBottom: 4 }}>
                        {t.title}
                      </div>
                      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                        <span
                          style={{
                            background: 'var(--color-bg)',
                            color: 'var(--color-muted)',
                            borderRadius: 99,
                            padding: '2px 8px',
                            fontSize: 11,
                          }}
                        >
                          Email
                        </span>
                        <span style={{ color: t.diffColor, fontSize: 12, fontWeight: 500 }}>
                          {t.diffDots} {t.diff}
                        </span>
                      </div>
                    </button>
                  );
                })}
                <div
                  style={{
                    borderRadius: 9,
                    padding: 14,
                    cursor: 'pointer',
                    border: '1.5px dashed var(--color-border)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                  }}
                >
                  <Sparkles size={16} color="var(--color-blue)" />
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-blue)' }}>{t('Tạo bằng AI')}</div>
                    <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{t('AIDA engine · tùy ngành, mùa vụ')}</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Email preview */}
            <div style={{ ...cardStyle, padding: 20 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
                {t('Xem trước email')}
              </div>
              <div
                style={{
                  background: 'var(--color-bg)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 8,
                  padding: 16,
                  fontSize: 13,
                  lineHeight: 1.6,
                }}
              >
                <div
                  style={{
                    marginBottom: 10,
                    paddingBottom: 10,
                    borderBottom: '1px solid var(--color-border)',
                  }}
                >
                  <div style={{ color: 'var(--color-muted)' }}>
                    From:{' '}
                    <code style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--color-text)', fontSize: 12 }}>
                      no-reply@edu-vn.support
                    </code>
                  </div>
                  <div style={{ color: 'var(--color-muted)' }}>
                    Subject: <strong style={{ color: 'var(--color-text)' }}>{t('Thông báo hoàn học phí học kỳ II')}</strong>
                  </div>
                </div>
                <p style={{ color: 'var(--color-text)', marginBottom: 8 }}>{t('Kính gửi sinh viên/học viên,')}</p>
                <p style={{ color: 'var(--color-text)', marginBottom: 8 }}>
                  {t('Theo quy định Bộ GD&ĐT, nhà trường sẽ hoàn học phí HK2 cho các trường hợp đủ điều kiện.')}
                </p>
                <p style={{ color: 'var(--color-text)', marginBottom: 12 }}>
                  {t('Vui lòng')}{' '}
                  <span style={{ color: 'var(--color-blue)', fontWeight: 500, cursor: 'pointer' }}>
                    {t('xác nhận thông tin ngân hàng tại đây')}
                  </span>{' '}
                  {t('trước 30/06/2026.')}
                </p>
                <p style={{ color: 'var(--color-muted)', fontSize: 12 }}>{t('Phòng Tài vụ · Đại học Quốc gia')}</p>
              </div>
              <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 12, color: 'var(--color-muted)' }}>{t('Độ khó:')}</span>
                <span style={{ fontSize: 13, color: 'var(--color-amber)', fontWeight: 500 }}>{t('●●○ Trung bình')}</span>
              </div>
            </div>
          </div>
        )}

        {/* Step 3 — Audience */}
        {step === 3 && (
          <div style={{ ...cardStyle, padding: 28, marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text)', marginBottom: 6 }}>
              {t('Chọn đối tượng · Target audience')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 24 }}>
              {t('Chọn nhóm người dùng sẽ nhận email mô phỏng')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {(groupsQuery.isLoading || (!groupsQuery.isLoading && groups.length === 0)) && (
                <div style={{ fontSize: 12.5, color: 'var(--color-muted)', padding: '4px 2px' }}>
                  {groupsQuery.isLoading ? t('Đang tải nhóm…') : t('Chưa có nhóm đối tượng nào.')}
                </div>
              )}
              {groups.map((g) => {
                const sel = group === g.id;
                return (
                  <button
                    type="button"
                    key={g.id}
                    onClick={() => setGroup(g.id)}
                    style={{
                      textAlign: 'left',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      borderRadius: 10,
                      padding: '16px 18px',
                      border: sel ? '1.5px solid var(--color-blue)' : '1.5px solid var(--color-border)',
                      background: sel ? 'rgba(37,102,235,.06)' : 'var(--color-surface)',
                    }}
                    aria-pressed={sel}
                  >
                    <Radio checked={sel} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 3 }}>
                        {g.title}
                      </div>
                      <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{g.sub}</div>
                    </div>
                    {g.smart && (
                      <span
                        style={{
                          background: 'rgba(37,102,235,.1)',
                          color: 'var(--color-blue)',
                          borderRadius: 99,
                          padding: '3px 9px',
                          fontSize: 11,
                          fontWeight: 600,
                          flexShrink: 0,
                        }}
                      >
                        Smart Group
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Step 4 — Schedule */}
        {step === 4 && (
          <div style={{ ...cardStyle, padding: 28, marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text)', marginBottom: 6 }}>
              {t('Đặt lịch gửi · Schedule')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 24 }}>
              {t('Cấu hình thời điểm và tốc độ gửi email mô phỏng')}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 16,
                marginBottom: 20,
              }}
            >
              <div>
                <label style={fieldLabel}>{t('Ngày gửi')}</label>
                <input type="date" value={date} onChange={(e) => setDate(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={fieldLabel}>{t('Giờ gửi')}</label>
                <input type="time" value={time} onChange={(e) => setTime(e.target.value)} style={inputStyle} />
              </div>
            </div>
            <div style={{ marginBottom: 20 }}>
              <label style={{ ...fieldLabel, marginBottom: 10 }}>{t('Tốc độ gửi')}</label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {sendSpeeds.map((label, i) => {
                  const sel = speed === i;
                  return (
                    <button
                      type="button"
                      key={label}
                      onClick={() => setSpeed(i)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        cursor: 'pointer',
                        background: 'none',
                        border: 'none',
                        padding: 0,
                        textAlign: 'left',
                      }}
                      aria-pressed={sel}
                    >
                      <Radio checked={sel} />
                      <span style={{ fontSize: 14, color: sel ? 'var(--color-text)' : 'var(--color-muted)' }}>{t(label)}</span>
                    </button>
                  );
                })}
              </div>
            </div>
            <div
              style={{
                background: 'rgba(37,102,235,.08)',
                border: '1px solid rgba(37,102,235,.2)',
                borderRadius: 8,
                padding: '12px 16px',
                fontSize: 13,
                color: 'var(--color-blue)',
              }}
            >
              {t('Sau khi ai đó bấm link mô phỏng → hệ thống tự đăng ký họ vào khóa học phù hợp và hiển thị trang Just-in-time coaching.')}
            </div>
          </div>
        )}

        {/* Step 5 — Preview & Launch */}
        {step === 5 && (
          <div style={{ ...cardStyle, padding: 28, marginBottom: 16 }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text)', marginBottom: 6 }}>
              {t('Xem trước & Phát động · Preview & Launch')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 24 }}>
              {t('Kiểm tra thông tin lần cuối trước khi phát động')}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 12,
                marginBottom: 28,
              }}
            >
              <SummaryTile label={t('Kênh')} value={selectedChannel?.title ?? 'Email Phishing'} />
              <SummaryTile label={t('Mẫu')} value={selectedTemplate?.title ?? t('Hoàn tiền học phí')} />
              <SummaryTile
                label={t('Đối tượng')}
                value={selectedGroup ? (selectedGroup.sub.split(' · ')[0] ?? t('212 người')) : t('212 người')}
                sub={selectedGroup?.title ?? t('Kế toán + Kinh doanh')}
              />
              <SummaryTile
                label={t('Lịch')}
                value={`${date.split('-').reverse().join('/')} ${time}`}
                sub={t((sendSpeeds[speed] ?? '').split(' (')[0] ?? '')}
              />
            </div>
            <Button variant="danger" fullWidth onClick={launch}>
              {t('Phát động chiến dịch · Launch Campaign')}
            </Button>
            <div style={{ textAlign: 'center', marginTop: 10, fontSize: 12.5, color: 'var(--color-muted)' }}>
              {t('Hành động này không thể hoàn tác.')}
            </div>
          </div>
        )}

        {/* Wizard nav */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button variant="secondary" disabled={step === 1} onClick={() => setStep((s) => Math.max(1, s - 1))}>
            {t('← Trở lại')}
          </Button>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{t('Bước {n} / 5', { n: step })}</div>
          {step < 5 ? (
            <Button variant="primary" disabled={!canNext} onClick={() => setStep((s) => Math.min(5, s + 1))}>
              {t('Tiếp theo →')}
            </Button>
          ) : (
            <div style={{ width: 100 }} />
          )}
        </div>
      </div>
    </>
  );
}

const fieldLabel: React.CSSProperties = {
  display: 'block',
  fontSize: 13,
  fontWeight: 500,
  color: 'var(--color-muted)',
  marginBottom: 6,
};
const inputStyle: React.CSSProperties = {
  width: '100%',
  background: 'var(--color-bg)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  padding: '10px 14px',
  color: 'var(--color-text)',
  fontSize: 14,
};

function Radio({ checked }: { checked: boolean }) {
  return (
    <div
      aria-hidden="true"
      style={{
        width: 18,
        height: 18,
        borderRadius: '50%',
        border: `2px solid ${checked ? 'var(--color-blue)' : 'var(--color-muted)'}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
      }}
    >
      {checked && <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--color-blue)' }} />}
    </div>
  );
}

function SummaryTile({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div style={{ background: 'var(--color-bg)', borderRadius: 10, padding: 16 }}>
      <div style={{ ...labelStyle, marginBottom: 8 }}>{label}</div>
      <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text)' }}>{value}</div>
      {sub && <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{sub}</div>}
    </div>
  );
}
