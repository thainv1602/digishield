import type { User } from 'oidc-client-ts';
import { describe, expect, it } from 'vitest';
import { toCurrentUser } from './cognito';
import { ROLES } from './roles';
import { DEMO_TENANT_ID } from '@/shared/api/tenant';

/**
 * toCurrentUser turns Cognito's token claims into the principal the whole app
 * authorises against. Getting the role wrong here is a privilege decision made
 * from a string list, so the precedence and the fallback both matter.
 */
const oidcUser = (profile: Record<string, unknown>): User =>
  ({ profile } as unknown as User);

describe('toCurrentUser', () => {
  it('takes the identity from the subject claim', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc-123' }));

    expect(user.id).toBe('abc-123');
  });

  it('falls back to the demo tenant when no tenant is configured', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc' }));

    expect(user.tenantId).toBe(DEMO_TENANT_ID);
  });

  it('reads the role from the Cognito group list', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc', 'cognito:groups': ['analyst'] }));

    expect(user.role).toBe(ROLES.ANALYST);
  });

  it('grants the highest privilege when a user is in several groups', () => {
    // Cognito returns every group a user belongs to, unordered. Picking the
    // first would hand out whichever role happened to come back first.
    const user = toCurrentUser(
      oidcUser({ sub: 'abc', 'cognito:groups': ['learner', 'super_admin', 'analyst'] }),
    );

    expect(user.role).toBe(ROLES.SUPER_ADMIN);
  });

  it('respects precedence regardless of the order the groups arrive in', () => {
    const ascending = toCurrentUser(
      oidcUser({ sub: 'abc', 'cognito:groups': ['learner', 'org_admin'] }),
    );
    const descending = toCurrentUser(
      oidcUser({ sub: 'abc', 'cognito:groups': ['org_admin', 'learner'] }),
    );

    expect(ascending.role).toBe(ROLES.ORG_ADMIN);
    expect(descending.role).toBe(ROLES.ORG_ADMIN);
  });

  it('defaults to the least privileged role when no group matches', () => {
    const noGroups = toCurrentUser(oidcUser({ sub: 'abc' }));
    const unknownGroup = toCurrentUser(
      oidcUser({ sub: 'abc', 'cognito:groups': ['some-other-group'] }),
    );

    expect(noGroups.role).toBe(ROLES.LEARNER);
    expect(unknownGroup.role).toBe(ROLES.LEARNER);
  });

  it('ignores a groups claim that is not a list', () => {
    // A malformed claim must not throw inside the sign-in callback, where the
    // failure would read as "login broken" rather than "claim malformed".
    const user = toCurrentUser(oidcUser({ sub: 'abc', 'cognito:groups': 'super_admin' }));

    expect(user.role).toBe(ROLES.LEARNER);
  });

  it('carries email, name and locale through when present', () => {
    const user = toCurrentUser(
      oidcUser({ sub: 'abc', email: 'a@b.com', name: 'Alice', locale: 'vi' }),
    );

    expect(user).toMatchObject({ email: 'a@b.com', name: 'Alice', locale: 'vi' });
  });

  it('shows the email when the profile carries no display name', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc', email: 'a@b.com' }));

    expect(user.name).toBe('a@b.com');
  });

  it('omits optional fields rather than setting them undefined', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc' }));

    expect('email' in user).toBe(false);
    expect('name' in user).toBe(false);
    expect('locale' in user).toBe(false);
  });

  it('ignores claims of the wrong type', () => {
    const user = toCurrentUser(oidcUser({ sub: 'abc', email: 42, locale: {} }));

    expect('email' in user).toBe(false);
    expect('locale' in user).toBe(false);
  });
});
