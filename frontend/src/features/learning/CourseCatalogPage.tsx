import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Fish, Phone, Bot, type LucideIcon } from 'lucide-react';
import { useCourses, type Course as CourseDto } from './api';
import { useT } from '@/shared/i18n/i18nContext';

/**
 * CourseCatalogPage — learner course grid (`/learn/courses`).
 *
 * 4-col card grid: icon bg + title + progress bar + CTA.
 * States: Completed (green) / In progress (blue border) / Locked (dimmed).
 *
 * Data comes from the live backend via `useCourses()` (`GET /courses`).
 * Loading/error/empty states handled below.
 */

type CourseState = 'completed' | 'in_progress' | 'locked';

interface Course {
  id: string;
  title: string;
  meta: string;
  icon: LucideIcon;
  /** icon tile background (CSS color) */
  iconBg: string;
  state: CourseState;
  progressPct: number;
  statusLabel: string;
  ctaLabel: string;
  lockedNote?: string;
}

// Icon rotation so each card still gets a distinct glyph (BE has no icon field).
const ICONS: LucideIcon[] = [ShieldCheck, Fish, Phone, Bot];

/** Coerce the BE `status` string into one of the three card states. */
function normalizeState(status: string | null | undefined): CourseState {
  if (status === 'completed' || status === 'in_progress' || status === 'locked') return status;
  return 'locked';
}

/** Map a backend `CourseView` onto the card view model. */
function toCourse(dto: CourseDto, index: number, t: ReturnType<typeof useT>): Course {
  const state = normalizeState(dto.status);
  const progressPct = typeof dto.progress === 'number' ? Math.max(0, Math.min(100, dto.progress)) : 0;
  const lessons = dto.lessonCount ?? 0;
  const duration = dto.durationMin ?? 0;
  const meta = t('{lessons} bài · {duration} phút', { lessons, duration });

  const completed = state === 'completed';
  const inProgress = state === 'in_progress';

  let statusLabel = '';
  if (completed) statusLabel = t('Hoàn thành');
  else if (inProgress) statusLabel = t('{progressPct}% · Đang học', { progressPct });

  let ctaLabel = t('Chưa mở');
  if (completed) ctaLabel = t('Xem lại');
  else if (inProgress) ctaLabel = t('Học tiếp →');

  const iconBg = completed
    ? '#DDF3E6'
    : inProgress
      ? 'rgba(37,102,235,.08)'
      : 'var(--bg)';

  return {
    id: dto.id,
    title: dto.title ?? t('Khóa học'),
    meta,
    icon: ICONS[index % ICONS.length] ?? ShieldCheck,
    iconBg,
    state,
    progressPct,
    statusLabel,
    ctaLabel,
    ...(state === 'locked' ? { lockedNote: t('Hoàn thành các bài trước để mở khóa') } : {}),
  };
}

export default function CourseCatalogPage() {
  const navigate = useNavigate();
  const t = useT();
  const { data, isLoading, isError, refetch } = useCourses();

  const courses = (data ?? []).map((dto, index) => toCourse(dto, index, t));

  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <header style={{ marginBottom: 20 }}>
          <h1 style={pageTitle}>{t('Khóa học của bạn · Your Courses')}</h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>
            {t('Lộ trình: Nhân viên văn phòng')}
          </p>
        </header>

        {isLoading && <CatalogMessage>{t('Đang tải khóa học…')}</CatalogMessage>}

        {!isLoading && isError && (
          <CatalogMessage>
            <span style={{ color: 'var(--red)', fontWeight: 600 }}>{t('Không tải được khóa học. ')}</span>
            <button type="button" onClick={() => refetch()} style={inlineRetry}>
              {t('Thử lại')}
            </button>
          </CatalogMessage>
        )}

        {!isLoading && !isError && courses.length === 0 && (
          <CatalogMessage>{t('Chưa có khóa học nào.')}</CatalogMessage>
        )}

        {!isLoading && !isError && courses.length > 0 && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 14,
          }}
        >
          {courses.map((course) => {
            const Icon = course.icon;
            const locked = course.state === 'locked';
            const inProgress = course.state === 'in_progress';
            const completed = course.state === 'completed';

            const barTrack = completed ? '#DDF3E6' : 'var(--bg)';
            const barFill = completed ? 'var(--teal)' : 'var(--blue)';
            const statusColor = completed ? 'var(--teal)' : 'var(--blue)';

            return (
              <article
                key={course.id}
                onClick={inProgress ? () => navigate(`/learn/courses/${course.id}`) : undefined}
                style={{
                  background: 'var(--surface)',
                  border: inProgress ? '2px solid var(--blue)' : '1px solid var(--border)',
                  borderRadius: 12,
                  overflow: 'hidden',
                  opacity: locked ? 0.6 : 1,
                  cursor: inProgress ? 'pointer' : 'default',
                }}
              >
                <div
                  style={{
                    background: course.iconBg,
                    padding: 20,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Icon
                    size={32}
                    aria-hidden="true"
                    color={completed ? 'var(--teal)' : inProgress ? 'var(--blue)' : 'var(--muted)'}
                  />
                </div>
                <div style={{ padding: 16 }}>
                  <div
                    style={{
                      fontSize: 13.5,
                      fontWeight: 600,
                      color: 'var(--text)',
                      marginBottom: 6,
                    }}
                  >
                    {course.title}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 10 }}>
                    {course.meta}
                  </div>

                  {locked ? (
                    <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 12 }}>
                      {course.lockedNote}
                    </div>
                  ) : (
                    <>
                      <div
                        style={{
                          background: barTrack,
                          borderRadius: 999,
                          height: 5,
                          marginBottom: 10,
                          overflow: 'hidden',
                        }}
                        role="progressbar"
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={course.progressPct}
                        aria-label={`${course.title} progress`}
                      >
                        <div
                          style={{
                            background: barFill,
                            borderRadius: 999,
                            height: 5,
                            width: `${course.progressPct}%`,
                          }}
                        />
                      </div>
                      <div
                        style={{
                          fontSize: 12,
                          color: statusColor,
                          fontWeight: 600,
                          marginBottom: 10,
                        }}
                      >
                        {course.statusLabel}
                      </div>
                    </>
                  )}

                  <button
                    type="button"
                    disabled={locked}
                    onClick={(e) => {
                      e.stopPropagation();
                      if (locked) return;
                      navigate(`/learn/courses/${course.id}`);
                    }}
                    style={{
                      display: 'block',
                      width: '100%',
                      borderRadius: 8,
                      padding: 8,
                      textAlign: 'center',
                      fontSize: 13,
                      cursor: locked ? 'not-allowed' : 'pointer',
                      fontWeight: inProgress ? 600 : 500,
                      background: inProgress ? 'var(--blue)' : 'var(--bg)',
                      color: inProgress ? '#fff' : 'var(--muted)',
                      border: inProgress ? '1px solid var(--blue)' : '1px solid var(--border)',
                    }}
                  >
                    {course.ctaLabel}
                  </button>
                </div>
              </article>
            );
          })}
        </div>
        )}
    </div>
  );
}

function CatalogMessage({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: '40px 16px',
        textAlign: 'center',
        fontSize: 14,
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
  fontSize: 14,
  cursor: 'pointer',
  padding: 0,
};

const pageTitle: React.CSSProperties = {
  fontFamily: "'Space Grotesk', system-ui",
  fontSize: 22,
  fontWeight: 700,
  color: 'var(--text)',
  letterSpacing: '-0.02em',
  margin: '0 0 4px',
};
