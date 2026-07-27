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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RiskRollupService}.
 * <p>
 * Pure Mockito: no Spring context, no database. These cover the arithmetic that
 * decides what two dashboard panels say, which is easy to get subtly wrong in a
 * way nobody would notice from the UI.
 */
@ExtendWith(MockitoExtension.class)
class RiskRollupServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-27T02:20:00Z");

    @Mock
    private RiskSignalRepository riskSignalRepository;
    @Mock
    private RiskScoreRepository riskScoreRepository;
    @Mock
    private DepartmentRiskRepository departmentRiskRepository;
    @Mock
    private WorkforceDirectory workforce;

    @InjectMocks
    private RiskRollupService service;

    @Captor
    private ArgumentCaptor<List<DepartmentRisk>> departmentsCaptor;
    @Captor
    private ArgumentCaptor<List<RiskScore>> scoresCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void countsEveryMemberInTheDenominatorNotOnlyThoseWithSignals() {
        UUID clicker = UUID.randomUUID();
        UUID quiet1 = UUID.randomUUID();
        UUID quiet2 = UUID.randomUUID();
        UUID quiet3 = UUID.randomUUID();

        when(workforce.members()).thenReturn(List.of(
                member(clicker, "Kế toán"),
                member(quiet1, "Kế toán"),
                member(quiet2, "Kế toán"),
                member(quiet3, "Kế toán")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of(signal(clicker, RiskSignalType.SIMULATION_CLICK, 25)));

        service.rollup(NOW);

        verify(departmentRiskRepository).saveAll(departmentsCaptor.capture());
        DepartmentRisk dept = departmentsCaptor.getValue().get(0);

        assertThat(dept.getHeadcount()).isEqualTo(4);
        // One of four people clicked.
        assertThat(dept.getPhishPronePct()).isEqualTo(25.0);
        // (30 + 5 + 5 + 5) / 4 — the three with no signals score the baseline,
        // rather than being left out and making the department look worse.
        assertThat(dept.getRiskScore()).isEqualTo(11);
    }

    @Test
    void countsRepeatClickersOnceEach() {
        UUID repeat = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        when(workforce.members()).thenReturn(List.of(member(repeat, "IT"), member(other, "IT")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of(
                        signal(repeat, RiskSignalType.SIMULATION_CLICK, 25),
                        signal(repeat, RiskSignalType.SIMULATION_CLICK, 25),
                        signal(repeat, RiskSignalType.SIMULATION_CLICK, 25)));

        service.rollup(NOW);

        verify(departmentRiskRepository).saveAll(departmentsCaptor.capture());
        // Three clicks by one person out of two people is 50%, not 150%.
        assertThat(departmentsCaptor.getValue().get(0).getPhishPronePct()).isEqualTo(50.0);
    }

    @Test
    void vigilantBehaviourLowersRiskWithoutCountingAsPhishProne() {
        UUID reporter = UUID.randomUUID();

        when(workforce.members()).thenReturn(List.of(member(reporter, "IT")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of(signal(reporter, RiskSignalType.PHISHING_REPORT_CONFIRMED, -15)));

        service.rollup(NOW);

        verify(departmentRiskRepository).saveAll(departmentsCaptor.capture());
        DepartmentRisk dept = departmentsCaptor.getValue().get(0);
        // 5 - 15 clamps at the floor rather than going negative.
        assertThat(dept.getRiskScore()).isEqualTo(RiskScoring.MIN_RISK);
        assertThat(dept.getPhishPronePct()).isZero();
    }

    @Test
    void groupsPeopleWithoutADepartmentTogetherRatherThanDroppingThem() {
        when(workforce.members()).thenReturn(List.of(
                member(UUID.randomUUID(), "IT"),
                member(UUID.randomUUID(), null),
                member(UUID.randomUUID(), "   ")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of());

        RiskRollupService.Summary summary = service.rollup(NOW);

        verify(departmentRiskRepository).saveAll(departmentsCaptor.capture());
        assertThat(departmentsCaptor.getValue()).hasSize(2);
        assertThat(departmentsCaptor.getValue())
                .extracting(DepartmentRisk::getHeadcount)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(summary.members()).isEqualTo(3);
    }

    @Test
    void writesOneOrgScoreCarryingTheMeasuredRate() {
        UUID clicker = UUID.randomUUID();
        when(workforce.members()).thenReturn(List.of(
                member(clicker, "IT"), member(UUID.randomUUID(), "Kế toán")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of(signal(clicker, RiskSignalType.SIMULATION_CLICK, 25)));

        service.rollup(NOW);

        verify(riskScoreRepository).saveAll(scoresCaptor.capture());
        List<RiskScore> org = scoresCaptor.getValue().stream()
                .filter(s -> s.getScope() == RiskScope.ORG)
                .toList();

        assertThat(org).hasSize(1);
        assertThat(org.get(0).getScopeId()).isEqualTo(TENANT_ID);
        assertThat(org.get(0).getComputedAt()).isEqualTo(NOW);
        // One clicker in two people, across both departments.
        assertThat(org.get(0).getPhishPronePct()).isEqualTo(50.0);
    }

    @Test
    void keepsADepartmentsHistoryUnderOneStableIdAcrossRuns() {
        when(workforce.members()).thenReturn(List.of(member(UUID.randomUUID(), "IT")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of());

        service.rollup(NOW);
        service.rollup(NOW.plusSeconds(86_400));

        verify(riskScoreRepository, org.mockito.Mockito.times(2)).saveAll(scoresCaptor.capture());
        List<UUID> deptScopeIds = scoresCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(s -> s.getScope() == RiskScope.DEPT)
                .map(RiskScore::getScopeId)
                .distinct()
                .toList();

        // Two runs, one department: a fresh id per run would make the trend
        // chart show a new department every night instead of a history.
        assertThat(deptScopeIds).hasSize(1);
    }

    @Test
    void replacesThePreviousSnapshotBeforeWritingTheNewOne() {
        when(workforce.members()).thenReturn(List.of(member(UUID.randomUUID(), "IT")));
        when(riskSignalRepository.findByTenantIdAndOccurredAtAfter(eq(TENANT_ID), any()))
                .thenReturn(List.of());

        service.rollup(NOW);

        org.mockito.InOrder order =
                org.mockito.Mockito.inOrder(departmentRiskRepository);
        order.verify(departmentRiskRepository).deleteByTenantId(TENANT_ID);
        order.verify(departmentRiskRepository).saveAll(any());
    }

    @Test
    void writesNothingForATenantWithNoPeople() {
        when(workforce.members()).thenReturn(List.of());

        RiskRollupService.Summary summary = service.rollup(NOW);

        // A zero here would render exactly like a measured zero. An empty panel
        // is the honest output when there is no workforce to measure.
        verify(departmentRiskRepository, never()).saveAll(any());
        verify(riskScoreRepository, never()).saveAll(any());
        verify(departmentRiskRepository, never()).deleteByTenantId(any());
        assertThat(summary.members()).isZero();
    }

    private static WorkforceDirectory.Member member(UUID id, String department) {
        return new WorkforceDirectory.Member(id, department);
    }

    private static RiskSignal signal(UUID userId, RiskSignalType type, int weight) {
        return new RiskSignal(UUID.randomUUID(), TENANT_ID, userId, type, weight, NOW.minusSeconds(3600));
    }
}
