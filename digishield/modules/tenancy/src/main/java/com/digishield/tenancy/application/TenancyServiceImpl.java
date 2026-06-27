package com.digishield.tenancy.application;

import com.digishield.tenancy.api.AuditLogView;
import com.digishield.tenancy.api.CreateTenantCommand;
import com.digishield.tenancy.api.FeatureFlagView;
import com.digishield.tenancy.api.ScimConfigView;
import com.digishield.tenancy.api.TenancyService;
import com.digishield.tenancy.api.TenantView;
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.ScimConfig;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.infrastructure.AuditLogRepository;
import com.digishield.tenancy.infrastructure.FeatureFlagRepository;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link TenancyService}.
 */
@Service
@Transactional
public class TenancyServiceImpl implements TenancyService {

    private final TenantRepository tenantRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimConfigRepository scimConfigRepository;

    public TenancyServiceImpl(TenantRepository tenantRepository,
                              FeatureFlagRepository featureFlagRepository,
                              AuditLogRepository auditLogRepository,
                              ScimConfigRepository scimConfigRepository) {
        this.tenantRepository = tenantRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.auditLogRepository = auditLogRepository;
        this.scimConfigRepository = scimConfigRepository;
    }

    @Override
    public TenantView createTenant(CreateTenantCommand command) {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(
                id,
                id,
                command.name(),
                TenantTier.valueOf(command.tier()),
                command.dataRegion(),
                TenantStatus.PROVISIONING
        );
        Tenant saved = tenantRepository.save(tenant);
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantView> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogView> listAuditLogs(UUID tenantId) {
        return auditLogRepository.findByTenantIdOrderByTsDesc(tenantId).stream()
                .map(this::toAuditView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ScimConfigView getScimConfig(UUID tenantId) {
        return scimConfigRepository.findByTenantId(tenantId)
                .map(this::toScimView)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureFlagView> getFeatureFlags(UUID tenantId) {
        return featureFlagRepository.findByTenantId(tenantId).stream()
                .map(f -> new FeatureFlagView(f.getKey(), f.isEnabled()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String key) {
        return featureFlagRepository.findByTenantIdAndKey(tenantId, key)
                .map(f -> f.isEnabled())
                .orElse(false);
    }

    private TenantView toView(Tenant tenant) {
        return new TenantView(
                tenant.getId(),
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getTier() != null ? tenant.getTier().name() : null,
                tenant.getDataRegion(),
                tenant.getStatus() != null ? tenant.getStatus().name() : null,
                tenant.getUserCount(),
                tenant.getDomain()
        );
    }

    private AuditLogView toAuditView(AuditLog a) {
        return new AuditLogView(a.getId(), a.getTs(), a.getActor(), a.getAction(),
                a.getTarget(), a.getIp(), a.getSeverity());
    }

    private ScimConfigView toScimView(ScimConfig s) {
        String syncStatus;
        if (s.getLastSyncAt() == null) {
            syncStatus = "never";
        } else if (s.getSyncErrorCount() != null && s.getSyncErrorCount() > 0) {
            syncStatus = "error";
        } else {
            syncStatus = "ok";
        }
        return new ScimConfigView(s.getTenantId(), s.getIdpName(), s.isConnected(),
                s.getIdpTenantId(), s.getClientId(), s.getScimEndpoint(), s.getLastSyncAt(),
                s.getSyncedUserCount(), s.getSyncErrorCount(), syncStatus);
    }
}
