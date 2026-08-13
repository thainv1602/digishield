package com.digishield.reporting.infrastructure;

import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link PhishingReport}.
 */
public interface PhishingReportRepository extends JpaRepository<PhishingReport, UUID> {

    List<PhishingReport> findByTenantId(UUID tenantId);

    List<PhishingReport> findByTenantIdOrderByReportedAtDesc(UUID tenantId);

    /**
     * The newest reports only. The dashboard shows a handful, so let the
     * database apply the limit instead of loading the tenant's whole history
     * and discarding all but the first few.
     */
    List<PhishingReport> findByTenantIdOrderByReportedAtDesc(UUID tenantId, Pageable pageable);

    List<PhishingReport> findByTenantIdAndStatusOrderByReportedAtDesc(UUID tenantId, ReportStatus status);

    List<PhishingReport> findByTenantIdAndUserIdOrderByReportedAtDesc(UUID tenantId, UUID userId);

    /**
     * Counts still-open reports per AI label in one aggregate.
     *
     * <p>The dashboard's open-alert tile used to be computed by fetching every
     * report the tenant had ever filed, mapping each to a DTO and counting in
     * Java. This returns at most one row per label instead, and is covered by
     * {@code idx_phishing_report_tenant_status_label}.
     *
     * @param tenantId the tenant to count within
     * @param statuses the statuses that count as open
     *                 ({@link ReportStatus#openStatuses()})
     * @return one row per label present, labels with no open reports absent
     */
    @Query("""
            select new com.digishield.reporting.infrastructure.AiLabelCount(r.aiLabel, count(r))
            from PhishingReport r
            where r.tenantId = :tenantId and r.status in :statuses
            group by r.aiLabel
            """)
    List<AiLabelCount> countByAiLabel(@Param("tenantId") UUID tenantId,
                                      @Param("statuses") Collection<ReportStatus> statuses);
}
