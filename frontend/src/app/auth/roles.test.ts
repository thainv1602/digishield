import { describe, expect, it } from 'vitest';
import {
  ADMIN_ROLES,
  ALL_ROLES,
  ANALYTICS_ROLES,
  MANAGER_ROLES,
  NAV_BY_PERSONA,
  ROLES,
  defaultRouteForRole,
  navForRole,
  roleToPersona,
  type Persona,
  type Role,
} from './roles';

const PERSONAS: Persona[] = ['admin', 'learner', 'analyst', 'super'];

/**
 * Roles decide two different things: which nav tree someone sees, and whether
 * they may reach the admin console. The second is an authorisation input, so a
 * role silently falling into the wrong bucket widens access rather than just
 * looking wrong.
 */
describe('roleToPersona', () => {
  it('gives every role a persona', () => {
    for (const role of ALL_ROLES) {
      expect(PERSONAS).toContain(roleToPersona(role));
    }
  });

  it.each([
    [ROLES.LEARNER, 'learner'],
    [ROLES.ANALYST, 'analyst'],
    [ROLES.SUPER_ADMIN, 'super'],
    [ROLES.ORG_ADMIN, 'admin'],
    [ROLES.MANAGER, 'admin'],
    [ROLES.CONTENT_EDITOR, 'admin'],
  ] as Array<[Role, Persona]>)('maps %s to the %s persona', (role, persona) => {
    expect(roleToPersona(role)).toBe(persona);
  });

  it('keeps the three admin-console roles on one nav tree', () => {
    // They share a sidebar; orgAdminOnly items are what separates them.
    const admins = [ROLES.ORG_ADMIN, ROLES.MANAGER, ROLES.CONTENT_EDITOR];
    expect(admins.map(roleToPersona)).toEqual(['admin', 'admin', 'admin']);
  });

  it('does not fold an unknown role into a privileged persona by default', () => {
    // The mapping ends in `default: 'admin'`, so a role added to ROLES without
    // a case here lands on the admin tree. Pinning it makes that visible if a
    // seventh role ever appears.
    const known: Role[] = [
      ROLES.LEARNER,
      ROLES.ANALYST,
      ROLES.SUPER_ADMIN,
      ROLES.ORG_ADMIN,
      ROLES.MANAGER,
      ROLES.CONTENT_EDITOR,
    ];
    expect(new Set(ALL_ROLES)).toEqual(new Set(known));
  });
});

describe('ADMIN_ROLES', () => {
  it('admits exactly the four console roles', () => {
    expect(new Set(ADMIN_ROLES)).toEqual(
      new Set([ROLES.SUPER_ADMIN, ROLES.ORG_ADMIN, ROLES.MANAGER, ROLES.CONTENT_EDITOR]),
    );
  });

  it('excludes learner and analyst', () => {
    expect(ADMIN_ROLES).not.toContain(ROLES.LEARNER);
    expect(ADMIN_ROLES).not.toContain(ROLES.ANALYST);
  });

  it('lists only real roles', () => {
    for (const role of ADMIN_ROLES) {
      expect(ALL_ROLES).toContain(role);
    }
  });
});

describe('NAV_BY_PERSONA', () => {
  it('gives every persona somewhere to go', () => {
    for (const persona of PERSONAS) {
      expect(NAV_BY_PERSONA[persona].length).toBeGreaterThan(0);
    }
  });

  it('uses unique keys within a persona', () => {
    for (const persona of PERSONAS) {
      const keys = NAV_BY_PERSONA[persona].map((i) => i.key);
      // Duplicates make React reuse the wrong node when the tree re-renders.
      expect(new Set(keys).size).toBe(keys.length);
    }
  });

  it('routes every item to an absolute path', () => {
    for (const persona of PERSONAS) {
      for (const item of NAV_BY_PERSONA[persona]) {
        expect(item.path.startsWith('/')).toBe(true);
      }
    }
  });

  it('labels and names every item', () => {
    for (const persona of PERSONAS) {
      for (const item of NAV_BY_PERSONA[persona]) {
        expect(item.label.trim()).not.toBe('');
        expect(item.icon.trim()).not.toBe('');
      }
    }
  });

  it('marks orgAdminOnly items only on the shared admin tree', () => {
    // The flag exists to hide items from manager/content_editor, who only
    // appear on the admin tree. Anywhere else it would silently do nothing.
    for (const persona of PERSONAS) {
      if (persona === 'admin') continue;
      const flagged = NAV_BY_PERSONA[persona].filter((i) => i.orgAdminOnly);
      expect(flagged).toEqual([]);
    }
  });
});

/**
 * These mirror backend @PreAuthorize annotations. The backend expands them
 * through the role hierarchy in MethodSecurityConfig
 * (SUPER_ADMIN > ORG_ADMIN > {MANAGER, ANALYST, CONTENT_EDITOR}), so the lists
 * here must be the expanded form. Getting them wrong does not fail loudly: the
 * page opens and only then does the API answer 403.
 */
describe('backend gate mirrors', () => {
  it('admits the roles AnalyticsController accepts', () => {
    // hasAnyRole('ANALYST','MANAGER'), plus everything above them.
    expect(new Set(ANALYTICS_ROLES)).toEqual(
      new Set([ROLES.SUPER_ADMIN, ROLES.ORG_ADMIN, ROLES.MANAGER, ROLES.ANALYST]),
    );
  });

  it('admits the roles a hasRole(MANAGER) endpoint accepts', () => {
    expect(new Set(MANAGER_ROLES)).toEqual(
      new Set([ROLES.SUPER_ADMIN, ROLES.ORG_ADMIN, ROLES.MANAGER]),
    );
  });

  it('excludes content_editor from both', () => {
    // content_editor inherits only LEARNER, so it satisfies neither gate — the
    // mismatch that let it open pages whose first request came back 403.
    expect(ANALYTICS_ROLES).not.toContain(ROLES.CONTENT_EDITOR);
    expect(MANAGER_ROLES).not.toContain(ROLES.CONTENT_EDITOR);
  });

  it('lets an analyst reach analytics but not manager-gated pages', () => {
    expect(ANALYTICS_ROLES).toContain(ROLES.ANALYST);
    expect(MANAGER_ROLES).not.toContain(ROLES.ANALYST);
  });
});

describe('navForRole', () => {
  it('offers the overview to every role the analytics gate admits', () => {
    for (const role of ANALYTICS_ROLES) {
      const paths = navForRole(role).map((i) => i.path);
      expect(paths).toContain('/dashboard');
    }
  });

  it('hides pages a role would be refused by the API', () => {
    const paths = navForRole(ROLES.CONTENT_EDITOR).map((i) => i.path);
    expect(paths).not.toContain('/dashboard');
    expect(paths).not.toContain('/campaigns/new');
    expect(paths).not.toContain('/compliance');
  });

  it('leaves the content editor a page to work on', () => {
    expect(navForRole(ROLES.CONTENT_EDITOR).length).toBeGreaterThan(0);
  });

  it('never lands a role on a page its own nav will not show', () => {
    // The `/` redirect and the post-login redirect both use this, so a landing
    // page the role cannot open turns a successful sign-in into a 403.
    for (const role of ALL_ROLES) {
      const home = defaultRouteForRole(role);
      const paths = navForRole(role).map((i) => i.path);
      expect(paths).toContain(home);
    }
  });
});
