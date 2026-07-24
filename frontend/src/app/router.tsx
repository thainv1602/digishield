import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/shared/ui';
import { RequireRole } from './auth/RequireRole';
import { ROLES, defaultRouteForRole, type Role } from './auth/roles';
import { useAuth } from './auth/useAuth';

/* ── Auth (owned here) — login delegates to the Cognito hosted UI, which also
   handles MFA, SSO, password reset and onboarding externally. ── */
const LoginPage = lazy(() => import('@/features/auth/LoginPage'));

/* ── Admin / Super (Agent 2) ── */
const AdminDashboardPage = lazy(() => import('@/features/dashboard/AdminDashboardPage'));
const CampaignWizardPage = lazy(() => import('@/features/campaigns/CampaignWizardPage'));
const CampaignResultsPage = lazy(() => import('@/features/campaigns/CampaignResultsPage'));
const UsersPage = lazy(() => import('@/features/users/UsersPage'));
const CompliancePage = lazy(() => import('@/features/compliance/CompliancePage'));
const ContentStudioPage = lazy(() => import('@/features/content/ContentStudioPage'));
const OrgSettingsPage = lazy(() => import('@/features/admin/OrgSettingsPage'));
const GamificationPage = lazy(() => import('@/features/admin/GamificationPage'));
const AidaPage = lazy(() => import('@/features/admin/AidaPage'));
const TenantConsolePage = lazy(() => import('@/features/super/TenantConsolePage'));
const ScimConfigPage = lazy(() => import('@/features/super/ScimConfigPage'));
const AuditLogPage = lazy(() => import('@/features/super/AuditLogPage'));

/* ── Learner / SOC (Agent 3) ── */
const LearnerPortalPage = lazy(() => import('@/features/learning/LearnerPortalPage'));
const CourseCatalogPage = lazy(() => import('@/features/learning/CourseCatalogPage'));
const LessonPlayerPage = lazy(() => import('@/features/learning/LessonPlayerPage'));
const QuizPage = lazy(() => import('@/features/learning/QuizPage'));
const QuizResultsPage = lazy(() => import('@/features/learning/QuizResultsPage'));
const CertificatePage = lazy(() => import('@/features/certificates/CertificatePage'));
const SocInboxPage = lazy(() => import('@/features/soc/SocInboxPage'));
const AlertCenterPage = lazy(() => import('@/features/soc/AlertCenterPage'));
const WatchlistPage = lazy(() => import('@/features/soc/WatchlistPage'));

/* ── Role groupings ── */
const ADMIN: Role[] = [ROLES.ORG_ADMIN, ROLES.MANAGER, ROLES.CONTENT_EDITOR, ROLES.SUPER_ADMIN];
const SUPER: Role[] = [ROLES.SUPER_ADMIN];
const LEARNER: Role[] = [ROLES.LEARNER];
const ANALYST: Role[] = [ROLES.ANALYST];

function PageFallback() {
  return (
    <div
      role="status"
      aria-live="polite"
      style={{ padding: 'var(--space-6)', color: 'var(--color-muted)' }}
    >
      Đang tải…
    </div>
  );
}

function Forbidden() {
  return (
    <div style={{ padding: 'var(--space-10)', textAlign: 'center' }}>
      <h1 style={{ fontSize: 'var(--font-size-2xl)', color: 'var(--color-navy)' }}>403</h1>
      <p style={{ color: 'var(--color-muted)' }}>
        Bạn không có quyền truy cập trang này.
      </p>
    </div>
  );
}

function NotFound() {
  return (
    <div style={{ padding: 'var(--space-10)', textAlign: 'center' }}>
      <h1 style={{ fontSize: 'var(--font-size-2xl)', color: 'var(--color-navy)' }}>404</h1>
      <p style={{ color: 'var(--color-muted)' }}>Không tìm thấy trang.</p>
    </div>
  );
}

/** Redirect the index route to the user's role-appropriate landing page. */
function RootRedirect() {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated || !user) return <Navigate to="/login" replace />;
  return <Navigate to={defaultRouteForRole(user.role)} replace />;
}

/**
 * Guard a page behind a role check and wrap it in the app shell.
 * AppShell renders from this single place so every authenticated page
 * shows the sidebar/menu exactly once (the shell derives its title from
 * the route via Topbar). Pages render only their inner content.
 */
function guarded(allow: Role[], Page: ComponentType): ReactNode {
  return (
    <RequireRole allow={allow}>
      <AppShell>
        <Page />
      </AppShell>
    </RequireRole>
  );
}

export function AppRouter() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        {/* ── Public / Auth ── */}
        <Route path="/login" element={<LoginPage />} />

        {/* ── Admin / Super ── */}
        <Route path="/dashboard" element={guarded(ADMIN, AdminDashboardPage)} />
        <Route path="/campaigns/new" element={guarded(ADMIN, CampaignWizardPage)} />
        <Route path="/campaigns/:id" element={guarded(ADMIN, CampaignResultsPage)} />
        <Route path="/users" element={guarded(ADMIN, UsersPage)} />
        <Route path="/compliance" element={guarded(ADMIN, CompliancePage)} />
        <Route path="/content/studio" element={guarded(ADMIN, ContentStudioPage)} />
        <Route path="/settings/org" element={guarded(ADMIN, OrgSettingsPage)} />
        <Route path="/gamification" element={guarded(ADMIN, GamificationPage)} />
        <Route path="/aida" element={guarded(ADMIN, AidaPage)} />
        <Route path="/super/tenants" element={guarded(SUPER, TenantConsolePage)} />
        <Route path="/super/scim" element={guarded(SUPER, ScimConfigPage)} />
        <Route path="/super/audit" element={guarded(ADMIN, AuditLogPage)} />

        {/* ── Learner ── */}
        <Route path="/learn" element={guarded(LEARNER, LearnerPortalPage)} />
        <Route path="/learn/courses" element={guarded(LEARNER, CourseCatalogPage)} />
        <Route path="/learn/lessons/:id" element={guarded(LEARNER, LessonPlayerPage)} />
        <Route path="/learn/quiz/:id" element={guarded(LEARNER, QuizPage)} />
        <Route path="/learn/quiz/:id/results" element={guarded(LEARNER, QuizResultsPage)} />
        <Route path="/certificates/:id" element={guarded(LEARNER, CertificatePage)} />

        {/* ── SOC / Analyst ── */}
        <Route path="/soc/inbox" element={guarded(ANALYST, SocInboxPage)} />
        <Route path="/soc/alerts" element={guarded(ANALYST, AlertCenterPage)} />
        <Route path="/soc/watchlist" element={guarded(ANALYST, WatchlistPage)} />

        {/* ── Utility ── */}
        <Route path="/403" element={<Forbidden />} />
        <Route path="/" element={<RootRedirect />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}
