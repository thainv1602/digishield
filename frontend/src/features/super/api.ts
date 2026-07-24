import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Thin, hand-written typed fetchers + hooks for the Super-Admin pages
 * (Tenant Console, SCIM & SSO Config, Audit Log).
 *
 * Types mirror the backend records in the tenancy module exactly. The records
 * use no `@JsonProperty`, so Jackson serializes the component names verbatim
 * (camelCase).
 */

// ---------------------------------------------------------------------------
// Tenant Console — GET /tenants
// ---------------------------------------------------------------------------

/** Mirrors `TenantView` (`GET /tenants`). */
export interface Tenant {
  id: string;
  tenantId: string;
  name: string | null;
  tier: string | null;
  dataRegion: string | null;
  status: string | null;
  userCount: number | null;
  domain: string | null;
}

/** GET /tenants — list of all tenants (Super Console). */
export function fetchTenants(signal?: AbortSignal): Promise<Tenant[]> {
  return apiRequest<Tenant[]>({
    url: '/tenants',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

export function useTenants() {
  return useQuery({
    queryKey: queryKeys.tenants,
    queryFn: ({ signal }) => fetchTenants(signal),
  });
}

/** GET /tenants/{id} — a single tenant (Super Admin, or Org Admin for self). */
export function fetchTenant(id: string, signal?: AbortSignal): Promise<Tenant> {
  return apiRequest<Tenant>({
    url: `/tenants/${id}`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

export function useTenant(id: string | null) {
  return useQuery({
    queryKey: [...queryKeys.tenants, id],
    queryFn: ({ signal }) => fetchTenant(id as string, signal),
    enabled: Boolean(id),
  });
}

/** Create-tenant payload (`CreateTenantCommand`). */
export interface CreateTenantInput {
  name: string;
  tier: string;
  dataRegion: string;
}

/** Update-tenant payload (`UpdateTenantCommand`); omitted fields left unchanged. */
export interface UpdateTenantInput {
  name?: string;
  tier?: string;
  status?: string;
  dataRegion?: string;
}

/** POST /tenants — create a tenant. */
export function createTenant(body: CreateTenantInput): Promise<Tenant> {
  return apiRequest<Tenant>({ url: '/tenants', method: 'POST', data: body });
}

/** PATCH /tenants/{id} — update tier / status / data region. */
export function updateTenant(id: string, body: UpdateTenantInput): Promise<Tenant> {
  return apiRequest<Tenant>({ url: `/tenants/${id}`, method: 'PATCH', data: body });
}

export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createTenant,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.tenants }),
  });
}

export function useUpdateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; body: UpdateTenantInput }) => updateTenant(vars.id, vars.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.tenants }),
  });
}

// ---------------------------------------------------------------------------
// SCIM & SSO Config — GET /super/scim
// ---------------------------------------------------------------------------

/** Mirrors `ScimConfigView` (`GET /super/scim`). */
export interface ScimConfig {
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

/** GET /super/scim — SCIM/SSO config of the current tenant. */
export function fetchScimConfig(signal?: AbortSignal): Promise<ScimConfig> {
  return apiRequest<ScimConfig>({
    url: '/super/scim',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

export function useScimConfig() {
  return useQuery({
    queryKey: queryKeys.scimConfig,
    queryFn: ({ signal }) => fetchScimConfig(signal),
  });
}

// ---------------------------------------------------------------------------
// Audit Log — GET /audit
// ---------------------------------------------------------------------------

/** Mirrors `AuditLogView` (`GET /audit`). */
export interface AuditLogEntry {
  id: string;
  ts: string | null;
  actor: string | null;
  action: string | null;
  target: string | null;
  ip: string | null;
  severity: string | null;
}

/** GET /audit — audit-log entries of the current tenant. */
export function fetchAuditLogs(signal?: AbortSignal): Promise<AuditLogEntry[]> {
  return apiRequest<AuditLogEntry[]>({
    url: '/audit',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

export function useAuditLogs() {
  return useQuery({
    queryKey: queryKeys.audit,
    queryFn: ({ signal }) => fetchAuditLogs(signal),
  });
}
