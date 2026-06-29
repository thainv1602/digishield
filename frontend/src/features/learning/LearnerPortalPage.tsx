import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Target, Zap, Award, AlertTriangle } from 'lucide-react';
import { Button, ProgressBar, Drawer, Select, useToast } from '@/shared/ui';
import { useAuth } from '@/app/auth/useAuth';
import {
  useEnrollments,
  useLeaderboard,
  useUserBadges,
  useUserPoints,
  useReportPhishing,
  type Enrollment,
  type ReportChannel,
} from './api';

/**
 * LearnerPortalPage — end-user training home (`/learn`).
 *
 * Layout mirrors the design handoff:
 *  - Greeting + always-visible top-right red "Báo cáo lừa đảo" CTA
 *  - 2-col: progress card (ProgressBar + badge chips) | points/leaderboard card
 *  - Tasks list grouped by urgency (overdue → upcoming → done)
 *
 * Data comes from the live backend:
 *  - `useEnrollments()` (`GET /enrollments`) → progress + tasks (main content)
 *  - `useLeaderboard()` (`GET /gamification/leaderboard`) → ranking card
 *  - `useUserPoints(userId)` / `useUserBadges(userId)` (`GET /users/{id}/points`
 *    & `/badges`) → points total + earned badges (only when the user id is known)
 */

type TaskUrgency = 'overdue' | 'upcoming' | 'done';

interface LearnerTask {
  id: string;
  title: string;
  due: string;
  urgency: TaskUrgency;
  /** Target route for the task action. */
  to: string;
  cta: string;
}

interface BadgeChip {
  id: string;
  label: string;
  icon: typeof ShieldCheck;
  /** chip color theme */
  tone: 'amber' | 'blue';
}

interface LeaderRow {
  id: string;
  name: string;
  points: string;
  isMe?: boolean;
}

const URGENCY_ORDER: TaskUrgency[] = ['overdue', 'upcoming', 'done'];

/** Map an `iconRef` hint to a chip icon + tone. */
function badgeChipMeta(iconRef: string | null, index: number): Pick<BadgeChip, 'icon' | 'tone'> {
  const ref = (iconRef ?? '').toLowerCase();
  if (ref.includes('target')) return { icon: Target, tone: 'blue' };
  if (ref.includes('zap')) return { icon: Zap, tone: 'amber' };
  if (ref.includes('shield')) return { icon: ShieldCheck, tone: 'amber' };
  if (ref.includes('award') || ref.includes('cert')) return { icon: Award, tone: 'blue' };
  return index % 2 === 0
    ? { icon: ShieldCheck, tone: 'amber' }
    : { icon: Target, tone: 'blue' };
}

/** Format an integer with Vietnamese thousands separators (e.g. 1240 → "1.240"). */
function formatPoints(n: number): string {
  return n.toLocaleString('vi-VN');
}

/** Derive a task from an enrollment (skip completed ones). */
function toTask(e: Enrollment): LearnerTask | null {
  const status = (e.status ?? '').toLowerCase();
  if (status === 'completed') return null;
  const overdue = status === 'overdue';
  const title = e.courseTitle ?? 'Khoá học';
  return {
    id: e.id,
    title: overdue ? `Hoàn thành "${title}"` : `Tiếp tục "${title}"`,
    due:
      typeof e.progress === 'number'
        ? `Tiến độ ${e.progress}%`
        : overdue
          ? 'Quá hạn'
          : 'Chưa bắt đầu',
    urgency: overdue ? 'overdue' : 'upcoming',
    to: '/learn/courses',
    cta: overdue ? 'Làm bài' : 'Học ngay',
  };
}

export default function LearnerPortalPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const userId = user?.id ?? null;

  const enrollmentsQuery = useEnrollments();
  const leaderboardQuery = useLeaderboard();
  const pointsQuery = useUserPoints(userId);
  const badgesQuery = useUserBadges(userId);

  const enrollments = useMemo<Enrollment[]>(
    () => enrollmentsQuery.data ?? [],
    [enrollmentsQuery.data],
  );

  // Progress: average across enrollments + completed/total counts.
  const lessonsTotal = enrollments.length;
  const lessonsDone = enrollments.filter((e) => (e.status ?? '').toLowerCase() === 'completed').length;
  const progressPct =
    lessonsTotal > 0
      ? Math.round(
          enrollments.reduce((acc, e) => acc + (e.progress ?? 0), 0) / lessonsTotal,
        )
      : 0;

  // Tasks derived from non-completed enrollments, ordered by urgency.
  const tasks = useMemo<LearnerTask[]>(
    () =>
      enrollments
        .map(toTask)
        .filter((t): t is LearnerTask => t != null)
        .sort((a, b) => URGENCY_ORDER.indexOf(a.urgency) - URGENCY_ORDER.indexOf(b.urgency)),
    [enrollments],
  );
  const openTaskCount = tasks.length;

  // Earned badges → chips.
  const badges: BadgeChip[] = useMemo(
    () =>
      (badgesQuery.data ?? [])
        .filter((b) => b.earned)
        .map((b, i) => ({ id: b.id, label: b.name, ...badgeChipMeta(b.iconRef, i) })),
    [badgesQuery.data],
  );

  // Leaderboard rows (mark the current user's row when name matches).
  const myName = user?.name ?? null;
  const leaderboard: LeaderRow[] = useMemo(
    () =>
      (leaderboardQuery.data ?? []).slice(0, 5).map((row) => ({
        id: `lb-${row.rank}`,
        name: `${row.rank}. ${row.name}`,
        points: formatPoints(row.points),
        isMe: myName != null && row.name === myName,
      })),
    [leaderboardQuery.data, myName],
  );

  // Points: prefer the dedicated endpoint; fall back to the user's leaderboard row.
  const myLeaderRow = (leaderboardQuery.data ?? []).find(
    (r) => myName != null && r.name === myName,
  );
  const points =
    pointsQuery.data?.total != null
      ? formatPoints(pointsQuery.data.total)
      : myLeaderRow
        ? formatPoints(myLeaderRow.points)
        : '—';
  const rankLabel = myLeaderRow
    ? `Hạng ${myLeaderRow.rank} trong tổ chức`
    : 'Bảng xếp hạng tổ chức';

  const learnerName = user?.name ?? 'bạn';
  const dateLabel = new Date().toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });

  const enrollmentsLoading = enrollmentsQuery.isLoading;
  const enrollmentsError = enrollmentsQuery.isError;

  // Report-phishing CTA → a Drawer that submits to POST /reports/phishing.
  const toast = useToast();
  const reportMut = useReportPhishing();
  const [reportOpen, setReportOpen] = useState(false);
  const [reportChannel, setReportChannel] = useState<ReportChannel>('email');
  const [reportPayload, setReportPayload] = useState('');

  function submitReport() {
    const payload = reportPayload.trim();
    if (payload.length === 0) {
      toast('Vui lòng dán nội dung nghi ngờ');
      return;
    }
    reportMut.mutate(
      { payload, channel: reportChannel },
      {
        onSuccess: () => {
          toast('Đã gửi báo cáo. Cảm ơn bạn!');
          setReportOpen(false);
          setReportPayload('');
        },
        onError: () => toast('Gửi báo cáo thất bại, thử lại'),
      },
    );
  }

  return (
    <>
    <div style={{ animation: 'fadeUp .3s ease' }}>
      {/* Greeting + report CTA */}
        <header
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 16,
            marginBottom: 24,
          }}
        >
          <div>
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
              Xin chào, {learnerName}
            </h1>
            <p style={{ margin: 0, fontSize: 13.5, color: 'var(--muted)' }}>
              Hôm nay là {dateLabel} · Bạn có {openTaskCount} việc cần làm
            </p>
          </div>
          <Button type="button" variant="danger" onClick={() => setReportOpen(true)}>
            Báo cáo lừa đảo
          </Button>
        </header>

        {/* 2-col: progress | points */}
        <section
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 14,
            marginBottom: 14,
          }}
        >
          {/* Progress card */}
          <article
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 12,
              padding: 20,
            }}
          >
            <h2 style={cardTitle}>Tiến độ học · Progress</h2>
            <div
              style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 12 }}
            >
              <div style={{ flex: 1 }}>
                <ProgressBar value={progressPct} max={100} color="var(--blue)" />
              </div>
              <span
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: 'var(--text)',
                  fontFamily: "'Space Grotesk', system-ui",
                }}
              >
                {progressPct}%
              </span>
            </div>
            <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0 0 12px' }}>
              {lessonsDone}/{lessonsTotal} khoá hoàn thành
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              {badges.length === 0 && (
                <span style={{ fontSize: 12, color: 'var(--muted)' }}>Chưa có huy hiệu</span>
              )}
              {badges.map((b) => {
                const Icon = b.icon;
                const amber = b.tone === 'amber';
                return (
                  <span
                    key={b.id}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      borderRadius: 8,
                      padding: '6px 10px',
                      fontSize: 11.5,
                      fontWeight: 600,
                      background: amber
                        ? 'rgba(224,138,11,.1)'
                        : 'rgba(37,102,235,.08)',
                      border: amber
                        ? '1.5px solid rgba(224,138,11,.3)'
                        : '1.5px solid rgba(37,102,235,.2)',
                      color: amber ? '#8B5B00' : '#1A4FD0',
                    }}
                  >
                    <Icon size={15} aria-hidden="true" />
                    {b.label}
                  </span>
                );
              })}
            </div>
          </article>

          {/* Points / leaderboard card */}
          <article
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 12,
              padding: 20,
            }}
          >
            <h2 style={cardTitle}>Điểm &amp; Xếp hạng · Points</h2>
            <div
              style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 10 }}
            >
              <span
                style={{
                  fontFamily: "'Space Grotesk', system-ui",
                  fontSize: 40,
                  fontWeight: 700,
                  color: 'var(--text)',
                  letterSpacing: '-0.02em',
                }}
              >
                {points}
              </span>
              <span style={{ fontSize: 14, color: 'var(--muted)' }}>điểm</span>
            </div>
            <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0 0 12px' }}>
              {rankLabel}
            </p>
            <div
              style={{
                background: 'var(--bg)',
                borderRadius: 8,
                padding: '10px 14px',
                display: 'grid',
                gap: 5,
              }}
            >
              {leaderboard.length === 0 && (
                <div style={{ fontSize: 12.5, color: 'var(--muted)' }}>
                  {leaderboardQuery.isLoading ? 'Đang tải…' : 'Chưa có dữ liệu xếp hạng'}
                </div>
              )}
              {leaderboard.map((row) => (
                <div
                  key={row.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: 12.5,
                    borderRadius: row.isMe ? 6 : undefined,
                    padding: row.isMe ? '3px 6px' : undefined,
                    background: row.isMe ? '#ECF3FF' : undefined,
                  }}
                >
                  <span
                    style={{
                      color: row.isMe ? '#1A4FD0' : 'var(--muted)',
                      fontWeight: row.isMe ? 500 : 400,
                    }}
                  >
                    {row.name}
                  </span>
                  <span
                    style={{
                      fontWeight: row.isMe ? 700 : 600,
                      color: row.isMe ? '#1A4FD0' : 'var(--text)',
                      fontFamily: "'JetBrains Mono', monospace",
                    }}
                  >
                    {row.points}
                  </span>
                </div>
              ))}
            </div>
          </article>
        </section>

        {/* Tasks grouped by urgency */}
        <section
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            padding: 20,
          }}
          aria-label="Việc cần làm"
        >
          <h2 style={cardTitle}>Việc cần làm · Tasks</h2>
          {enrollmentsLoading && (
            <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>Đang tải…</p>
          )}
          {!enrollmentsLoading && enrollmentsError && (
            <p style={{ fontSize: 13, color: 'var(--red)', margin: 0 }}>
              Không tải được danh sách. Vui lòng thử lại.
            </p>
          )}
          {!enrollmentsLoading && !enrollmentsError && tasks.length === 0 && (
            <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>
              Bạn đã hoàn thành tất cả nhiệm vụ.
            </p>
          )}
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 8 }}>
            {tasks.map((task) => {
              const overdue = task.urgency === 'overdue';
              return (
                <li
                  key={task.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                    padding: '12px 16px',
                    borderRadius: 10,
                    background: overdue ? '#FBE0DF' : 'var(--bg)',
                    border: overdue
                      ? '1px solid rgba(221,59,64,.15)'
                      : '1px solid var(--border)',
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <div
                      style={{
                        fontSize: 13.5,
                        fontWeight: overdue ? 600 : 500,
                        color: 'var(--text)',
                        marginBottom: 3,
                      }}
                    >
                      {task.title}
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        fontWeight: overdue ? 500 : 400,
                        color: overdue ? 'var(--red)' : 'var(--muted)',
                      }}
                    >
                      {overdue && (
                        <AlertTriangle
                          size={12}
                          aria-hidden="true"
                          style={{ verticalAlign: '-2px', marginRight: 4 }}
                        />
                      )}
                      {task.due}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant={overdue ? 'primary' : 'secondary'}
                    size="sm"
                    onClick={() => navigate(task.to)}
                  >
                    {task.cta}
                  </Button>
                </li>
              );
            })}
          </ul>
        </section>
    </div>

    <Drawer
      open={reportOpen}
      onClose={() => setReportOpen(false)}
      title="Báo cáo lừa đảo"
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <p style={{ margin: 0, fontSize: 13, color: 'var(--muted)' }}>
          Dán nội dung email/SMS đáng ngờ (kèm link, người gửi nếu có). Đội SOC sẽ
          phân tích và cảnh báo cả tổ chức nếu là mối đe dọa thật.
        </p>
        <Select
          label="Kênh"
          value={reportChannel}
          onChange={(e) => setReportChannel(e.target.value as ReportChannel)}
        >
          <option value="email">Email</option>
          <option value="sms">SMS</option>
        </Select>
        <label style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--text)' }}>
          Nội dung nghi ngờ
          <textarea
            value={reportPayload}
            onChange={(e) => setReportPayload(e.target.value)}
            rows={8}
            placeholder="Dán nội dung email/SMS, đường link hoặc số điện thoại đáng ngờ…"
            style={{
              width: '100%',
              marginTop: 6,
              padding: '10px 12px',
              fontSize: 13,
              fontFamily: 'inherit',
              color: 'var(--text)',
              background: 'var(--bg)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              resize: 'vertical',
              boxSizing: 'border-box',
            }}
          />
        </label>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <Button type="button" variant="secondary" onClick={() => setReportOpen(false)}>
            Huỷ
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={reportMut.isPending}
            onClick={submitReport}
          >
            {reportMut.isPending ? 'Đang gửi…' : 'Gửi báo cáo'}
          </Button>
        </div>
      </div>
    </Drawer>
    </>
  );
}

const cardTitle: React.CSSProperties = {
  fontSize: 14,
  fontWeight: 600,
  color: 'var(--text)',
  margin: '0 0 14px',
};
