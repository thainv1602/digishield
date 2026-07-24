import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Thin, hand-written typed fetcher + hook for the Users screen.
 *
 * Mirrors `UserView` from the auth module (`GET /api/v1/users`). The BE emits
 * both camelCase (`riskScore`) and snake_case (`org_id`, `risk_score`) wire
 * fields via `@JsonProperty`; the page reads `dept`/`risk`, so the hook maps
 * `department` -> `dept` and `riskScore`/`risk_score` -> `risk`.
 */

/** Raw `UserView` as emitted on the wire by the BE. */
export interface UserViewDto {
  id: string;
  org_id: string | null;
  email: string | null;
  name: string | null;
  role: string | null;
  status: string | null;
  department: string | null;
  department_id: string | null;
  locale: string | null;
  riskScore: number | null;
  risk_score: number | null;
}

/** FE-facing row consumed by {@link UsersPage}. */
export interface UserRow {
  id: string;
  name: string;
  email: string;
  role: string;
  dept: string;
  risk: number;
  status: string;
  /** Kept for the edit form so a PATCH preserves these fields. */
  departmentId: string | null;
  locale: string;
}

/** Create/update payload — mirrors the BE `UserInput` schema (snake_case wire). */
export interface UserUpsert {
  email: string;
  role: string;
  department_id?: string | null;
  locale?: string | null;
}

/** GET /users — list users of the current tenant. */
export function fetchUsers(signal?: AbortSignal): Promise<UserViewDto[]> {
  return apiRequest<UserViewDto[]>({
    url: '/users',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** Map a backend `UserView` onto the FE row (dept/risk field rename). */
export function toUserRow(dto: UserViewDto): UserRow {
  return {
    id: dto.id,
    name: dto.name ?? '—',
    email: dto.email ?? '',
    role: dto.role ?? '',
    dept: dto.department ?? '',
    risk: dto.riskScore ?? dto.risk_score ?? 0,
    status: dto.status ?? 'active',
    departmentId: dto.department_id ?? null,
    locale: dto.locale ?? 'vi',
  };
}

/** TanStack Query hook powering {@link UsersPage}; returns mapped rows. */
export function useUsers() {
  return useQuery({
    queryKey: queryKeys.users,
    queryFn: ({ signal }) => fetchUsers(signal),
    select: (data) => data.map(toUserRow),
  });
}

// ---- Mutations (create / update / import) ---------------------------------

/** POST /users — create a user in the current tenant. */
export function createUser(body: UserUpsert): Promise<UserViewDto> {
  return apiRequest<UserViewDto>({ url: '/users', method: 'POST', data: body });
}

/** PATCH /users/{id} — update a user. */
export function updateUser(id: string, body: UserUpsert): Promise<UserViewDto> {
  return apiRequest<UserViewDto>({ url: `/users/${id}`, method: 'PATCH', data: body });
}

/** POST /users/import — bulk create/update from a list. */
export function importUsers(users: UserUpsert[]): Promise<unknown> {
  return apiRequest<unknown>({ url: '/users/import', method: 'POST', data: { users } });
}

/** Create-user mutation; refreshes the user list on success. */
export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users }),
  });
}

/** Update-user mutation; refreshes the user list on success. */
export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; body: UserUpsert }) => updateUser(vars.id, vars.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users }),
  });
}

/** Bulk-import mutation; refreshes the user list on success. */
export function useImportUsers() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: importUsers,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users }),
  });
}
