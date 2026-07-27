package com.digishield.analytics.application;

import com.digishield.analytics.api.WorkforceDirectory;
import com.digishield.analytics.domain.DepartmentRisk;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.domain.RiskScoring;
import com.digishield.analytics.domain.RiskSignal;
import com.digishield.analytics.domain.RiskSignalType;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.analytics.infrastructure.RiskSignalRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns individual risk signals into the aggregates the dashboard reads:
 * a {@link DepartmentRisk} row per department and an org-wide {@link RiskScore}
 * point.
 * <p>
 * Nothing computed these before. {@code department_risk} had exactly one writer,
 * a {@code @Profile("dev")} seeder holding a hardcoded list of departments, so
 * in production the department panel and the org risk trend were permanently
 * empty — the signals were being recorded faithfully and then never added up.
 * <p>
 * Runs against whatever tenant is in {@link TenantContext}; the caller sets it
 * one tenant at a time so every query here is scoped by RLS exactly as a request
 * would be.
 */
@Service
public class RiskRollupService {

    private static final Logger log = LoggerFactory.getLogger(RiskRollupService.class);

    /** Bucket for people whose department is unset, so they still get counted. */
    private static final String UNASSIGNED = "—";

    private final RiskSignalRepository riskSignalRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final DepartmentRiskRepository departmentRiskRepository;
    private final WorkforceDirectory workforce;

    public RiskRollupService(RiskSignalRepository riskSignalRepository,
                             RiskScoreRepository riskScoreRepository,
                             DepartmentRiskRepository departmentRiskRepository,
                             WorkforceDirectory workforce) {
        this.riskSignalRepository = riskSignalRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.departmentRiskRepository = departmentRiskRepository;
        this.workforce = workforce;
    }

    /**
     * Recomputes department and org aggregates for the tenant currently in
     * context.
     *
     * @param now instant the rollup is stamped with, passed in so a caller
     *            processing several tenants gives them all the same timestamp
     * @return what was written, for logging
     */
    @Transactional
    public Summary rollup(Instant now) {
        UUID tenantId = TenantContext.requireUuid();
        List<WorkforceDirectory.Member> members = workforce.members();

        if (members.isEmpty()) {
            // No people means no denominator. Writing a zero here would render as
            // a real measurement of a real workforce, which is worse than a panel
            // that is visibly empty.
            log.debug("Risk rollup skipped for tenant {}: no members", tenantId);
            return new Summary(0, 0, 0, 0.0);
        }

        Map<UUID, List<RiskSignal>> signalsByUser = riskSignalRepository
                .findByTenantIdAndOccurredAtAfter(tenantId, RiskScoring.windowStart(now))
                .stream()
                .collect(Collectors.groupingBy(RiskSignal::getUserId));

        // Everyone who clicked at least once in the window. A user with several
        // clicks is still one phish-prone person, so this is a set, not a count.
        Set<UUID> clickers = signalsByUser.entrySet().stream()
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> s.getType() == RiskSignalType.SIMULATION_CLICK))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Map<String, List<WorkforceDirectory.Member>> byDepartment = members.stream()
                .collect(Collectors.groupingBy(RiskRollupService::departmentOf,
                        LinkedHashMap::new, Collectors.toList()));

        List<DepartmentRisk> departments = new ArrayList<>();
        List<RiskScore> scores = new ArrayList<>();

        byDepartment.forEach((name, people) -> {
            Group group = summarise(people, signalsByUser, clickers);
            departments.add(new DepartmentRisk(UUID.randomUUID(), tenantId, name,
                    group.risk(), group.phishPronePct(), people.size()));
            // Department history keyed by a stable id derived from the name:
            // department names are what the workforce exposes, and a random id
            // per run would make every rollup look like a new department.
            scores.add(new RiskScore(UUID.randomUUID(), tenantId, RiskScope.DEPT,
                    departmentScopeId(tenantId, name), group.risk(), now, group.phishPronePct()));
        });

        Group org = summarise(members, signalsByUser, clickers);
        scores.add(new RiskScore(UUID.randomUUID(), tenantId, RiskScope.ORG, tenantId,
                org.risk(), now, org.phishPronePct()));

        // department_risk is a snapshot of "how things stand", not a history, and
        // the dashboard reads all of a tenant's rows. Leaving the previous run's
        // rows behind would show every department twice, and a department that
        // has since been dissolved forever.
        departmentRiskRepository.deleteByTenantId(tenantId);
        departmentRiskRepository.saveAll(departments);
        riskScoreRepository.saveAll(scores);

        log.info("Risk rollup for tenant {}: {} members, {} departments, org risk {}, phish-prone {}%",
                tenantId, members.size(), departments.size(), org.risk(), org.phishPronePct());
        return new Summary(members.size(), departments.size(), org.risk(), org.phishPronePct());
    }

    /**
     * Aggregates one group of people: mean risk across <em>all</em> of them, and
     * the share who clicked.
     * <p>
     * People with no signals are scored too, at the baseline. Averaging only
     * over those who generated signals would make a department look worse the
     * more of its members behaved well, since good behaviour mostly shows up as
     * an absence of clicks.
     */
    private static Group summarise(Collection<WorkforceDirectory.Member> people,
                                   Map<UUID, List<RiskSignal>> signalsByUser,
                                   Set<UUID> clickers) {
        int total = 0;
        int clicked = 0;
        for (WorkforceDirectory.Member member : people) {
            total += RiskScoring.score(signalsByUser.getOrDefault(member.userId(), List.of()));
            if (clickers.contains(member.userId())) {
                clicked++;
            }
        }
        int risk = RiskScoring.clamp(Math.round((float) total / people.size()));
        double pct = Math.round(clicked * 1000.0 / people.size()) / 10.0;
        return new Group(risk, pct);
    }

    private static String departmentOf(WorkforceDirectory.Member member) {
        String department = member.department();
        return department == null || department.isBlank() ? UNASSIGNED : department.trim();
    }

    /**
     * Stable id for a department's score history. The workforce identifies
     * departments by name, so the history has to be keyed off the name; hashing
     * it with the tenant keeps two tenants' identically named departments apart.
     */
    private static UUID departmentScopeId(UUID tenantId, String name) {
        return UUID.nameUUIDFromBytes((tenantId + ":" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Group(int risk, double phishPronePct) {
    }

    /**
     * What one tenant's rollup produced.
     *
     * @param members        people considered
     * @param departments    department rows written
     * @param orgRisk        org-wide mean risk
     * @param phishPronePct  org-wide share (%) who clicked
     */
    public record Summary(int members, int departments, int orgRisk, double phishPronePct) {
    }
}
