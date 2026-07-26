package com.digishield;

import com.digishield.shared.tenantcontext.AuditRecorder;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.tenancy.api.TenancyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Wires the {@link AuditRecorder} SPI to the tenancy module, which owns the audit
 * log. Lives in the boot app so no business module has to depend on tenancy —
 * mirrors {@link ReportingRecentReports} and {@link AiCampaignTemplates}.
 *
 * <p>Attribution is resolved here rather than passed in: the authenticated
 * principal, the request's source address and the tenant on the current thread.
 * A call site that had to supply those could get them wrong, or quietly omit them.
 */
@Component
class TenancyAuditRecorder implements AuditRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(TenancyAuditRecorder.class);

    private final TenancyService tenancyService;

    TenancyAuditRecorder(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    @Override
    public void record(String action, String target, Severity severity) {
        record(currentTenant(), currentActor(), action, target, severity);
    }

    @Override
    public void record(UUID tenantId, String actor, String action, String target, Severity severity) {
        if (tenantId == null) {
            // Nothing to file it under — an attempt against an address that belongs
            // to no tenant. Still visible in the application log, which now carries
            // a requestId, rather than disappearing entirely.
            LOG.warn("Unattributable audit event: action={} actor={} target={}", action, actor, target);
            return;
        }
        try {
            tenancyService.recordAudit(tenantId, actor, action, target, clientIp(),
                    severity == null ? Severity.STANDARD.name().toLowerCase()
                            : severity.name().toLowerCase());
        } catch (RuntimeException e) {
            // Auditing must never break the action it is auditing.
            LOG.warn("Could not write audit entry action={} target={}: {}", action, target, e.toString());
        }
    }

    private static UUID currentTenant() {
        try {
            return TenantContext.requireUuid();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    /** Source address of the request being audited, when there is one. */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
