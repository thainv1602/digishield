package com.digishield.reporting.infrastructure;

import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link PhishingReport}.
 */
public interface PhishingReportRepository extends JpaRepository<PhishingReport, UUID> {

    List<PhishingReport> findByTenantId(UUID tenantId);

    List<PhishingReport> findByTenantIdOrderByReportedAtDesc(UUID tenantId);

    List<PhishingReport> findByTenantIdAndStatusOrderByReportedAtDesc(UUID tenantId, ReportStatus status);
}
