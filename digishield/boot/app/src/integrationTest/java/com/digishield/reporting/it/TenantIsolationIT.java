package com.digishield.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.shared.tenantcontext.TenantContext;
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
}
