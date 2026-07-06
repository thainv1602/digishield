import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Hand-written typed fetchers + hooks for the Content Studio.
 *
 * Templates live in the AI module and map onto the OpenAPI `SimTemplate` schema.
 * Enum-backed fields are lowercase on the wire; `body_ref` is a stable slug and
 * `body` is the actual message content.
 *
 * - `GET  /ai/templates`             → library
 * - `POST /ai/templates`             → author a new template
 * - `PATCH /ai/templates/{id}`       → edit an existing template
 * - `POST /ai/templates/{id}/submit` → submit (draft → approved)
 * - `DELETE /ai/templates/{id}`      → remove
 * - `POST /ai/templates/generate`    → AI-assist: generate + persist a draft
 */

/** Wire shape of `SimTemplate` (AI module `SimTemplateView`). */
export interface SimTemplate {
  id: string;
  channel: string;
  subject: string;
  body_ref: string;
  /** the actual message body (phishing email/SMS content) */
  body: string | null;
  /** free-text theme, e.g. "Cơ quan thuế" */
  category: string | null;
  difficulty: string;
  status: string;
}

/** GET /ai/templates — the saved simulation-template library for the tenant. */
export function fetchTemplates(signal?: AbortSignal): Promise<SimTemplate[]> {
  return apiRequest<SimTemplate[]>({
    url: '/ai/templates',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** TanStack Query hook powering the template library in the Content Studio. */
export function useTemplates() {
  return useQuery({
    queryKey: queryKeys.aiTemplates,
    queryFn: ({ signal }) => fetchTemplates(signal),
  });
}

/** Fields an editor can author/edit. On update, omitted fields are left unchanged. */
export interface UpsertTemplateRequest {
  channel?: string;
  subject?: string;
  body?: string | null;
  category?: string | null;
  difficulty?: string;
  /** create only: save straight as APPROVED instead of DRAFT */
  approved?: boolean;
}

/** POST /ai/templates — author a new template (draft unless `approved`). */
export function createTemplate(body: UpsertTemplateRequest): Promise<SimTemplate> {
  return apiRequest<SimTemplate>({ url: '/ai/templates', method: 'POST', data: body });
}

/** PATCH /ai/templates/{id} — edit an existing template. */
export function updateTemplate(id: string, body: UpsertTemplateRequest): Promise<SimTemplate> {
  return apiRequest<SimTemplate>({ url: `/ai/templates/${id}`, method: 'PATCH', data: body });
}

/** POST /ai/templates/{id}/submit — move a draft to APPROVED. */
export function submitTemplate(id: string): Promise<SimTemplate> {
  return apiRequest<SimTemplate>({ url: `/ai/templates/${id}/submit`, method: 'POST' });
}

/** DELETE /ai/templates/{id} — remove a template. */
export function deleteTemplate(id: string): Promise<void> {
  return apiRequest<void>({ url: `/ai/templates/${id}`, method: 'DELETE' });
}

/** Request for `POST /ai/templates/generate` (AI-assisted draft). */
export interface GenerateTemplateRequest {
  channel: string;
  /** industry / theme hint (stored as the draft's category) */
  industry?: string | null;
  season?: string | null;
}

/** POST /ai/templates/generate — AI-generate + persist a draft, returned in full. */
export function generateTemplate(body: GenerateTemplateRequest): Promise<SimTemplate> {
  return apiRequest<SimTemplate>({ url: '/ai/templates/generate', method: 'POST', data: body });
}

/** Invalidate the library so every mutation refreshes the list. */
function useTemplatesInvalidator() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: queryKeys.aiTemplates });
}

/** Mutation: AI-generate a draft (Content Studio "Sinh bằng AI"). */
export function useGenerateTemplate() {
  const invalidate = useTemplatesInvalidator();
  return useMutation({ mutationFn: generateTemplate, onSuccess: () => invalidate() });
}

/** Mutation: create a new template. */
export function useCreateTemplate() {
  const invalidate = useTemplatesInvalidator();
  return useMutation({ mutationFn: createTemplate, onSuccess: () => invalidate() });
}

/** Mutation: edit an existing template. */
export function useUpdateTemplate() {
  const invalidate = useTemplatesInvalidator();
  return useMutation({
    mutationFn: (vars: { id: string; body: UpsertTemplateRequest }) => updateTemplate(vars.id, vars.body),
    onSuccess: () => invalidate(),
  });
}

/** Mutation: submit a template for use (draft → approved). */
export function useSubmitTemplate() {
  const invalidate = useTemplatesInvalidator();
  return useMutation({ mutationFn: submitTemplate, onSuccess: () => invalidate() });
}

/** Mutation: delete a template. */
export function useDeleteTemplate() {
  const invalidate = useTemplatesInvalidator();
  return useMutation({ mutationFn: deleteTemplate, onSuccess: () => invalidate() });
}
