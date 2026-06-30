import { useMemo, useState } from 'react';
import { Button, Select, StatusPill } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import {
  useEvaluateIntervention,
  useRunOrchestration,
  useAidaRuns,
  type AidaRun,
} from './api';

/**
 * AidaPage — AI Orchestration (Adaptive Intelligence Deployment Agent).
 * Pixel-matched to the design handoff "AIDA ORCHESTRATION" screen.
 *
 * Wired to the live backend:
 *  - "Chạy AIDA ngay" → POST /ai/orchestration/run (records a run; the panel
 *    on the right refreshes to show it).
 *  - "Đánh giá can thiệp" → POST /interventions/evaluate (sample transaction).
 *  - Recent runs → GET /ai/orchestration/runs.
 */

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 24,
};

interface RunRow {
  id: string;
  when: string;
  scope: string;
  result: string;
  ok: boolean;
}

/** Format an ISO timestamp as `Chạy dd/MM/yyyy HH:mm`. */
function formatRunTime(
  iso: string | undefined,
  t: (key: string, vars?: Record<string, string | number>) => string,
): string {
  if (!iso) return t('Chạy —');
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return t('Chạy —');
  const pad = (n: number) => String(n).padStart(2, '0');
  const time = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return t('Chạy {time}', { time });
}

/** Map a backend `AidaRun` onto the recent-runs row. */
function toRunRow(
  dto: AidaRun,
  t: (key: string, vars?: Record<string, string | number>) => string,
): RunRow {
  return {
    id: dto.id,
    when: formatRunTime(dto.created_at, t),
    scope: t('Phạm vi: {scope}', { scope: dto.scope }),
    result: dto.summary ?? '—',
    ok: dto.status === 'success',
  };
}

const SCOPE_LABELS: Record<string, string> = {
  org: 'Toàn tổ chức',
  acc: 'Phòng Kế toán',
  risk: 'Nhóm Risk Score cao',
};

export default function AidaPage() {
  const t = useT();
  const [scope, setScope] = useState('org');
  const [goal, setGoal] = useState('reduce');

  const runOrch = useRunOrchestration();
  const evaluate = useEvaluateIntervention();
  const runsQuery = useAidaRuns();

  const runs = useMemo<RunRow[]>(
    () => (runsQuery.data ?? []).map((dto) => toRunRow(dto, t)),
    [runsQuery.data, t],
  );

  function runAida() {
    runOrch.mutate({ scope: SCOPE_LABELS[scope] ?? scope });
  }

  function runEvaluate() {
    evaluate.mutate({
      userId: '22222222-2222-2222-2222-222222222222',
      amount: 50_000_000,
      destAccount: '9999-8888-7777',
      onCall: true,
      newPayee: true,
    });
  }

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
            AI Orchestration · AIDA
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
            {t('Adaptive Intelligence Deployment Agent — tự động hóa lộ trình học')}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {/* Trigger AIDA */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 20 }}>
              {t('Kích hoạt AIDA')}
            </div>
            <div style={{ marginBottom: 16 }}>
              <label style={fieldLabel}>{t('Phạm vi')}</label>
              <Select aria-label={t('Phạm vi')} value={scope} onChange={(e) => setScope(e.target.value)}>
                <option value="org">{t('Toàn tổ chức (1.240 người)')}</option>
                <option value="acc">{t('Phòng Kế toán (212 người)')}</option>
                <option value="risk">{t('Nhóm Risk Score cao (87 người)')}</option>
              </Select>
            </div>
            <div style={{ marginBottom: 20 }}>
              <label style={fieldLabel}>{t('Mục tiêu')}</label>
              <Select aria-label={t('Mục tiêu')} value={goal} onChange={(e) => setGoal(e.target.value)}>
                <option value="reduce">{t('Giảm Risk Score xuống dưới 50')}</option>
                <option value="mandatory">{t('Hoàn thành lộ trình bắt buộc')}</option>
                <option value="prepare">{t('Chuẩn bị cho chiến dịch mô phỏng')}</option>
              </Select>
            </div>
            <Button variant="primary" fullWidth onClick={runAida} disabled={runOrch.isPending}>
              {runOrch.isPending ? t('Đang chạy AIDA…') : t('Chạy AIDA ngay')}
            </Button>

            {/* Orchestration result (live: POST /ai/orchestration/run) */}
            {runOrch.isError && (
              <ResultBox tone="error">{t('Không chạy được AIDA. Vui lòng thử lại.')}</ResultBox>
            )}
            {runOrch.isSuccess && (
              <ResultBox tone="info">
                {t('Đã chạy AIDA cho phạm vi ')}<strong>{t(SCOPE_LABELS[scope] ?? scope)}</strong>{t('. Xem kết quả bên phải.')}
              </ResultBox>
            )}

            {/* Intervention evaluation (live: POST /interventions/evaluate) */}
            <div style={{ marginTop: 14 }}>
              <Button variant="outline" fullWidth onClick={runEvaluate} disabled={evaluate.isPending}>
                {evaluate.isPending ? t('Đang đánh giá…') : t('Đánh giá can thiệp (mẫu)')}
              </Button>
            </div>
            {evaluate.isError && (
              <ResultBox tone="error">{t('Không đánh giá được can thiệp. Vui lòng thử lại.')}</ResultBox>
            )}
            {evaluate.data && (
              <ResultBox tone="info">
                {t('Quyết định: ')}<strong>{evaluate.data.decision}</strong>
                {evaluate.data.signals?.length > 0 && t(' · Tín hiệu: {signals}', { signals: evaluate.data.signals.join(', ') })}
                {evaluate.data.message && (
                  <div style={{ marginTop: 6, color: 'var(--color-muted)' }}>{evaluate.data.message}</div>
                )}
              </ResultBox>
            )}
          </div>

          {/* Recent runs (static — no BE run-history endpoint) */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Kết quả lần chạy gần nhất')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {(runsQuery.isLoading || runsQuery.isError || runs.length === 0) && (
                <div style={{ fontSize: 12.5, color: 'var(--color-muted)', padding: '4px 2px' }}>
                  {runsQuery.isLoading
                    ? t('Đang tải lịch sử…')
                    : runsQuery.isError
                      ? t('Không tải được lịch sử chạy.')
                      : t('Chưa có lần chạy nào. Bấm “Chạy AIDA ngay”.')}
                </div>
              )}
              {runs.map((r) => (
                <div key={r.id} style={{ background: 'var(--color-bg)', borderRadius: 10, padding: 14 }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: 6,
                    }}
                  >
                    <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--color-text)' }}>{r.when}</span>
                    <StatusPill variant={r.ok ? 'safe' : 'threat'} dot={false}>
                      {r.ok ? t('Thành công') : t('Thất bại')}
                    </StatusPill>
                  </div>
                  <div style={{ fontSize: 12.5, color: 'var(--color-muted)', marginBottom: 6 }}>{r.scope}</div>
                  <div style={{ fontSize: 13, color: 'var(--color-blue)', fontWeight: 500 }}>{r.result}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function ResultBox({ tone, children }: { tone: 'info' | 'error'; children: React.ReactNode }) {
  const isError = tone === 'error';
  return (
    <div
      style={{
        marginTop: 14,
        background: isError ? 'rgba(221,59,64,.06)' : 'rgba(37,102,235,.06)',
        border: `1px solid ${isError ? 'rgba(221,59,64,.2)' : 'rgba(37,102,235,.15)'}`,
        borderRadius: 8,
        padding: '12px 16px',
        fontSize: 13,
        color: isError ? 'var(--color-red)' : 'var(--pill-info-fg)',
      }}
    >
      {children}
    </div>
  );
}

const fieldLabel: React.CSSProperties = {
  display: 'block',
  fontSize: 12.5,
  fontWeight: 500,
  color: 'var(--color-muted)',
  marginBottom: 6,
};
