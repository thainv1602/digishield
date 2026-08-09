import { describe, expect, it } from 'vitest';
import {
  ADMIN_ROLES,
  ALL_ROLES,
  NAV_BY_PERSONA,
  ROLES,
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
