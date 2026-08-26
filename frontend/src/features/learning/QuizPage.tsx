import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { useToast } from '@/shared/ui';
import { useQuiz, useSubmitResponses, type QuizQuestion } from './api';
import { useT } from '@/shared/i18n/i18nContext';

/**
 * QuizPage — interactive quiz (`/learn/quiz/:id`).
 *
 * Replicates the design handoff interactivity exactly:
 *  - Question number + red countdown timer pill (JetBrains Mono)
 *  - Progress bar (answered / total)
 *  - Question text (Space Grotesk 18px 600)
 *  - 4 options labeled A/B/C/D (28px circle) + text rows
 *    selected = rgba(37,102,235,.06) bg + 2px blue border
 *  - Bottom prev/next + numbered DOT nav
 *    (green = answered, blue = current, gray = unanswered)
 *  - Submit validates all answered, then POSTs and navigates to results
 *
 * Questions come from the live backend via `useQuiz(id)`
 * (`GET /lessons/{id}/quiz`). Submit POSTs to `/assessments/{id}/responses`
 * via `useSubmitResponses(id)`; the returned `AssessmentResult` is passed to the
 * results screen through router state.
 */

const OPTION_LETTERS = ['A', 'B', 'C', 'D'];
const QUIZ_SECONDS = 6 * 60 + 21; // 06:21 starting countdown

function formatTime(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export default function QuizPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const t = useT();

  const { data: quiz, isLoading, isError, refetch } = useQuiz(id);
  const submitMutation = useSubmitResponses(id);

  const questions: QuizQuestion[] = useMemo(() => quiz?.questions ?? [], [quiz]);
  const total = questions.length;

  const [current, setCurrent] = useState(0);
  // answers: question index -> selected option index
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [secondsLeft, setSecondsLeft] = useState(QUIZ_SECONDS);

  // Countdown timer.
  useEffect(() => {
    if (secondsLeft <= 0) return;
    const t = setInterval(() => setSecondsLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(t);
  }, [secondsLeft]);

  const answeredCount = useMemo(() => Object.keys(answers).length, [answers]);
  const progressPct = total > 0 ? Math.round((answeredCount / total) * 100) : 0;
  const question = questions[current];
  const isFirst = current === 0;
  const isLast = current === total - 1;
  const allAnswered = total > 0 && answeredCount === total;

  function selectOption(optionIndex: number) {
    setAnswers((prev) => ({ ...prev, [current]: optionIndex }));
  }

  function goTo(index: number) {
    setCurrent(Math.max(0, Math.min(total - 1, index)));
  }

  function handleSubmit() {
    if (!allAnswered) {
      // Validation: jump to first unanswered question.
      const firstUnanswered = questions.findIndex((_, i) => answers[i] == null);
      toast(t('Vui lòng trả lời tất cả câu hỏi trước khi nộp bài.'));
      if (firstUnanswered >= 0) setCurrent(firstUnanswered);
      return;
    }
    if (!id) return;
    // Build the BE payload: questionId -> selected option index.
    const payload: Record<string, number> = {};
    questions.forEach((q, i) => {
      const selected = answers[i];
      if (selected != null) payload[q.id] = selected;
    });
    submitMutation.mutate(payload, {
      onSuccess: (result) => {
        navigate(`/learn/quiz/${id}/results`, { state: { result } });
      },
      onError: () => {
        toast(t('Không thể nộp bài. Vui lòng thử lại.'));
      },
    });
  }

  if (isLoading) {
    return <StatusBlock>{t('Đang tải bài kiểm tra…')}</StatusBlock>;
  }
  if (isError || !quiz || total === 0 || !question) {
    return (
      <StatusBlock>
        {isError || !quiz ? (
          <>
            <span style={{ color: 'var(--red)', fontWeight: 600 }}>
              {t('Không tải được bài kiểm tra.')}{' '}
            </span>
            <button
              type="button"
              onClick={() => refetch()}
              style={{ all: 'unset', color: 'var(--blue)', cursor: 'pointer', fontWeight: 600 }}
            >
              {t('Thử lại')}
            </button>
          </>
        ) : (
          t('Bài kiểm tra này chưa có câu hỏi.')
        )}
      </StatusBlock>
    );
  }

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 720, margin: '0 auto' }}>
        {/* Question number + timer */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 16,
          }}
        >
          <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--muted)' }}>
            {t('Câu {a} / {b}', { a: current + 1, b: total })}
          </div>
          <div
            role="timer"
            aria-label={t('Thời gian còn lại')}
            style={{
              background: '#FBE0DF',
              borderRadius: 999,
              padding: '5px 14px',
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--red)',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              fontFamily: "'JetBrains Mono', monospace",
            }}
          >
            <Clock size={13} aria-hidden="true" />
            {formatTime(secondsLeft)}
          </div>
        </div>

        {/* Progress bar */}
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progressPct}
          aria-label={t('Tiến độ bài kiểm tra')}
          style={{
            background: 'var(--bg)',
            borderRadius: 999,
            height: 5,
            marginBottom: 28,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              background: 'var(--blue)',
              borderRadius: 999,
              height: 5,
              width: `${progressPct}%`,
              transition: 'width 160ms ease',
            }}
          />
        </div>

        {/* Question card */}
        <div
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 16,
            padding: 32,
            marginBottom: 20,
          }}
        >
          <h1
            style={{
              fontFamily: "'Space Grotesk', system-ui",
              fontSize: 18,
              fontWeight: 600,
              color: 'var(--text)',
              lineHeight: 1.4,
              margin: '0 0 28px',
              letterSpacing: '-0.01em',
            }}
          >
            {question.q}
          </h1>

          <div
            role="radiogroup"
            aria-label={t('Đáp án')}
            style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
          >
            {question.options.map((opt, i) => {
              const selected = answers[current] === i;
              return (
                <div
                  key={i}
                  role="radio"
                  aria-checked={selected}
                  tabIndex={0}
                  onClick={() => selectOption(i)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      selectOption(i);
                    }
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 14px',
                    borderRadius: 10,
                    cursor: 'pointer',
                    color: 'var(--text)',
                    background: selected ? 'rgba(37,102,235,.06)' : 'transparent',
                    border: selected
                      ? '2px solid var(--blue)'
                      : '2px solid transparent',
                  }}
                >
                  <div
                    aria-hidden="true"
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: '50%',
                      background: selected ? 'var(--blue)' : 'var(--bg)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 12,
                      fontWeight: 700,
                      color: selected ? '#fff' : 'var(--muted)',
                      flexShrink: 0,
                      fontFamily: "'Space Grotesk', system-ui",
                    }}
                  >
                    {OPTION_LETTERS[i]}
                  </div>
                  <span style={{ fontSize: 14, lineHeight: 1.5 }}>{opt}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Bottom nav: prev / dots / next-or-submit */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {isFirst ? (
            <div style={{ flex: 1 }} />
          ) : (
            <button
              type="button"
              onClick={() => goTo(current - 1)}
              style={navBtn('secondary')}
            >
              ← {t('Câu trước')}
            </button>
          )}

          {/* Dot navigation */}
          <div style={{ display: 'flex', gap: 6 }} role="group" aria-label={t('Chuyển câu')}>
            {questions.map((_, i) => {
              const isCurrent = i === current;
              const isAnswered = answers[i] != null;
              const bg = isCurrent
                ? 'var(--blue)'
                : isAnswered
                  ? 'var(--teal)'
                  : 'var(--bg)';
              const textColor = isCurrent || isAnswered ? '#fff' : 'var(--muted)';
              return (
                <button
                  key={i}
                  type="button"
                  onClick={() => goTo(i)}
                  aria-current={isCurrent ? 'true' : undefined}
                  aria-label={
                    isAnswered
                      ? t('Câu {n}, đã trả lời', { n: i + 1 })
                      : t('Câu {n}, chưa trả lời', { n: i + 1 })
                  }
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 11.5,
                    fontWeight: 700,
                    cursor: 'pointer',
                    border: 'none',
                    background: bg,
                    color: textColor,
                  }}
                >
                  {i + 1}
                </button>
              );
            })}
          </div>

          {isLast ? (
            <button
              type="button"
              onClick={handleSubmit}
              disabled={submitMutation.isPending}
              style={{
                ...navBtn('submit'),
                ...(submitMutation.isPending ? { opacity: 0.6, cursor: 'wait' } : {}),
              }}
            >
              {submitMutation.isPending ? t('Đang nộp…') : t('Nộp bài')}
            </button>
          ) : (
            <button type="button" onClick={() => goTo(current + 1)} style={navBtn('primary')}>
              {t('Câu tiếp')} →
            </button>
          )}
        </div>

        <p
          style={{
            textAlign: 'center',
            marginTop: 12,
            fontSize: 12.5,
            color: 'var(--muted)',
          }}
          aria-live="polite"
        >
          {t('Đã trả lời {a} / {b} câu', { a: answeredCount, b: total })}
        </p>
    </div>
  );
}

function StatusBlock({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        animation: 'fadeUp .3s ease',
        maxWidth: 720,
        margin: '0 auto',
        padding: '48px 16px',
        textAlign: 'center',
        fontSize: 14,
        color: 'var(--muted)',
      }}
    >
      {children}
    </div>
  );
}

function navBtn(kind: 'primary' | 'secondary' | 'submit'): React.CSSProperties {
  const base: React.CSSProperties = {
    flex: 1,
    borderRadius: 9,
    padding: 10,
    textAlign: 'center',
    fontSize: 14,
    cursor: 'pointer',
  };
  if (kind === 'secondary') {
    return {
      ...base,
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      color: 'var(--text)',
      fontWeight: 500,
    };
  }
  if (kind === 'submit') {
    return {
      ...base,
      background: 'var(--teal)',
      border: 'none',
      color: '#fff',
      fontWeight: 600,
    };
  }
  return {
    ...base,
    background: 'var(--blue)',
    border: 'none',
    color: '#fff',
    fontWeight: 600,
  };
}
