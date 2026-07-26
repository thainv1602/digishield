import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useLesson } from './api';

/**
 * LessonPlayerPage — single lesson view (`/learn/lessons/:id`).
 *
 * Full-width top progress bar; 2-col layout (main content area + sidebar outline
 * with checkpoints); Prev/Next navigation. Uses useParams() for the lesson id.
 *
 * Data comes from the live backend via `useLesson(id)` (`GET /lessons/{id}`).
 * Loading/error states handled below.
 */

type CheckpointState = 'done' | 'current' | 'todo';

interface Checkpoint {
  id: string;
  label: string;
  state: CheckpointState;
}

function normalizeCpState(value: string | null | undefined): CheckpointState {
  return value === 'done' || value === 'current' ? value : 'todo';
}

export default function LessonPlayerPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const t = useT();
  const { data: lesson, isLoading, isError, refetch } = useLesson(id);

  if (isLoading) {
    return <StatusBlock>{t('Đang tải bài học…')}</StatusBlock>;
  }
  if (isError || !lesson) {
    return (
      <StatusBlock>
        <span style={{ color: 'var(--red)', fontWeight: 600 }}>{t('Không tải được bài học. ')}</span>
        <button
          type="button"
          onClick={() => refetch()}
          style={{ all: 'unset', color: 'var(--blue)', cursor: 'pointer', fontWeight: 600 }}
        >
          {t('Thử lại')}
        </button>
      </StatusBlock>
    );
  }

  const progressPct = lesson.progressPct ?? 0;
  const checkpoints: Checkpoint[] = (lesson.checkpoints ?? []).map((cp, i) => ({
    id: `cp${i}`,
    label: cp.label,
    state: normalizeCpState(cp.state),
  }));

  return (
    <div style={{ animation: 'fadeUp .3s ease', maxWidth: 900 }}>
        {/* Full-width progress bar */}
        <div
          style={{
            background: 'var(--bg)',
            borderRadius: 999,
            height: 5,
            marginBottom: 20,
            overflow: 'hidden',
          }}
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progressPct}
          aria-label={t('Tiến độ bài học')}
        >
          <div
            style={{
              background: 'var(--blue)',
              borderRadius: 999,
              height: 5,
              width: `${progressPct}%`,
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: 24 }}>
          {/* Main content */}
          <div style={{ flex: 1 }}>
            <article
              style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 12,
                overflow: 'hidden',
                marginBottom: 16,
              }}
            >
              <div
                style={{
                  background: 'var(--navy)',
                  aspectRatio: '16 / 9',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--muted)',
                  fontSize: 14,
                }}
              >
                {t('[ Hình minh họa / Video nội dung ]')}
              </div>
              <div style={{ padding: 24 }}>
                <h1
                  style={{
                    fontFamily: "'Space Grotesk', system-ui",
                    fontSize: 18,
                    fontWeight: 700,
                    color: 'var(--text)',
                    letterSpacing: '-0.01em',
                    margin: '0 0 16px',
                  }}
                >
                  {lesson.title}
                </h1>
                {lesson.body && <p style={bodyText}>{lesson.body}</p>}
                {(lesson.exampleTitle || lesson.example) && (
                  <div
                    style={{
                      background: '#FBE0DF',
                      borderLeft: '3px solid var(--red)',
                      borderRadius: '0 8px 8px 0',
                      padding: '12px 16px',
                      margin: '14px 0',
                    }}
                  >
                    {lesson.exampleTitle && (
                      <div
                        style={{
                          fontSize: 13,
                          fontWeight: 600,
                          color: '#BE2A2F',
                          marginBottom: 4,
                        }}
                      >
                        {lesson.exampleTitle}
                      </div>
                    )}
                    {lesson.example && (
                      <div style={{ fontSize: 13, color: '#394559', lineHeight: 1.5 }}>
                        {lesson.example}
                      </div>
                    )}
                  </div>
                )}
                {lesson.closing && <p style={bodyText}>{lesson.closing}</p>}
              </div>
            </article>

            {/* Prev / Next nav */}
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Button type="button" variant="secondary" onClick={() => navigate(-1)}>
                {t('← Bài trước')}
              </Button>
              <Button
                type="button"
                variant="primary"
                onClick={() => id && navigate(`/learn/quiz/${id}`)}
              >
                {t('Bài tiếp → Thực hành')}
              </Button>
            </div>
          </div>

          {/* Sidebar outline */}
          <aside style={{ width: 200, flexShrink: 0 }}>
            <nav
              aria-label={t('Trong bài này')}
              style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 12,
                padding: 16,
                position: 'sticky',
                top: 0,
              }}
            >
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: 'var(--muted)',
                  marginBottom: 12,
                  letterSpacing: '.06em',
                  textTransform: 'uppercase',
                }}
              >
                {t('Trong bài này')}
              </div>
              <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 8 }}>
                {checkpoints.map((cp) => {
                  const done = cp.state === 'done';
                  const current = cp.state === 'current';
                  const color = done
                    ? 'var(--teal)'
                    : current
                      ? 'var(--blue)'
                      : 'var(--muted)';
                  return (
                    <li
                      key={cp.id}
                      aria-current={current ? 'step' : undefined}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        fontSize: 13,
                        color,
                        fontWeight: current ? 500 : 400,
                      }}
                    >
                      <span
                        aria-hidden="true"
                        style={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          flexShrink: 0,
                          background: done || current ? color : 'transparent',
                          border: done || current ? 'none' : '1.5px solid #C0CBDC',
                        }}
                      />
                      {cp.label}
                    </li>
                  );
                })}
              </ol>
            </nav>
          </aside>
        </div>
    </div>
  );
}

const bodyText: React.CSSProperties = {
  fontSize: 14,
  color: '#394559',
  lineHeight: 1.7,
  margin: '0 0 14px',
};

function StatusBlock({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        animation: 'fadeUp .3s ease',
        maxWidth: 900,
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
