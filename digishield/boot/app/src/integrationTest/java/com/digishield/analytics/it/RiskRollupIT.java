package com.digishield.analytics.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.digishield.analytics.application.RiskRollupService;
import com.digishield.analytics.domain.DepartmentRisk;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.shared.tenantcontext.SystemScope;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the risk rollup against a real PostgreSQL with the real migrations.
 *
 * <p>The unit tests for {@code RiskRollupService} mock the repositories, so they
 * say nothing about the three things most likely to break it in production: that
 * {@code deleteByTenantId} is a valid derived query the app role is allowed to
 * run, that the aggregates it writes satisfy the tables' RLS {@code WITH CHECK}
 * predicates, and that {@code risk_score.phish_prone_pct} survives a round trip
 * through the migration added alongside the job. Each of those passes a mocked
 * test whatever the database thinks.
 *
 * <p>Flyway runs here, unlike {@code TenantIsolationIT}, because the point is to
 * exercise the shipped schema rather than a hand-written stand-in. Connecting as
 * the owner and letting the aspect drop to {@code digishield_app} per
 * transaction reproduces the cluster's arrangement, so these writes meet the
 * same policies production applies.
 *
 * <p>Requires Docker.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=true",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.cache.type=none",
                "management.health.redis.enabled=false"
        })
class RiskRollupIT {

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
    private static final Instant NOW = Instant.parse("2026-07-27T02:20:00Z");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private RiskRollupService rollupService;
    @Autowired
    private DepartmentRiskRepository departmentRiskRepository;
    @Autowired
    private RiskScoreRepository riskScoreRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway migrates as the owner; the app then runs as digishield_app, the
        // NOBYPASSRLS role the migration creates — the same arrangement as the
        // cluster, so the writes below are checked by the real policies.
        registry.add("digishield.rls.app-role", () -> "digishield_app");
    }

    @BeforeEach
    void seed() throws Exception {
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("DELETE FROM risk_score");
            s.execute("DELETE FROM department_risk");
            s.execute("DELETE FROM risk_signal");
            s.execute("DELETE FROM app_user");
            s.execute("DELETE FROM tenant");

            insertTenant(s, TENANT_A, "Alpha");
            insertTenant(s, TENANT_B, "Beta");

            // Tenant A: two people in Kế toán, one of whom clicked; one in IT.
            UUID clicker = UUID.randomUUID();
            insertUser(s, TENANT_A, clicker, "clicker@a.test", "Kế toán");
            insertUser(s, TENANT_A, UUID.randomUUID(), "quiet@a.test", "Kế toán");
            insertUser(s, TENANT_A, UUID.randomUUID(), "it@a.test", "IT");
            insertSignal(s, TENANT_A, clicker);

            // Tenant B exists only to be left alone.
            insertUser(s, TENANT_B, UUID.randomUUID(), "someone@b.test", "Kế toán");
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void writesAggregatesTheDatabaseAccepts() {
        RiskRollupService.Summary summary = runAsTenant(TENANT_A, () -> rollupService.rollup(NOW));

        assertThat(summary.members()).isEqualTo(3);
        assertThat(summary.departments()).isEqualTo(2);

        List<DepartmentRisk> departments = runAsTenant(TENANT_A,
                () -> departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_A));
        assertThat(departments).extracting(DepartmentRisk::getName)
                .containsExactlyInAnyOrder("Kế toán", "IT");

        DepartmentRisk accounting = departments.stream()
                .filter(d -> "Kế toán".equals(d.getName())).findFirst().orElseThrow();
        assertThat(accounting.getHeadcount()).isEqualTo(2);
        assertThat(accounting.getPhishPronePct()).isEqualTo(50.0);
    }

    @Test
    void persistsTheMeasuredRateOnTheOrgScore() {
        runAsTenant(TENANT_A, () -> rollupService.rollup(NOW));

        List<RiskScore> org = runAsTenant(TENANT_A,
                () -> riskScoreRepository.findByTenantIdAndScope(TENANT_A, RiskScope.ORG));

        assertThat(org).hasSize(1);
        // One clicker among three people — read back from the column the
        // migration added, not from the object that was saved.
        assertThat(org.get(0).getPhishPronePct()).isEqualTo(33.3);
    }

    @Test
    void replacesItsOwnSnapshotWithoutAccumulatingRows() {
        runAsTenant(TENANT_A, () -> rollupService.rollup(NOW));
        runAsTenant(TENANT_A, () -> rollupService.rollup(NOW.plusSeconds(86_400)));

        List<DepartmentRisk> departments = runAsTenant(TENANT_A,
                () -> departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_A));
        // Two runs must leave two departments, not four — this is the assertion
        // that deleteByTenantId really deleted, rather than merely being a
        // method name the app role is not allowed to execute.
        assertThat(departments).hasSize(2);

        List<RiskScore> orgHistory = runAsTenant(TENANT_A,
                () -> riskScoreRepository.findByTenantIdAndScope(TENANT_A, RiskScope.ORG));
        // Scores are a history and must accumulate, unlike the snapshot.
        assertThat(orgHistory).hasSize(2);
    }

    @Test
    void leavesOtherTenantsUntouched() {
        runAsTenant(TENANT_A, () -> rollupService.rollup(NOW));

        List<DepartmentRisk> seenByB = runAsTenant(TENANT_B,
                () -> departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_B));
        assertThat(seenByB).isEmpty();

        // And A's delete did not reach across: run B, then confirm A still has
        // its rows.
        runAsTenant(TENANT_B, () -> rollupService.rollup(NOW));
        List<DepartmentRisk> seenByA = runAsTenant(TENANT_A,
                () -> departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_A));
        assertThat(seenByA).hasSize(2);
    }

    @Test
    void systemScopeRefusesToOpenOnARequestThread() {
        var attributes = new org.springframework.web.context.request.ServletRequestAttributes(
                new org.springframework.mock.web.MockHttpServletRequest());
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attributes);
        try {
            // The guard that keeps tenant-free access out of reach of a logged-in
            // caller, exercised rather than assumed.
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> SystemScope.call(() -> "should not run"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    private <T> T runAsTenant(UUID tenantId, Supplier<T> action) {
        TenantContext.set(tenantId.toString());
        try {
            return transactionTemplate.execute(status -> action.get());
        } finally {
            TenantContext.clear();
        }
    }

    private static void insertTenant(java.sql.Statement s, UUID tenantId, String name)
            throws java.sql.SQLException {
        s.execute("INSERT INTO tenant (id, tenant_id, name, tier, data_region, status) VALUES ('"
                + tenantId + "', '" + tenantId + "', '" + name + "', 'SILO', 'in-country', 'ACTIVE')");
    }

    private static void insertUser(java.sql.Statement s, UUID tenantId, UUID id, String email,
                                   String department) throws java.sql.SQLException {
        s.execute("INSERT INTO app_user (id, tenant_id, email, role, status, department) VALUES ('"
                + id + "', '" + tenantId + "', '" + email + "', 'LEARNER', 'ACTIVE', '" + department + "')");
    }

    private static void insertSignal(java.sql.Statement s, UUID tenantId, UUID userId)
            throws java.sql.SQLException {
        s.execute("INSERT INTO risk_signal (id, tenant_id, user_id, type, weight, occurred_at) VALUES ('"
                + UUID.randomUUID() + "', '" + tenantId + "', '" + userId
                + "', 'SIMULATION_CLICK', 25, now() - interval '1 day')");
    }
}
