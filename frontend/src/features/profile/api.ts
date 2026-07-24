import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '@/shared/api/client';

const PROFILE_KEY = ['auth', 'profile'] as const;

/** Mirrors `ProfileView` (`GET/PATCH /auth/profile`). */
export interface Profile {
  id: string | null;
  tenantId: string | null;
  email: string | null;
  role: string | null;
  name: string | null;
  locale: string | null;
}

/** Self-profile update payload; all fields optional. */
export interface ProfileUpdate {
  name?: string;
  locale?: string;
  /** The ID-token email, sent so the backend can provision the row on first save. */
  email?: string | null;
}

/** GET /auth/profile — the signed-in user's own profile. */
export function fetchProfile(signal?: AbortSignal): Promise<Profile> {
  return apiRequest<Profile>({
    url: '/auth/profile',
    method: 'GET',
    ...(signal ? { signal } : {}),
  });
}

/** PATCH /auth/profile — update own name / locale. */
export function updateProfile(body: ProfileUpdate): Promise<Profile> {
  return apiRequest<Profile>({ url: '/auth/profile', method: 'PATCH', data: body });
}

export function useMyProfile() {
  return useQuery({
    queryKey: PROFILE_KEY,
    queryFn: ({ signal }) => fetchProfile(signal),
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: updateProfile,
    onSuccess: (data) => qc.setQueryData(PROFILE_KEY, data),
  });
}
