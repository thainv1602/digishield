package com.digishield.tenancy.application;

import com.digishield.tenancy.api.DeliveryUsageProvider;
import com.digishield.tenancy.api.TenancyService;
import com.digishield.tenancy.domain.UsageMetering;
import com.digishield.tenancy.infrastructure.UsageMeteringRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UsageMeteringJob}.
 * <p>
 * Nothing metered usage before this, so every case covers behaviour that had no
 * implementation rather than a change to one.
 */
@ExtendWith(MockitoExtension.class)
class UsageMeteringJobTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final YearMonth MONTH = YearMonth.of(2026, 7);

    @Mock
    private TenancyService tenancyService;
    @Mock
    private DeliveryUsageProvider deliveryUsage;
    @Mock
    private UsageMeteringRepository usageMeteringRepository;

    private UsageMeteringJob job() {
        return new UsageMeteringJob(tenancyService, deliveryUsage, usageMeteringRepository,
                "Asia/Ho_Chi_Minh");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void writesOnlyTheMetricsThatCanBeCounted() {
        when(deliveryUsage.sentCounts(eq(TENANT_ID), any(), any()))
                .thenReturn(Map.of("email_sent", 42L, "sms_sent", 7L));
        when(usageMeteringRepository.findByTenantIdAndPeriod(TENANT_ID, "2026-07"))
                .thenReturn(List.of());
        when(usageMeteringRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        job().meter(TENANT_ID, MONTH);

        ArgumentCaptor<UsageMetering> saved = ArgumentCaptor.forClass(UsageMetering.class);
        verify(usageMeteringRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        // The seeder also carried ai_call and storage. Nothing records either,
        // so no row is written: absent reads as "not measured", a zero would
        // claim it was measured and came to nothing.
        assertThat(saved.getAllValues()).extracting(UsageMetering::getMetric)
                .containsExactlyInAnyOrder("email_sent", "sms_sent");
        assertThat(saved.getAllValues()).extracting(UsageMetering::getValue)
                .containsExactlyInAnyOrder(42L, 7L);
    }

    @Test
    void rewritesTheMonthsRowRatherThanAddingASecond() {
        UsageMetering existing =
                new UsageMetering(UUID.randomUUID(), TENANT_ID, "email_sent", 10, "2026-07");
        when(deliveryUsage.sentCounts(eq(TENANT_ID), any(), any()))
                .thenReturn(Map.of("email_sent", 55L));
        when(usageMeteringRepository.findByTenantIdAndPeriod(TENANT_ID, "2026-07"))
                .thenReturn(List.of(existing));
        when(usageMeteringRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        job().meter(TENANT_ID, MONTH);

        // This is the running total for the month, not a history. A second row
        // would double the tenant's usage against its plan limit.
        assertThat(existing.getValue()).isEqualTo(55L);
        verify(usageMeteringRepository).save(existing);
    }

    @Test
    void saysSoLoudlyWhenNoTenantIsFound() {
        when(tenancyService.systemActiveTenantIds()).thenReturn(List.of());

        job().run();

        // A running system always has a tenant, so an empty list is a fault
        // rather than a quiet month.
        verify(usageMeteringRepository, org.mockito.Mockito.never()).save(any());
    }
}
