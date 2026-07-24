import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';

/**
 * Typed fetchers + hooks for the Groups screen.
 *
 * Mirrors `GroupView` (`/api/v1/groups`): a group is "smart" when it carries a
 * `rule_json` (dynamic membership) and "static" otherwise. The backend supports
 * list, create and re-evaluate (there is no update/delete endpoint yet).
 */

/** Wire shape of a group (`GroupView`). */
export interface GroupDto {
  id: string;
  name: string | null;
  rule_json: Record<string, unknown> | null;
  member_count: number | null;
}

/** Create payload — `member_count` is server-computed and omitted. */
export interface GroupUpsert {
  name: string;
  rule_json?: Record<string, unknown> | null;
}

/** GET /groups — groups of the current tenant. */
export function fetchGroups(signal?: AbortSignal): Promise<GroupDto[]> {
  return apiRequest<GroupDto[]>({
    url: '/groups',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** POST /groups — create a static or smart group. */
export function createGroup(body: GroupUpsert): Promise<GroupDto> {
  return apiRequest<GroupDto>({ url: '/groups', method: 'POST', data: body });
}

/** POST /groups/{id}/evaluate — re-evaluate a smart group's membership. */
export function evaluateGroup(id: string): Promise<{ member_count: number }> {
  return apiRequest<{ member_count: number }>({
    url: `/groups/${id}/evaluate`,
    method: 'POST',
  });
}

/** TanStack Query hook powering the Groups page. */
export function useGroups() {
  return useQuery({
    queryKey: queryKeys.groups,
    queryFn: ({ signal }) => fetchGroups(signal),
  });
}

/** Create-group mutation; refreshes the list on success. */
export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createGroup,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups }),
  });
}

/** Re-evaluate mutation; refreshes the list so the new member count shows. */
export function useEvaluateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: evaluateGroup,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups }),
  });
}
