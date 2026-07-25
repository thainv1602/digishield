import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, PlayCircle, HelpCircle, Clock } from 'lucide-react';
import { useT } from '@/shared/i18n/I18nProvider';
import { useCourses, useLessons, type LessonSummary } from './api';

/**
 * CourseDetailPage — a course's lessons (`/learn/courses/:id`).
 *
 * The course catalog linked cards to `/learn/courses/{id}`, which had no route
 * and 404'd. This lists the course's lessons (`GET /lessons` filtered by
 * `courseId`); each opens the lesson player (`/learn/lessons/{lessonId}`), and
 * lessons with a quiz also offer a shortcut to `/learn/quiz/{lessonId}`.
 */
export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const t = useT();

  const coursesQ = useCourses();
  const lessonsQ = useLessons();

  const course = (coursesQ.data ?? []).find((c) => c.id === id);
  const lessons: LessonSummary[] = (lessonsQ.data ?? []).filter((l) => l.courseId === id);

  const isLoading = coursesQ.isLoading || lessonsQ.isLoading;
  const isError = coursesQ.isError || lessonsQ.isError;

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 720 }}>
      <button
        type="button"
        onClick={() => navigate('/learn/courses')}
        style={{
          all: 'unset',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 13,
          color: 'var(--color-muted)',
          cursor: 'pointer',
          marginBottom: 16,
        }}
      >
        <ArrowLeft size={15} /> {t('Khóa học')}
      </button>

      <div style={{ marginBottom: 24 }}>
        <div
          style={{
            fontFamily: "'Space Grotesk', system-ui",
            fontSize: 22,
            fontWeight: 700,
            color: 'var(--color-text)',
            letterSpacing: '-.02em',
          }}
        >
          {course?.title ?? t('Khóa học')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
          {t('Danh sách bài học')}
        </div>
      </div>

      {isLoading && <Msg>{t('Đang tải khóa học…')}</Msg>}
      {!isLoading && isError && (
        <Msg>
          <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
            {t('Không tải được khóa học. ')}
          </span>
          <button
            type="button"
            onClick={() => {
              coursesQ.refetch();
              lessonsQ.refetch();
            }}
            style={retry}
          >
            {t('Thử lại')}
          </button>
        </Msg>
      )}
      {!isLoading && !isError && lessons.length === 0 && (
        <Msg>{t('Khóa học này chưa có bài học nào.')}</Msg>
      )}

      {!isLoading && !isError && lessons.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {lessons.map((l) => (
            <div
              key={l.id}
              style={{
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: 12,
                padding: 18,
                display: 'flex',
                alignItems: 'center',
                gap: 14,
              }}
            >
              <PlayCircle size={26} color="var(--color-blue)" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                  {l.title ?? t('Bài học')}
                </div>
                <div
                  style={{
                    fontSize: 12.5,
                    color: 'var(--color-muted)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    marginTop: 2,
                  }}
                >
                  {l.durationMin != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <Clock size={12} /> {l.durationMin}′
                    </span>
                  )}
                  {l.questionCount > 0 && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <HelpCircle size={12} /> {t('{n} câu hỏi', { n: l.questionCount })}
                    </span>
                  )}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                {l.questionCount > 0 && (
                  <button
                    type="button"
                    onClick={() => navigate(`/learn/quiz/${l.id}`)}
                    style={btn('ghost')}
                  >
                    {t('Kiểm tra')}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => navigate(`/learn/lessons/${l.id}`)}
                  style={btn('primary')}
                >
                  {t('Học')} →
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function Msg({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 12,
        padding: '28px 20px',
        textAlign: 'center',
        fontSize: 13.5,
        color: 'var(--color-muted)',
      }}
    >
      {children}
    </div>
  );
}

function btn(kind: 'primary' | 'ghost'): React.CSSProperties {
  const base: React.CSSProperties = {
    borderRadius: 8,
    padding: '7px 12px',
    fontSize: 12.5,
    fontWeight: 600,
    cursor: 'pointer',
  };
  if (kind === 'ghost') {
    return {
      ...base,
      background: 'var(--color-bg)',
      color: 'var(--color-text)',
      border: '1px solid var(--color-border)',
    };
  }
  return {
    ...base,
    background: 'var(--color-blue)',
    color: '#fff',
    border: '1px solid var(--color-blue)',
  };
}

const retry: React.CSSProperties = {
  all: 'unset',
  color: 'var(--color-blue)',
  cursor: 'pointer',
  fontWeight: 600,
};
