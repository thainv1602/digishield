import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Check, X, ChevronDown } from 'lucide-react';
import { Button } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import type { AssessmentResult } from './api';

/**
 * QuizResultsPage — quiz score + answer review (`/learn/quiz/:id/results`).
 *
 * Score circle (80px, green/red tint) + points earned; answer-review accordion
 * (check/x icon + explanation for wrong answers); CTAs View Certificate +
 * Next Lesson.
 *
 * Reads the `AssessmentResult` produced by `POST /assessments/{id}/responses`
 * from router location state (set by {@link QuizPage} on submit). There is no GET
 * results endpoint, so if the page is opened directly (no state) it shows a
 * fallback prompt to take the quiz.
 */

interface ReviewRow {
  num: number;
  correct: boolean;
  explain: string;
}

interface ResultsState {
  result?: AssessmentResult;
}

const POINTS_PER_CORRECT = 20;

export default function QuizResultsPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as ResultsState | null) ?? {};
  const result = state.result;

  const [open, setOpen] = useState<Record<number, boolean>>({});

  // No GET endpoint for results — they only arrive via the submit response in
  // router state. If absent (e.g. direct navigation/refresh), prompt to retake.
  if (!result) {
    return (
      <div
        style={{
          animation: 'fadeUp .3s ease',
          maxWidth: 640,
          margin: '0 auto',
          padding: '48px 16px',
          textAlign: 'center',
        }}
      >
        <p style={{ fontSize: 14, color: 'var(--muted)', marginBottom: 16 }}>
          {t('Chưa có kết quả để hiển thị. Vui lòng làm bài kiểm tra.')}
        </p>
        <Button type="button" variant="primary" onClick={() => navigate(`/learn/quiz/${id ?? ''}`)}>
          {t('Làm bài kiểm tra')}
        </Button>
      </div>
    );
  }

  const total = result.total;
  const score = result.score;
  const passed = result.passed;
  const review: ReviewRow[] = result.review.map((r) => ({
    num: r.num,
    correct: r.correct,
    explain: r.explain ?? '',
  }));
  const pointsEarned = score * POINTS_PER_CORRECT;

  const tintBg = passed ? 'rgba(24,147,92,.12)' : 'rgba(221,59,64,.12)';
  const tintColor = passed ? 'var(--teal)' : 'var(--red)';
  const resultLabel = passed ? t('Đạt yêu cầu') : t('Chưa đạt');

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 640, margin: '0 auto' }}>
        {/* Score circle */}
        <section
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 16,
            padding: 36,
            textAlign: 'center',
            marginBottom: 20,
          }}
        >
          <div
            aria-hidden="true"
            style={{
              width: 80,
              height: 80,
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
              background: tintBg,
              fontSize: 36,
              fontWeight: 700,
              fontFamily: "'Space Grotesk', system-ui",
              color: tintColor,
            }}
          >
            {score}
          </div>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>
            {t('{score} / {total} câu đúng', { score, total })}
          </div>
          <div
            style={{
              fontFamily: "'Space Grotesk', system-ui",
              fontSize: 26,
              fontWeight: 700,
              color: tintColor,
              letterSpacing: '-0.01em',
              marginBottom: 16,
            }}
          >
            {resultLabel}
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 20,
            }}
          >
            <div>
              <div
                style={{
                  fontSize: 28,
                  fontWeight: 700,
                  color: 'var(--text)',
                  fontFamily: "'Space Grotesk', system-ui",
                }}
              >
                +{pointsEarned}
              </div>
              <div style={{ fontSize: 12, color: 'var(--muted)' }}>{t('điểm nhận được')}</div>
            </div>
            <div style={{ width: 1, height: 40, background: 'var(--border)' }} />
            <div>
              <div
                style={{
                  fontSize: 28,
                  fontWeight: 700,
                  color: 'var(--text)',
                  fontFamily: "'Space Grotesk', system-ui",
                }}
              >
                {score}/{total}
              </div>
              <div style={{ fontSize: 12, color: 'var(--muted)' }}>{t('câu đúng')}</div>
            </div>
          </div>
        </section>

        {/* Answer review accordion */}
        <section
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            padding: 20,
            marginBottom: 16,
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: 'var(--text)', margin: '0 0 16px' }}>
            {t('Xem lại đáp án')}
          </h2>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {review.map((r) => {
              const expandable = !r.correct;
              const isOpen = !!open[r.num];
              return (
                <li
                  key={r.num}
                  style={{
                    padding: '12px 0',
                    borderBottom: '1px solid var(--bg)',
                    display: 'flex',
                    gap: 12,
                    alignItems: 'flex-start',
                  }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: '50%',
                      background: r.correct ? '#DDF3E6' : '#FBE0DF',
                      color: r.correct ? '#0F7A4A' : '#BE2A2F',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    {r.correct ? <Check size={14} /> : <X size={14} />}
                  </span>
                  <div style={{ flex: 1 }}>
                    <button
                      type="button"
                      onClick={expandable ? () => setOpen((o) => ({ ...o, [r.num]: !o[r.num] })) : undefined}
                      aria-expanded={expandable ? isOpen : undefined}
                      style={{
                        all: 'unset',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        cursor: expandable ? 'pointer' : 'default',
                        fontSize: 12.5,
                        fontWeight: 600,
                        color: 'var(--text)',
                      }}
                    >
                      {t('Câu {num}', { num: r.num })}
                      {expandable && (
                        <ChevronDown
                          size={13}
                          aria-hidden="true"
                          style={{
                            transform: isOpen ? 'rotate(180deg)' : 'none',
                            transition: 'transform 150ms ease',
                            color: 'var(--muted)',
                          }}
                        />
                      )}
                    </button>
                    {expandable && isOpen && (
                      <div
                        style={{
                          fontSize: 12.5,
                          color: 'var(--muted)',
                          lineHeight: 1.5,
                          marginTop: 6,
                        }}
                      >
                        {r.explain}
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        </section>

        {/* CTAs */}
        <div style={{ display: 'flex', gap: 12 }}>
          <Button
            type="button"
            variant="secondary"
            fullWidth
            onClick={() => navigate('/certificates')}
          >
            {t('Xem chứng chỉ')}
          </Button>
          <Button
            type="button"
            variant="primary"
            fullWidth
            onClick={() => navigate('/learn/courses')}
          >
            {t('Học tiếp →')}
          </Button>
        </div>
    </div>
  );
}
