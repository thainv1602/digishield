/**
 * RBAC role constants, persona mapping, and persona-scoped navigation trees.
 * Roles mirror the backend authorization model (org-scoped JWT). The sidebar
 * groups the 6 roles into 4 UI personas (Admin / Learner / Analyst / Super) to
 * pick which nav tree to show, while route guards still use raw roles.
 */

export const ROLES = {
  SUPER_ADMIN: 'super_admin',
  ORG_ADMIN: 'org_admin',
  MANAGER: 'manager',
  CONTENT_EDITOR: 'content_editor',
  ANALYST: 'analyst',
  LEARNER: 'learner',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const ALL_ROLES: Role[] = Object.values(ROLES);

/** Roles that may reach the admin console area. */
export const ADMIN_ROLES: Role[] = [
  ROLES.SUPER_ADMIN,
  ROLES.ORG_ADMIN,
  ROLES.MANAGER,
  ROLES.CONTENT_EDITOR,
];

/* ── UI personas: group the 6 RBAC roles into the 4 sidebar nav trees ── */
export type Persona = 'admin' | 'learner' | 'analyst' | 'super';

/** Map a raw RBAC role to its sidebar persona. */
export function roleToPersona(role: Role): Persona {
  switch (role) {
    case ROLES.LEARNER:
      return 'learner';
    case ROLES.ANALYST:
      return 'analyst';
    case ROLES.SUPER_ADMIN:
      return 'super';
    // org_admin | manager | content_editor -> "admin" persona for nav
    default:
      return 'admin';
  }
}

/**
 * Navigation item shape used by the Sidebar. `icon` is a Lucide icon name
 * resolved by the Sidebar; `section` groups items under a heading.
 */
export interface NavItem {
  /** Stable key for React lists. */
  key: string;
  /** Visible label. */
  label: string;
  /** Target route path. */
  path: string;
  /** Lucide icon name (resolved in Sidebar). */
  icon: string;
  /** Optional section heading the item lives under. */
  section?: string;
  /** Optional trailing badge (e.g. unread count). */
  badge?: string;
}

/** Persona-scoped nav trees, derived from the prototype's sidebar. */
export const NAV_BY_PERSONA: Record<Persona, NavItem[]> = {
  admin: [
    { key: 'dashboard', label: 'Tổng quan', path: '/dashboard', icon: 'grid', section: 'Quản trị · Admin' },
    { key: 'campaigns', label: 'Mô phỏng', path: '/campaigns/new', icon: 'triangle', section: 'Quản trị · Admin' },
    { key: 'users', label: 'Người dùng', path: '/users', icon: 'users', section: 'Quản trị · Admin' },
    { key: 'groups', label: 'Nhóm', path: '/groups', icon: 'layers', section: 'Quản trị · Admin' },
    { key: 'compliance', label: 'Tuân thủ', path: '/compliance', icon: 'clipboard-check', section: 'Quản trị · Admin' },
    { key: 'content', label: 'Content Studio', path: '/content/studio', icon: 'pen-square', section: 'Quản trị · Admin' },
    { key: 'audit', label: 'Nhật ký kiểm toán', path: '/super/audit', icon: 'file-text', section: 'Quản trị · Admin' },
    { key: 'org', label: 'Cài đặt tổ chức', path: '/settings/org', icon: 'settings', section: 'Hệ thống' },
    { key: 'gamification', label: 'Gamification', path: '/gamification', icon: 'award', section: 'Hệ thống' },
    { key: 'aida', label: 'AI Orchestration', path: '/aida', icon: 'circle-check', section: 'Hệ thống' },
  ],
  learner: [
    { key: 'learn', label: 'Cổng học viên', path: '/learn', icon: 'home', section: 'Học viên · Learner' },
    { key: 'courses', label: 'Khóa học', path: '/learn/courses', icon: 'book-open', section: 'Học viên · Learner' },
    { key: 'quiz', label: 'Bài kiểm tra', path: '/learn/quiz/1', icon: 'help-circle', section: 'Học viên · Learner' },
    { key: 'certificate', label: 'Chứng chỉ của tôi', path: '/certificates/1', icon: 'award', section: 'Học viên · Learner' },
  ],
  analyst: [
    { key: 'inbox', label: 'Hộp báo cáo', path: '/soc/inbox', icon: 'inbox', section: 'SOC Analyst' },
    { key: 'alerts', label: 'Trung tâm cảnh báo', path: '/soc/alerts', icon: 'bell', section: 'SOC Analyst' },
    { key: 'watchlist', label: 'Watchlist', path: '/soc/watchlist', icon: 'eye', section: 'SOC Analyst' },
  ],
  super: [
    { key: 'tenants', label: 'Quản trị Tenant', path: '/super/tenants', icon: 'monitor', section: 'Super Admin' },
    { key: 'scim', label: 'SCIM & SSO Config', path: '/super/scim', icon: 'users', section: 'Super Admin' },
    { key: 'audit', label: 'Nhật ký kiểm toán', path: '/super/audit', icon: 'file-text', section: 'Super Admin' },
  ],
};

/** Default landing route per raw role (used by the `/` redirect). */
export function defaultRouteForRole(role: Role): string {
  switch (roleToPersona(role)) {
    case 'learner':
      return '/learn';
    case 'analyst':
      return '/soc/inbox';
    case 'super':
      return '/super/tenants';
    case 'admin':
    default:
      return '/dashboard';
  }
}

/** Type guard for narrowing arbitrary strings to a known Role. */
export function isRole(value: string): value is Role {
  return (ALL_ROLES as string[]).includes(value);
}
