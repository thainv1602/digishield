import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Thin, hand-written typed fetchers + hooks for the SOC inbox.
 *
 * Mirrors `PhishingReportDto` from the reporting module
 * (`GET /reports/phishing?status=`). These fields are emitted as camelCase
 * (the BE uses `@JsonProperty("aiLabel")` etc.).
 */

/** AI classification as emitted by the BE (lowercase). */
export type AiLabel = 'clean' | 'spam' | 'threat';

export interface PhishingReport {
  id: string;
  userId: string | null;
  reporter: string | null;
  subject: string | null;
  sender: string | null;
  payload: string | null;
  aiLabel: AiLabel | string;
  aiConfidence: number;
  reasoning: string | null;
  blacklistMatch: boolean;
  status: string | null;
  channel: string | null;
  /**
   * ISO-8601 instant, or null on rows written before the column existed.
   * `ageLabel` is the same moment rendered for a human; this one is what can be
   * grouped, sorted or plotted.
   */
  reportedAt: string | null;
  ageLabel: string | null;
}

/** GET /reports/phishing — SOC inbox queue, optionally filtered by status. */
export function fetchPhishingReports(
  status?: string,
  signal?: AbortSignal,
): Promise<PhishingReport[]> {
  return apiRequest<PhishingReport[]>({
    url: '/reports/phishing',
    method: 'GET',
    ...(status ? { params: { status } } : {}),
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link SocInboxPage}. */
export function usePhishingReports(status?: string) {
  return useQuery({
    queryKey: queryKeys.phishingReports(status),
    queryFn: ({ signal }) => fetchPhishingReports(status, signal),
  });
}

// ---------------------------------------------------------------------------
// SOC inbox — triage mutations (POST /reports/phishing/{id}/triage)
// ---------------------------------------------------------------------------

/** Analyst triage decision, as accepted by the BE. */
export type TriageDecision = 'confirm_threat' | 'dismiss' | 'quarantine';

export interface TriageInput {
  id: string;
  decision: TriageDecision;
  /** when confirming a threat, also add its indicators to the blacklist */
  addToBlacklist?: boolean;
}

/** POST /reports/phishing/{id}/triage — record an analyst decision on a report. */
export function triageReport({ id, decision, addToBlacklist }: TriageInput): Promise<PhishingReport> {
  return apiRequest<PhishingReport>({
    url: `/reports/phishing/${id}/triage`,
    method: 'POST',
    data: { decision, add_to_blacklist: Boolean(addToBlacklist) },
  });
}

/** Mutation hook for a single report's triage; invalidates the inbox on success. */
export function useTriageReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: triageReport,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['reports', 'phishing'] });
    },
  });
}

/** POST /reports/phishing/{id}/convert-to-training — ThreatFlip a report into a lesson. */
export function convertReportToTraining(id: string): Promise<void> {
  return apiRequest<void>({
    url: `/reports/phishing/${id}/convert-to-training`,
    method: 'POST',
  });
}

/** Mutation hook for converting a report into a training lesson. */
export function useConvertReportToTraining() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: convertReportToTraining,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['reports', 'phishing'] });
    },
  });
}

// ---------------------------------------------------------------------------
// Threat intelligence feed (GET /threat-intel, POST /threat-intel/{id}/convert)
// ---------------------------------------------------------------------------

/** Mirrors `ThreatIntel` from the threat-flip module (snake_case wire shape). */
export interface ThreatIntel {
  id?: string;
  source?: string;
  raw_payload?: string;
  collected_at?: string;
  /** set once ThreatFlip has turned it into a coaching template */
  converted_template_id?: string;
}

/** GET /threat-intel — the tenant's incoming threat-intelligence feed. */
export function fetchThreatIntel(signal?: AbortSignal): Promise<ThreatIntel[]> {
  return apiRequest<ThreatIntel[]>({
    url: '/threat-intel',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link ThreatIntelPage}. */
export function useThreatIntel() {
  return useQuery({
    queryKey: queryKeys.threatIntel,
    queryFn: ({ signal }) => fetchThreatIntel(signal),
  });
}

/** POST /threat-intel/{id}/convert — ThreatFlip an item into a coaching template. */
export function convertThreatIntel(id: string): Promise<unknown> {
  return apiRequest<unknown>({
    url: `/threat-intel/${id}/convert`,
    method: 'POST',
  });
}

/** Mutation hook for ThreatFlip; refreshes the feed on success. */
export function useConvertThreatIntel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: convertThreatIntel,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.threatIntel });
    },
  });
}

// ---------------------------------------------------------------------------
// Intervention audit log (GET /interventions)
// ---------------------------------------------------------------------------

/** Decision emitted by the transaction-risk engine. */
export type InterventionDecision = 'allow' | 'warn' | 'pause' | 'block';

/** Mirrors `InterventionEvent` from the interception module. */
export interface InterventionEvent {
  id?: string;
  user_id?: string;
  org_id?: string;
  decision?: InterventionDecision;
  signals?: string[];
  ts?: string;
}

/** GET /interventions — the transaction-risk intervention audit log. */
export function fetchInterventions(signal?: AbortSignal): Promise<InterventionEvent[]> {
  return apiRequest<InterventionEvent[]>({
    url: '/interventions',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering {@link InterventionLogPage}. */
export function useInterventions() {
  return useQuery({
    queryKey: queryKeys.interventions,
    queryFn: ({ signal }) => fetchInterventions(signal),
  });
}
