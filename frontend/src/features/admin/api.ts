import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';

/**
 * Thin, hand-written typed fetchers + hooks for the Admin-system pages
 * (Org Settings, Gamification, AIDA / AI orchestration).
 *
 * Types mirror the backend records exactly. The records use no `@JsonProperty`,
 * so Jackson serializes the component names verbatim (camelCase).
 */

// ---------------------------------------------------------------------------
// Org Settings — tenancy module
// ---------------------------------------------------------------------------

/** Mirrors `ScimConfigView` (`GET /tenants/{tenantId}/settings`). */
export interface TenantSettings {
  tenantId: string;
  idpName: string | null;
  connected: boolean;
  idpTenantId: string | null;
  clientId: string | null;
  scimEndpoint: string | null;
  lastSyncAt: string | null;
  syncedUserCount: number | null;
  syncErrorCount: number | null;
  syncStatus: string | null;
}

/** Mirrors `FeatureFlagView` (`GET /tenants/{tenantId}/feature-flags`). */
export interface FeatureFlag {
  key: string;
  enabled: boolean;
}

/** GET /tenants/{tenantId}/settings — tenant SCIM/SSO + org config view. */
export function fetchTenantSettings(
  tenantId: string,
  signal?: AbortSignal,
): Promise<TenantSettings> {
  return apiRequest<TenantSettings>({
    url: `/tenants/${tenantId}/settings`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** GET /tenants/{tenantId}/feature-flags — tenant feature flags. */
export function fetchFeatureFlags(
  tenantId: string,
  signal?: AbortSignal,
): Promise<FeatureFlag[]> {
  return apiRequest<FeatureFlag[]>({
    url: `/tenants/${tenantId}/feature-flags`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link OrgSettingsPage} (settings card). */
export function useTenantSettings(tenantId: string = DEMO_TENANT_ID) {
  return useQuery({
    queryKey: queryKeys.tenantSettings(tenantId),
    queryFn: ({ signal }) => fetchTenantSettings(tenantId, signal),
  });
}

/** TanStack Query hook powering {@link OrgSettingsPage} (feature flags). */
export function useFeatureFlags(tenantId: string = DEMO_TENANT_ID) {
  return useQuery({
    queryKey: queryKeys.featureFlags(tenantId),
    queryFn: ({ signal }) => fetchFeatureFlags(tenantId, signal),
  });
}

// ---------------------------------------------------------------------------
// Gamification — learning module
// ---------------------------------------------------------------------------

/** Mirrors `LeaderboardRowView` (`GET /gamification/leaderboard`). */
export interface LeaderboardRow {
  rank: number;
  name: string;
  points: number;
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

/** Shape of `GET /users/{id}/points` ({ total, entries }). */
export interface UserPoints {
  total: number;
  entries: unknown[];
}

/** GET /gamification/leaderboard — current tenant's leaderboard rows. */
export function fetchLeaderboard(signal?: AbortSignal): Promise<LeaderboardRow[]> {
  return apiRequest<LeaderboardRow[]>({
    url: '/gamification/leaderboard',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** GET /users/{id}/badges — gamification badges of a user. */
export function fetchUserBadges(userId: string, signal?: AbortSignal): Promise<Badge[]> {
  return apiRequest<Badge[]>({
    url: `/users/${userId}/badges`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** GET /users/{id}/points — total points of a user. */
export function fetchUserPoints(userId: string, signal?: AbortSignal): Promise<UserPoints> {
  return apiRequest<UserPoints>({
    url: `/users/${userId}/points`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

export function useLeaderboard() {
  return useQuery({
    queryKey: queryKeys.leaderboard,
    queryFn: ({ signal }) => fetchLeaderboard(signal),
  });
}

export function useUserBadges(userId: string) {
  return useQuery({
    queryKey: queryKeys.userBadges(userId),
    queryFn: ({ signal }) => fetchUserBadges(userId, signal),
  });
}

// ---- Badge catalog (tenant badge definitions) -----------------------------

const BADGE_CATALOG_KEY = ['gamification', 'badge-catalog'] as const;

/** Mirrors `BadgeCatalogView` (`/gamification/badges`). */
export interface BadgeCatalog {
  id: string;
  name: string;
  description: string | null;
  iconRef: string | null;
}

/** Create/edit payload for a badge definition. */
export interface BadgeCatalogInput {
  name: string;
  description?: string | null;
  iconRef?: string | null;
}

/** GET /gamification/badges — the tenant's badge catalog. */
export function fetchBadgeCatalog(signal?: AbortSignal): Promise<BadgeCatalog[]> {
  return apiRequest<BadgeCatalog[]>({
    url: '/gamification/badges',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** POST /gamification/badges — create a badge definition. */
export function createBadgeCatalog(body: BadgeCatalogInput): Promise<BadgeCatalog> {
  return apiRequest<BadgeCatalog>({ url: '/gamification/badges', method: 'POST', data: body });
}

/** DELETE /gamification/badges/{id} — remove a badge definition. */
export function deleteBadgeCatalog(id: string): Promise<void> {
  return apiRequest<void>({ url: `/gamification/badges/${id}`, method: 'DELETE' });
}

export function useBadgeCatalog() {
  return useQuery({
    queryKey: BADGE_CATALOG_KEY,
    queryFn: ({ signal }) => fetchBadgeCatalog(signal),
  });
}

export function useCreateBadge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createBadgeCatalog,
    onSuccess: () => qc.invalidateQueries({ queryKey: BADGE_CATALOG_KEY }),
  });
}

export function useDeleteBadge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteBadgeCatalog,
    onSuccess: () => qc.invalidateQueries({ queryKey: BADGE_CATALOG_KEY }),
  });
}

export function useUserPoints(userId: string) {
  return useQuery({
    queryKey: queryKeys.userPoints(userId),
    queryFn: ({ signal }) => fetchUserPoints(userId, signal),
  });
}

// ---------------------------------------------------------------------------
// Gamification point rules — learning module
// ---------------------------------------------------------------------------

/** Mirrors `PointRuleView` (`GET /gamification/point-rules`). */
export interface PointRule {
  action: string;
  label: string;
  points: number;
}

/** GET /gamification/point-rules — points awarded/deducted per action. */
export function fetchPointRules(signal?: AbortSignal): Promise<PointRule[]> {
  return apiRequest<PointRule[]>({
    url: '/gamification/point-rules',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** Query hook for the point-rules panel in {@link GamificationPage}. */
export function usePointRules() {
  return useQuery({
    queryKey: queryKeys.pointRules,
    queryFn: ({ signal }) => fetchPointRules(signal),
  });
}

// ---------------------------------------------------------------------------
// Business thresholds (org-settings) — tenancy module
// ---------------------------------------------------------------------------

/** Mirrors `BusinessThresholdsView` (snake_case wire shape). */
export interface BusinessThresholds {
  risk_alert_score: number;
  pass_score_pct: number;
  min_campaigns_per_quarter: number;
}

/** GET /tenants/{id}/thresholds — the tenant's business thresholds. */
export function fetchThresholds(tenantId: string, signal?: AbortSignal): Promise<BusinessThresholds> {
  return apiRequest<BusinessThresholds>({
    url: `/tenants/${tenantId}/thresholds`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** PATCH /tenants/{id}/thresholds — update the tenant's business thresholds. */
export function patchThresholds(
  tenantId: string,
  body: BusinessThresholds,
): Promise<BusinessThresholds> {
  return apiRequest<BusinessThresholds>({
    url: `/tenants/${tenantId}/thresholds`,
    method: 'PATCH',
    data: body,
  });
}

/** Query hook for the business thresholds shown in {@link OrgSettingsPage}. */
export function useBusinessThresholds(tenantId: string = DEMO_TENANT_ID) {
  return useQuery({
    queryKey: queryKeys.businessThresholds(tenantId),
    queryFn: ({ signal }) => fetchThresholds(tenantId, signal),
  });
}

/** Mutation hook to save the business thresholds; refreshes the query on success. */
export function useUpdateThresholds(tenantId: string = DEMO_TENANT_ID) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BusinessThresholds) => patchThresholds(tenantId, body),
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.businessThresholds(tenantId), data);
    },
  });
}

// ---------------------------------------------------------------------------
// AIDA / AI orchestration — ai + interception modules
// ---------------------------------------------------------------------------

/** Mirrors `AiController.ClassifyRequest`. */
export interface ClassifyRequest {
  content: string;
}

/** Mirrors `AiController.ClassifyResponse`. */
export interface ClassifyResponse {
  label: string;
}

/** Mirrors `EvaluateRequest` (interception module). */
export interface EvaluateRequest {
  userId: string;
  amount: number;
  destAccount: string;
  onCall: boolean;
  newPayee: boolean;
}

/** Mirrors `InterventionDecision`. */
export interface InterventionDecision {
  decision: string;
  signals: string[];
  message: string;
}

/** POST /ai/classify — classify report/email content (clean|spam|threat). */
export function classify(body: ClassifyRequest): Promise<ClassifyResponse> {
  return apiRequest<ClassifyResponse>({
    url: '/ai/classify',
    method: 'POST',
    data: body,
  });
}

/** POST /interventions/evaluate — get an intervention decision for a transaction. */
export function evaluateIntervention(body: EvaluateRequest): Promise<InterventionDecision> {
  return apiRequest<InterventionDecision>({
    url: '/interventions/evaluate',
    method: 'POST',
    data: body,
  });
}

/** useMutation hook wrapping POST /ai/classify. */
export function useClassify() {
  return useMutation({ mutationFn: classify });
}

/** useMutation hook wrapping POST /interventions/evaluate. */
export function useEvaluateIntervention() {
  return useMutation({ mutationFn: evaluateIntervention });
}

// ---------------------------------------------------------------------------
// AIDA orchestration runs (history) — ai module
// ---------------------------------------------------------------------------

/** Mirrors `AidaRunView` (`GET /ai/orchestration/runs`). snake_case wire shape. */
export interface AidaRun {
  id: string;
  scope: string;
  scope_id?: string | null;
  status: string;
  summary?: string | null;
  created_at?: string;
}

/** GET /ai/orchestration/runs — recent AIDA runs for the tenant, newest first. */
export function fetchAidaRuns(signal?: AbortSignal): Promise<AidaRun[]> {
  return apiRequest<AidaRun[]>({
    url: '/ai/orchestration/runs',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering the recent-runs panel in {@link AidaPage}. */
export function useAidaRuns() {
  return useQuery({
    queryKey: queryKeys.aidaRuns,
    queryFn: ({ signal }) => fetchAidaRuns(signal),
  });
}

/** Body for `POST /ai/orchestration/run`. */
export interface RunOrchestrationRequest {
  scope: string;
}

/** POST /ai/orchestration/run — trigger AIDA and record the run. */
export function runOrchestration(body: RunOrchestrationRequest): Promise<void> {
  return apiRequest<void>({
    url: '/ai/orchestration/run',
    method: 'POST',
    data: body,
  });
}

/** Mutation hook for triggering AIDA; refreshes the runs panel on success. */
export function useRunOrchestration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: runOrchestration,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.aidaRuns });
    },
  });
}
