package com.digishield.analytics.application;

import com.digishield.analytics.domain.DepartmentRisk;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo analytics data (org/department risk scores) for the {@code dev}
 * profile so the admin dashboard renders against a real datasource.
 * <p>
 * All rows are scoped to the fixed demo tenant.
 */
@Component
@Profile("dev | seed")
@Order(20)
public class AnalyticsDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;

    private final RiskScoreRepository riskScoreRepository;
    private final DepartmentRiskRepository departmentRiskRepository;

    public AnalyticsDevSeeder(RiskScoreRepository riskScoreRepository,
                              DepartmentRiskRepository departmentRiskRepository) {
        this.riskScoreRepository = riskScoreRepository;
        this.departmentRiskRepository = departmentRiskRepository;
    }

    @Override
    public void run(String... args) {
        if (!departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(DEMO_TENANT).isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        // Org-level risk history (~3 months) — powers the dashboard trend chart.
        // {daysAgo, score}, oldest first; the latest point (below) is the current score.
        int[][] history = {
                {95, 59}, {80, 58}, {65, 60}, {50, 61}, {35, 59},
                {25, 62}, {17, 63}, {11, 64}, {5, 63}};
        for (int[] point : history) {
            riskScoreRepository.save(new RiskScore(
                    UUID.randomUUID(), DEMO_TENANT, RiskScope.ORG, DEMO_TENANT, point[1],
                    now.minus(point[0], ChronoUnit.DAYS)));
        }
        // Current org-level risk score (~62), used by the dashboard + GET /analytics/risk.
        riskScoreRepository.save(new RiskScore(
                UUID.randomUUID(), DEMO_TENANT, RiskScope.ORG, DEMO_TENANT, 62,
                now.minus(1, ChronoUnit.HOURS)));

        // Named departments (with both an aggregate row and a scoped risk score).
        record Dept(String name, int risk, double phishPct, int headcount) {
        }
        List<Dept> depts = List.of(
                new Dept("Kế toán", 78, 24.0, 18),
                new Dept("Kinh doanh", 64, 19.0, 34),
                new Dept("Hành chính", 41, 12.0, 22),
                new Dept("IT", 32, 8.0, 14),
                new Dept("HR", 27, 6.5, 9));

        for (Dept d : depts) {
            UUID deptId = UUID.randomUUID();
            departmentRiskRepository.save(new DepartmentRisk(
                    deptId, DEMO_TENANT, d.name(), d.risk(), d.phishPct(), d.headcount()));
            riskScoreRepository.save(new RiskScore(
                    UUID.randomUUID(), DEMO_TENANT, RiskScope.DEPT, deptId, d.risk(),
                    now.minus(1, ChronoUnit.HOURS)));
        }
    }
}
