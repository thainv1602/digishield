import { useNavigate } from 'react-router-dom';
import { HelpCircle, Clock } from 'lucide-react';
import { useT } from '@/shared/i18n/i18nContext';
import { useLessons, type LessonSummary } from './api';

/**
 * QuizLandingPage — learner "Bài kiểm tra" entry (`/learn/quiz`).
 *
 * The sidebar shortcut used to point at a hard-coded `/learn/quiz/1`, whose id
 * is not a real lesson UUID, so the quiz always failed to load. Instead, list
 * the lessons that have a quiz (`GET /lessons`, `questionCount > 0`) and link
 * each to its quiz (`/learn/quiz/{lessonId}`).
 */
export default function QuizLandingPage() {
  const t = useT();
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = useLessons();
  const quizzes: LessonSummary[] = (data ?? []).filter((l) => l.questionCount > 0);

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 720 }}>
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
          {t('Bài kiểm tra')}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4 }}>
          {t('Chọn một bài kiểm tra để bắt đầu')}
        </div>
      </div>

      {isLoading && <Msg>{t('Đang tải bài kiểm tra…')}</Msg>}
      {!isLoading && isError && (
        <Msg>
          <span style={{ color: 'var(--color-red)', fontWeight: 600 }}>
            {t('Không tải được bài kiểm tra.')}{' '}
          </span>
          <button type="button" onClick={() => refetch()} style={retry}>
            {t('Thử lại')}
          </button>
        </Msg>
      )}
      {!isLoading && !isError && quizzes.length === 0 && (
        <Msg>{t('Chưa có bài kiểm tra nào.')}</Msg>
      )}

      {!isLoading && !isError && quizzes.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {quizzes.map((l) => (
            <button
              key={l.id}
              type="button"
              onClick={() => navigate(`/learn/quiz/${l.id}`)}
              style={{
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: 12,
                padding: 18,
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                cursor: 'pointer',
                textAlign: 'left',
                width: '100%',
              }}
            >
              <HelpCircle size={26} color="var(--color-blue)" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text)' }}>
                  {l.title ?? t('Bài kiểm tra')}
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
                  {l.courseTitle && <span>{l.courseTitle}</span>}
                  <span>{t('{n} câu hỏi', { n: l.questionCount })}</span>
                  {l.durationMin != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <Clock size={12} /> {l.durationMin}′
                    </span>
                  )}
                </div>
              </div>
              <span style={{ fontSize: 12.5, color: 'var(--color-blue)', fontWeight: 600 }}>
                {t('Bắt đầu')} →
              </span>
            </button>
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

const retry: React.CSSProperties = {
  all: 'unset',
  color: 'var(--color-blue)',
  cursor: 'pointer',
  fontWeight: 600,
};
