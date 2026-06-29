import { useMutation, useQuery } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Thin, hand-written typed fetchers + hooks for the learning catalog.
 *
 * Mirrors `CourseView` from the learning module (`GET /courses`). `CourseView`
 * is a plain record (no `@JsonProperty`), so Jackson serializes it as camelCase.
 */

/** Catalog state derived by the BE. */
export type CourseStatus = 'completed' | 'in_progress' | 'locked' | string;

export interface Course {
  id: string;
  tenantId: string;
  title: string | null;
  level: string | null;
  lang: string | null;
  durationMin: number | null;
  lessonCount: number | null;
  progress: number | null;
  status: CourseStatus | null;
}

/** GET /courses — course catalog for the current tenant. */
export function fetchCourses(signal?: AbortSignal): Promise<Course[]> {
  return apiRequest<Course[]>({
    url: '/courses',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link CourseCatalogPage}. */
export function useCourses() {
  return useQuery({
    queryKey: queryKeys.courses,
    queryFn: ({ signal }) => fetchCourses(signal),
  });
}

// ---- Enrollments (Learner Portal) -----------------------------------------

/**
 * Mirrors `EnrollmentView` (`GET /enrollments`). Plain record → camelCase.
 * `status` is lower-case: assigned | in_progress | completed | overdue.
 */
export interface Enrollment {
  id: string;
  tenantId: string;
  userId: string;
  courseId: string;
  courseTitle: string | null;
  status: string | null;
  progress: number | null;
  score: number | null;
}

/** GET /enrollments — the tenant's enrollments (learner progress). */
export function fetchEnrollments(
  status?: string,
  signal?: AbortSignal,
): Promise<Enrollment[]> {
  return apiRequest<Enrollment[]>({
    url: '/enrollments',
    method: 'GET',
    ...(status ? { params: { status } } : {}),
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link LearnerPortalPage}. */
export function useEnrollments(status?: string) {
  return useQuery({
    queryKey: queryKeys.enrollments(status),
    queryFn: ({ signal }) => fetchEnrollments(status, signal),
  });
}

// ---- Gamification (Learner Portal) ----------------------------------------

/** Mirrors `LeaderboardRowView` (`GET /gamification/leaderboard`). */
export interface LeaderboardRow {
  rank: number;
  name: string;
  points: number;
}

/** GET /gamification/leaderboard — tenant leaderboard. */
export function fetchLeaderboard(signal?: AbortSignal): Promise<LeaderboardRow[]> {
  return apiRequest<LeaderboardRow[]>({
    url: '/gamification/leaderboard',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook for the leaderboard. */
export function useLeaderboard() {
  return useQuery({
    queryKey: queryKeys.leaderboard,
    queryFn: ({ signal }) => fetchLeaderboard(signal),
  });
}

/** Mirrors `BadgeView` (`GET /users/{id}/badges`). */
export interface Badge {
  id: string;
  name: string;
  description: string | null;
  iconRef: string | null;
  earned: boolean;
  awardedAt: string | null;
}

/** GET /users/{id}/badges — a user's gamification badges. */
export function fetchUserBadges(userId: string, signal?: AbortSignal): Promise<Badge[]> {
  return apiRequest<Badge[]>({
    url: `/users/${userId}/badges`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook for a user's badges (enabled only when userId is known). */
export function useUserBadges(userId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.userBadges(userId ?? 'none'),
    queryFn: ({ signal }) => fetchUserBadges(userId as string, signal),
    enabled: Boolean(userId),
  });
}

/** Shape of `GET /users/{id}/points` → `{ total, entries }`. */
export interface UserPoints {
  total: number;
  entries: unknown[];
}

/** GET /users/{id}/points — a user's accumulated points. */
export function fetchUserPoints(userId: string, signal?: AbortSignal): Promise<UserPoints> {
  return apiRequest<UserPoints>({
    url: `/users/${userId}/points`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook for a user's points (enabled only when userId is known). */
export function useUserPoints(userId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.userPoints(userId ?? 'none'),
    queryFn: ({ signal }) => fetchUserPoints(userId as string, signal),
    enabled: Boolean(userId),
  });
}

// ---- Lesson (Lesson Player) -----------------------------------------------

/** A single checkpoint in {@link Lesson.checkpoints}. `state`: done|current|todo. */
export interface LessonCheckpoint {
  label: string;
  state: string;
}

/** Mirrors `LessonView` (`GET /lessons/{id}`). Plain record → camelCase. */
export interface Lesson {
  id: string;
  courseId: string;
  title: string | null;
  body: string | null;
  exampleTitle: string | null;
  example: string | null;
  closing: string | null;
  durationMin: number | null;
  progressPct: number;
  checkpoints: LessonCheckpoint[] | null;
}

/** GET /lessons/{id} — lesson content + checkpoint outline. */
export function fetchLesson(id: string, signal?: AbortSignal): Promise<Lesson> {
  return apiRequest<Lesson>({
    url: `/lessons/${id}`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link LessonPlayerPage}. */
export function useLesson(id: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.lesson(id ?? 'none'),
    queryFn: ({ signal }) => fetchLesson(id as string, signal),
    enabled: Boolean(id),
  });
}

// ---- Quiz (Quiz screen) ----------------------------------------------------

/** Mirrors `QuizView.QuizQuestionView`. `correct` is the 0-based answer index. */
export interface QuizQuestion {
  id: string;
  q: string;
  options: string[];
  correct: number;
  explain: string | null;
}

/** Mirrors `QuizView` (`GET /lessons/{id}/quiz`). */
export interface Quiz {
  lessonId: string;
  questions: QuizQuestion[];
}

/** GET /lessons/{id}/quiz — quiz questions for a lesson. */
export function fetchQuiz(id: string, signal?: AbortSignal): Promise<Quiz> {
  return apiRequest<Quiz>({
    url: `/lessons/${id}/quiz`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link QuizPage}. */
export function useQuiz(id: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.quiz(id ?? 'none'),
    queryFn: ({ signal }) => fetchQuiz(id as string, signal),
    enabled: Boolean(id),
  });
}

// ---- Assessment submit (Quiz Results) -------------------------------------

/** A single answer-review row from `AssessmentResultView.ReviewRow`. */
export interface AssessmentReviewRow {
  num: number;
  correct: boolean;
  explain: string | null;
}

/** Mirrors `AssessmentResultView` (POST /assessments/{id}/responses). */
export interface AssessmentResult {
  score: number;
  total: number;
  passed: boolean;
  review: AssessmentReviewRow[];
}

/** Request body for the quiz submit: questionId/key -> selected option index. */
export type SubmitResponsesBody = { answers: Record<string, number> };

/** POST /assessments/{id}/responses — submit quiz answers, returns the result. */
export function submitResponses(
  id: string,
  answers: Record<string, number>,
): Promise<AssessmentResult> {
  return apiRequest<AssessmentResult>({
    url: `/assessments/${id}/responses`,
    method: 'POST',
    data: { answers } satisfies SubmitResponsesBody,
  });
}

/** Mutation hook powering the quiz submit in {@link QuizPage}. */
export function useSubmitResponses(id: string | null | undefined) {
  return useMutation({
    mutationFn: (answers: Record<string, number>) => submitResponses(id as string, answers),
  });
}

// ---- Certificate (Certificate screen) -------------------------------------

/** Mirrors `CertificateView` (`GET /certificates/{id}`). Plain record → camelCase. */
export interface Certificate {
  id: string;
  serial: string | null;
  courseTitle: string | null;
  recipient: string | null;
  score: number | null;
  issuedAt: string | null;
  validUntil: string | null;
  verifyUrl: string | null;
  qr: string | null;
}

/** GET /certificates/{id} — certificate detail. */
export function fetchCertificate(id: string, signal?: AbortSignal): Promise<Certificate> {
  return apiRequest<Certificate>({
    url: `/certificates/${id}`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link CertificatePage}. */
export function useCertificate(id: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.certificate(id ?? 'none'),
    queryFn: ({ signal }) => fetchCertificate(id as string, signal),
    enabled: Boolean(id),
  });
}

// ---- Report phishing (learner CTA) ----------------------------------------

/** Channel a learner reports a scam from. */
export type ReportChannel = 'email' | 'sms';

/** Body for `POST /reports/phishing` — the suspicious content + its channel. */
export interface ReportPhishingInput {
  /** Email/SMS content, headers or link. */
  payload: string;
  channel?: ReportChannel;
}

/** POST /reports/phishing — a learner reports a suspected scam. */
export function reportPhishing({ payload, channel = 'email' }: ReportPhishingInput): Promise<unknown> {
  return apiRequest<unknown>({
    url: '/reports/phishing',
    method: 'POST',
    data: { payload, channel },
  });
}

/** Mutation hook powering the "Báo cáo lừa đảo" CTA in {@link LearnerPortalPage}. */
export function useReportPhishing() {
  return useMutation({
    mutationFn: reportPhishing,
  });
}
