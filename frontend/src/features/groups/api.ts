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

/** PATCH /groups/{id} — update name and/or rule (empty rule_json clears it). */
export function updateGroup(id: string, body: GroupUpsert): Promise<GroupDto> {
  return apiRequest<GroupDto>({ url: `/groups/${id}`, method: 'PATCH', data: body });
}

/** DELETE /groups/{id} — remove a group and its memberships. */
export function deleteGroup(id: string): Promise<void> {
  return apiRequest<void>({ url: `/groups/${id}`, method: 'DELETE' });
}

/** GET /groups/{id}/members — user ids of the group's members. */
export function fetchGroupMembers(id: string, signal?: AbortSignal): Promise<string[]> {
  return apiRequest<string[]>({
    url: `/groups/${id}/members`,
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** POST /groups/{id}/members — add a user; returns the new count. */
export function addGroupMember(id: string, userId: string): Promise<{ member_count: number }> {
  return apiRequest<{ member_count: number }>({
    url: `/groups/${id}/members`,
    method: 'POST',
    data: { user_id: userId },
  });
}

/** DELETE /groups/{id}/members/{userId} — remove a user; returns the new count. */
export function removeGroupMember(id: string, userId: string): Promise<{ member_count: number }> {
  return apiRequest<{ member_count: number }>({
    url: `/groups/${id}/members/${userId}`,
    method: 'DELETE',
  });
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

/** Update-group mutation. */
export function useUpdateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; body: GroupUpsert }) => updateGroup(vars.id, vars.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups }),
  });
}

/** Delete-group mutation. */
export function useDeleteGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteGroup,
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups }),
  });
}

/** Members (user ids) of a group. */
export function useGroupMembers(groupId: string | null) {
  return useQuery({
    queryKey: [...queryKeys.groups, groupId, 'members'],
    queryFn: ({ signal }) => fetchGroupMembers(groupId as string, signal),
    enabled: Boolean(groupId),
  });
}

/** Add-member mutation; refreshes the group's member list and the group list. */
export function useAddGroupMember(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => addGroupMember(groupId, userId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [...queryKeys.groups, groupId, 'members'] });
      void qc.invalidateQueries({ queryKey: queryKeys.groups });
    },
  });
}

/** Remove-member mutation. */
export function useRemoveGroupMember(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => removeGroupMember(groupId, userId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [...queryKeys.groups, groupId, 'members'] });
      void qc.invalidateQueries({ queryKey: queryKeys.groups });
    },
  });
}
