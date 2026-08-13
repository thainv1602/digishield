package com.digishield.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.AiLabelCount;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.shared.tenantcontext.PlatformScope;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The flagship integration test: it proves that PostgreSQL Row-Level Security
 * (RLS) genuinely isolates tenants in DigiShield.
 *
 * <p>Design notes / how isolation is made <em>real</em> in this test:
 * <ul>
 *   <li>A real PostgreSQL is started via Testcontainers and wired into the
 *       Spring context through {@link DynamicPropertySource}.</li>
 *   <li>Testcontainers' default DB user is the table owner and a superuser, and
 *       PostgreSQL <strong>bypasses RLS for owners/superusers</strong>. To make
 *       isolation real we run {@code it/rls-setup.sql}, which declares the
 *       {@code phishing_report} table with
 *       {@code ALTER TABLE phishing_report FORCE ROW LEVEL SECURITY;} so the
 *       policy is enforced even for the connecting role.</li>
 *   <li>The same setup script creates the RLS policy filtering on the session
 *       GUC {@code app.tenant_id}. In production this GUC is set per transaction
 *       by {@code RlsTenantAspect} via
 *       {@code set_config('app.tenant_id', ?, true)}; here that aspect runs for
 *       free because every repository call below goes through a
 *       {@code @Transactional} unit of work.</li>
 *   <li>Flyway is disabled (as in production boot) and Hibernate DDL is set to
 *       {@code none}; the test owns the schema via the setup script. The setup
 *       DDL is kept in sync with the real Flyway migration's
 *       {@code phishing_report} table, which matches the {@link PhishingReport}
 *       JPA entity ({@code tenant_id} as uuid, plus
 *       {@code user_id / payload / ai_label / ai_confidence / status}), so the
 *       test exercises the same shape that production would migrate.</li>
 * </ul>
 *
 * <p>Requires Docker and JDK 25 to run; it executes on the developer machine,
 * not in CI-less environments.
 */
@SpringBootTest(
        properties = {
                // Own the schema from the test setup script, not from Hibernate.
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=false",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                // No Redis in this IT: use a no-op cache and skip Redis health.
                "spring.cache.type=none",
                "management.health.redis.enabled=false"
        })
class TenantIsolationIT {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        }
    }

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private PhishingReportRepository reportRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            try (var stmt = conn.createStatement()) {
                // Create non-superuser role for RLS test enforcement
                stmt.execute("CREATE ROLE normal_user WITH LOGIN PASSWORD 'normal_pass' NOSUPERUSER NOBYPASSRLS");
                stmt.execute("GRANT ALL PRIVILEGES ON DATABASE " + POSTGRES.getDatabaseName() + " TO normal_user");
                stmt.execute("GRANT ALL ON SCHEMA public TO normal_user");
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to initialize normal_user for RLS testing", e);
        }

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "normal_user");
        registry.add("spring.datasource.password", () -> "normal_pass");
        // The app already connects as a non-superuser here, so RLS is enforced by
        // the connection role — disable the aspect's SET LOCAL ROLE (there is no
        // digishield_app role in this test DB).
        registry.add("digishield.rls.app-role", () -> "");
    }

    @BeforeAll
    static void ensureContainerRunning() {
        // @Testcontainers starts the container; this is just an explicit guard.
        assertThat(POSTGRES.isRunning()).isTrue();
    }

    /**
     * Recreate the RLS-protected table before each test so the dataset is clean
     * and FORCE ROW LEVEL SECURITY is in place. Runs as the owner with no
     * app.tenant_id set, which is fine: DDL is not subject to the row policy.
     */
    @BeforeEach
    void setUpSchema() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("it/rls-setup.sql"));
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * Saves one report for tenant A and one for tenant B, then asserts that each
     * tenant sees only its own row when querying through the JPA repository.
     * The RlsTenantAspect sets app.tenant_id for each transactional block, so the
     * RLS policy transparently scopes every SELECT.
     */
    @Test
    void rlsIsolatesReportsBetweenTenants() {
        UUID reportA = saveReportForTenant(TENANT_A);
        UUID reportB = saveReportForTenant(TENANT_B);

        // Tenant A sees only its own report.
        List<PhishingReport> seenByA = runAsTenant(TENANT_A, () -> reportRepository.findAll());
        assertThat(seenByA).extracting(PhishingReport::getId).containsExactly(reportA);
        assertThat(seenByA).extracting(PhishingReport::getTenantId).containsOnly(TENANT_A);

        // Tenant B sees only its own report, and zero of tenant A's rows.
        List<PhishingReport> seenByB = runAsTenant(TENANT_B, () -> reportRepository.findAll());
        assertThat(seenByB).extracting(PhishingReport::getId).containsExactly(reportB);
        assertThat(seenByB).extracting(PhishingReport::getId).doesNotContain(reportA);

        // Even a derived query that explicitly filters by the *other* tenant's id
        // returns nothing, because RLS filters before the WHERE clause is applied.
        List<PhishingReport> crossTenant =
                runAsTenant(TENANT_B, () -> reportRepository.findByTenantId(TENANT_A));
        assertThat(crossTenant).isEmpty();
    }

    /**
     * Fail-closed behaviour. Two complementary checks:
     * <ul>
     *   <li>Running a repository query inside a transaction with NO TenantContext
     *       fails fast, because RlsTenantAspect calls TenantContext.require().</li>
     *   <li>Running a raw query (bypassing the aspect) with app.tenant_id unset
     *       returns ZERO rows, because the RLS predicate
     *       {@code tenant_id = current_setting('app.tenant_id', true)::uuid}
     *       evaluates against NULL and matches nothing.</li>
     * </ul>
     */
    @Test
    void queryingWithoutTenantContextFailsClosed() {
        // Seed data while a tenant is set.
        saveReportForTenant(TENANT_A);

        // (1) Aspect-enforced path: no tenant => require() throws.
        TenantContext.clear();
        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> reportRepository.findAll()))
                .isInstanceOf(IllegalStateException.class);

        // (2) Pure-DB path: with app.tenant_id never set, RLS hides everything.
        long visibleWithNoGuc = countRowsWithoutTenantGuc();
        assertThat(visibleWithNoGuc).isZero();
    }

    /**
     * The dashboard's open-alert tile is a GROUP BY, not a row fetch. An
     * aggregate is exactly where a leak would be hardest to notice — a wrong
     * count carries no tenant id with it — so this pins that RLS filters the
     * rows before they are counted, and that closed reports are left out.
     */
    @Test
    void openAlertCountsAreAggregatedPerTenantAndOnlyOverOpenStatuses() {
        Instant now = Instant.now();
        saveReport(TENANT_A, AiLabel.THREAT, ReportStatus.SUBMITTED, now);
        saveReport(TENANT_A, AiLabel.THREAT, ReportStatus.TRIAGING, now);
        saveReport(TENANT_A, AiLabel.SPAM, ReportStatus.SUBMITTED, now);
        // Already dealt with: not an open alert.
        saveReport(TENANT_A, AiLabel.THREAT, ReportStatus.CONFIRMED, now);
        saveReport(TENANT_A, AiLabel.SPAM, ReportStatus.DISMISSED, now);
        // Another tenant's open threats must not reach tenant A's total.
        saveReport(TENANT_B, AiLabel.THREAT, ReportStatus.SUBMITTED, now);
        saveReport(TENANT_B, AiLabel.THREAT, ReportStatus.SUBMITTED, now);

        List<AiLabelCount> forA = runAsTenant(TENANT_A, () ->
                reportRepository.countByAiLabel(TENANT_A, ReportStatus.openStatuses()));

        assertThat(forA)
                .as("two open threats and one open spam, ignoring the confirmed and dismissed rows")
                .containsExactlyInAnyOrder(
                        new AiLabelCount(AiLabel.THREAT, 2L),
                        new AiLabelCount(AiLabel.SPAM, 1L));

        List<AiLabelCount> forB = runAsTenant(TENANT_B, () ->
                reportRepository.countByAiLabel(TENANT_B, ReportStatus.openStatuses()));

        assertThat(forB).containsExactly(new AiLabelCount(AiLabel.THREAT, 2L));
    }

    /**
     * The recent-reports panel asks for a handful. This checks the limit is
     * applied by the database in the right order, rather than the caller
     * fetching everything and keeping the head.
     */
    @Test
    void recentReportsAreTheNewestFewAndStayWithinTheTenant() {
        Instant now = Instant.now();
        saveReport(TENANT_A, AiLabel.CLEAN, ReportStatus.SUBMITTED, now.minus(Duration.ofDays(3)));
        saveReport(TENANT_A, AiLabel.SPAM, ReportStatus.SUBMITTED, now.minus(Duration.ofDays(2)));
        UUID newest = saveReport(TENANT_A, AiLabel.THREAT, ReportStatus.SUBMITTED, now);
        saveReport(TENANT_B, AiLabel.THREAT, ReportStatus.SUBMITTED, now.plus(Duration.ofDays(1)));

        List<PhishingReport> head = runAsTenant(TENANT_A, () ->
                reportRepository.findByTenantIdOrderByReportedAtDesc(TENANT_A, PageRequest.of(0, 2)));

        assertThat(head).hasSize(2);
        assertThat(head.getFirst().getId()).isEqualTo(newest);
        // Tenant B's row is newer than every one of A's, and still absent.
        assertThat(head).extracting(PhishingReport::getTenantId).containsOnly(TENANT_A);
    }

    private UUID saveReport(UUID tenantId, AiLabel label, ReportStatus status, Instant reportedAt) {
        return runAsTenant(tenantId, () -> {
            PhishingReport report = new PhishingReport(
                    UUID.randomUUID(), tenantId, UUID.randomUUID(), "suspicious-email-payload",
                    label, 0.9, status, null, null, "kegian@lua-dao.vn", null, false, reportedAt);
            return reportRepository.save(report).getId();
        });
    }

    private UUID saveReportForTenant(UUID tenantId) {
        return runAsTenant(tenantId, () -> {
            PhishingReport report = new PhishingReport(
                    UUID.randomUUID(),
                    tenantId,
                    UUID.randomUUID(),
                    "suspicious-email-payload",
                    AiLabel.THREAT,
                    0.97,
                    ReportStatus.SUBMITTED);
            return reportRepository.save(report).getId();
        });
    }

    /**
     * Runs the supplied work inside a transaction with the given tenant in the
     * TenantContext, so RlsTenantAspect emits the matching set_config call.
     */
    private <T> T runAsTenant(UUID tenantId, java.util.function.Supplier<T> work) {
        TenantContext.set(tenantId.toString());
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Opens a fresh connection and counts rows WITHOUT ever setting app.tenant_id,
     * demonstrating that the RLS policy is fail-closed at the database layer.
     */
    private long countRowsWithoutTenantGuc() {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rs = statement.executeQuery("SELECT count(*) FROM phishing_report")) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to count rows", e);
        }
    }

    /**
     * Regression for a bug every unit test missed and production found.
     *
     * <p>Entering a tenant writes an audit entry belonging to that tenant while the
     * operator is still in their own context — a cross-tenant write. Mocked
     * repositories have no RLS, so the unit tests passed; against a real database
     * the insert was refused and the trail for the most sensitive action there is
     * silently never appeared.
     *
     * <p>The fix satisfies the policy instead of bypassing it: the write runs as
     * the target tenant. This test pins both halves — refused from the wrong
     * context, accepted from the right one — and needs no elevated privilege, so
     * it also holds on a deployment that connects as an unprivileged role.
     */
    @Test
    void aWriteForAnotherTenantMustRunAsThatTenant() {
        UUID actingFrom = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertThatThrownBy(() -> runAsTenant(actingFrom, () ->
                reportRepository.save(report(target))))
                .hasMessageContaining("row-level security");

        UUID id = runAsTenant(target, () -> reportRepository.save(report(target)).getId());

        assertThat(runAsTenant(target, () -> reportRepository.findById(id).isPresent())).isTrue();
        assertThat(runAsTenant(actingFrom, () -> reportRepository.findById(id).isPresent())).isFalse();
    }

    /**
     * PlatformScope skips the tenant GUC so a super admin can read across tenants.
     * That works only while the connection can bypass RLS — as a superuser does.
     * Here the role is unprivileged and the table is FORCE'd, so skipping the GUC
     * leaves the policy comparing against NULL and the write is refused.
     *
     * <p>Pinning it makes the dependency visible instead of latent: cross-tenant
     * *reads* still rest on the production connection being a superuser, and that
     * wants replacing with an explicit predicate in the policies.
     */
    @Test
    void platformScopeDoesNotSubstituteForPrivilegeOnAnUnprivilegedConnection() {
        SecurityContextHolder.getContext().setAuthentication(authentication("ROLE_SUPER_ADMIN"));
        try {
            UUID actingFrom = UUID.randomUUID();
            UUID target = UUID.randomUUID();
            assertThatThrownBy(() -> runAsTenant(actingFrom, () ->
                    PlatformScope.call(() -> reportRepository.save(report(target)))))
                    .hasMessageContaining("row-level security");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** PlatformScope refuses to open for anyone else, so it is not a general bypass. */
    @Test
    void platformScopeStaysShutForANonSuperAdmin() {
        SecurityContextHolder.getContext().setAuthentication(authentication("ROLE_ORG_ADMIN"));
        try {
            assertThatThrownBy(() -> PlatformScope.call(() -> "nope"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUPER_ADMIN");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static PhishingReport report(UUID tenantId) {
        return new PhishingReport(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                "cross-tenant", AiLabel.THREAT, 0.5, ReportStatus.SUBMITTED);
    }

    private static org.springframework.security.core.Authentication authentication(String authority) {
        var token = new org.springframework.security.authentication.TestingAuthenticationToken(
                "operator", null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority)));
        token.setAuthenticated(true);
        return token;
    }
}
