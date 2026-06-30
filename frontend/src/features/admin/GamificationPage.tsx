import { Button } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { Award, ShieldCheck, Target, Zap } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useLeaderboard, useUserBadges, usePointRules, type PointRule } from './api';

/**
 * GamificationPage — badge definitions, point rules, leaderboard scope.
 * Pixel-matched to the design handoff "GAMIFICATION" screen.
 *
 * Badges come from the live backend via `useUserBadges()`
 * (`GET /users/{id}/badges`) for the seeded demo learner, the leaderboard from
 * `useLeaderboard()` (`GET /gamification/leaderboard`), and the point rules from
 * `usePointRules()` (`GET /gamification/point-rules`). Loading/error/empty
 * states handled below.
 */

// Seeded demo learner (dev profile) — the user whose badges/points are shown.
const DEMO_USER_ID = '22222222-2222-2222-2222-222222222222';

// BE `iconRef` hint → lucide icon.
const ICON_MAP: Record<string, LucideIcon> = {
  shield: ShieldCheck,
  target: Target,
  zap: Zap,
  award: Award,
};

function iconFor(ref: string | null): LucideIcon {
  return (ref && ICON_MAP[ref]) || ShieldCheck;
}

/** Signed points label, e.g. `+50đ` / `-5đ`. */
function pointsLabel(points: number): string {
  return `${points > 0 ? '+' : ''}${points}đ`;
}

/** Colour by sign/size: deduction → red, big reward → teal, otherwise blue. */
function pointsColor(points: number): string {
  if (points < 0) return 'var(--color-red)';
  if (points >= 50) return 'var(--color-teal)';
  return 'var(--color-blue)';
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 20,
};

export default function GamificationPage() {
  const t = useT();
  const badges = useUserBadges(DEMO_USER_ID);
  const leaderboard = useLeaderboard();
  const pointRulesQuery = usePointRules();
  const pointRules: PointRule[] = pointRulesQuery.data ?? [];

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
            {t('Gamification · Huy hiệu & Điểm')}
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>
            {t('Định nghĩa huy hiệu, quy tắc tích điểm và bảng xếp hạng')}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {/* Badges */}
          <div style={cardStyle}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 16,
              }}
            >
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>{t('Huy hiệu · Badges')}</div>
              <Button size="sm" variant="primary">
                {t('+ Thêm')}
              </Button>
            </div>

            {badges.isLoading && <InlineMessage>{t('Đang tải huy hiệu…')}</InlineMessage>}
            {!badges.isLoading && badges.isError && (
              <InlineMessage>
                <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được huy hiệu. ')}</span>
                <button type="button" onClick={() => badges.refetch()} style={inlineRetry}>
                  {t('Thử lại')}
                </button>
              </InlineMessage>
            )}
            {!badges.isLoading && !badges.isError && (badges.data?.length ?? 0) === 0 && (
              <InlineMessage>{t('Chưa có huy hiệu nào.')}</InlineMessage>
            )}
            {!badges.isLoading && !badges.isError && (badges.data?.length ?? 0) > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {(badges.data ?? []).map((b) => {
                  const Icon = iconFor(b.iconRef);
                  return (
                    <div
                      key={b.id}
                      style={{
                        background: 'var(--color-bg)',
                        borderRadius: 10,
                        padding: 14,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 12,
                      }}
                    >
                      <Icon size={26} color={b.earned ? 'var(--color-amber)' : 'var(--color-muted)'} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-text)', marginBottom: 3 }}>
                          {b.name}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{b.description ?? ''}</div>
                      </div>
                      <div
                        style={{
                          fontSize: 11.5,
                          color: b.earned ? 'var(--color-teal)' : 'var(--color-muted)',
                          fontWeight: 500,
                        }}
                      >
                        {b.earned ? t('Đã đạt') : t('Chưa đạt')}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Point rules + leaderboard */}
          <div style={cardStyle}>
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)', marginBottom: 16 }}>
              {t('Quy tắc điểm · Point Rules')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {(pointRulesQuery.isLoading || pointRulesQuery.isError || pointRules.length === 0) && (
                <InlineMessage>
                  {pointRulesQuery.isLoading
                    ? t('Đang tải quy tắc điểm…')
                    : pointRulesQuery.isError
                      ? t('Không tải được quy tắc điểm.')
                      : t('Chưa có quy tắc điểm nào.')}
                </InlineMessage>
              )}
              {pointRules.map((r) => (
                <div
                  key={r.action}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: 10,
                    background: 'var(--color-bg)',
                    borderRadius: 8,
                  }}
                >
                  <span style={{ fontSize: 13, color: 'var(--color-text)' }}>{t(r.label)}</span>
                  <span
                    style={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: 14,
                      fontWeight: 700,
                      color: pointsColor(r.points),
                    }}
                  >
                    {pointsLabel(r.points)}
                  </span>
                </div>
              ))}
            </div>

            <div style={{ marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--color-border)' }}>
              <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-text)', marginBottom: 10 }}>
                {t('Bảng xếp hạng')}
              </div>

              {leaderboard.isLoading && <InlineMessage>{t('Đang tải bảng xếp hạng…')}</InlineMessage>}
              {!leaderboard.isLoading && leaderboard.isError && (
                <InlineMessage>
                  <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>{t('Không tải được bảng xếp hạng. ')}</span>
                  <button type="button" onClick={() => leaderboard.refetch()} style={inlineRetry}>
                    {t('Thử lại')}
                  </button>
                </InlineMessage>
              )}
              {!leaderboard.isLoading && !leaderboard.isError && (leaderboard.data?.length ?? 0) === 0 && (
                <InlineMessage>{t('Chưa có dữ liệu xếp hạng.')}</InlineMessage>
              )}
              {!leaderboard.isLoading && !leaderboard.isError && (leaderboard.data?.length ?? 0) > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {(leaderboard.data ?? []).map((row) => (
                    <div
                      key={`${row.rank}-${row.name}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        padding: '8px 12px',
                        background: 'var(--color-bg)',
                        borderRadius: 8,
                      }}
                    >
                      <span
                        style={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: 13,
                          fontWeight: 700,
                          color: 'var(--color-blue)',
                          width: 24,
                        }}
                      >
                        #{row.rank}
                      </span>
                      <span style={{ flex: 1, fontSize: 13, color: 'var(--color-text)' }}>{row.name}</span>
                      <span
                        style={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: 13,
                          fontWeight: 600,
                          color: 'var(--color-teal)',
                        }}
                      >
                        {row.points.toLocaleString('vi-VN')}đ
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
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
