/**
 * AWS Cognito OIDC wiring (authorization-code + PKCE via the hosted UI).
 *
 * Enabled only when VITE_COGNITO_AUTHORITY + VITE_COGNITO_CLIENT_ID are set
 * (i.e. the deployed build). When unset (local dev / tests) `cognitoEnabled` is
 * false and the app keeps its demo login. The issuer (authority) is the Cognito
 * user-pool URL; oidc-client-ts reads its discovery doc for the hosted-UI
 * authorize/token endpoints.
 */
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { ROLES, type Role } from './roles';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';
import type { CurrentUser } from './AuthContext';

const authority = import.meta.env.VITE_COGNITO_AUTHORITY as string | undefined;
const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID as string | undefined;
// Must match a registered Cognito callback URL exactly (origin, no trailing /).
const redirectUri =
  (import.meta.env.VITE_COGNITO_REDIRECT_URI as string | undefined) ||
  window.location.origin;

export const cognitoEnabled = Boolean(authority && clientId);

export const userManager: UserManager | null = cognitoEnabled
  ? new UserManager({
      authority: authority as string,
      client_id: clientId as string,
      redirect_uri: redirectUri,
      post_logout_redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'openid email profile',
      // Persist the session so a refresh keeps the user signed in.
      userStore: new WebStorageStateStore({ store: window.localStorage }),
      automaticSilentRenew: true,
    })
  : null;

// Highest privilege first — a user may be in several groups.
const ROLE_PRECEDENCE: Role[] = [
  ROLES.SUPER_ADMIN,
  ROLES.ORG_ADMIN,
  ROLES.MANAGER,
  ROLES.CONTENT_EDITOR,
  ROLES.ANALYST,
  ROLES.LEARNER,
];

function roleFromGroups(groups: unknown): Role {
  const list = Array.isArray(groups) ? groups.map(String) : [];
  return ROLE_PRECEDENCE.find((r) => list.includes(r)) ?? ROLES.LEARNER;
}

/** Map an OIDC user (Cognito tokens) to the app's CurrentUser. */
export function toCurrentUser(user: User): CurrentUser {
  const p = user.profile as Record<string, unknown>;
  const email = typeof p.email === 'string' ? p.email : undefined;
  const name = typeof p.name === 'string' ? p.name : email;
  const current: CurrentUser = {
    id: String(p.sub ?? ''),
    tenantId: (import.meta.env.VITE_TENANT_ID as string | undefined) || DEMO_TENANT_ID,
    role: roleFromGroups(p['cognito:groups']),
  };
  if (email !== undefined) current.email = email;
  if (name !== undefined) current.name = name;
  return current;
}
