package com.digishield.reporting.infrastructure;

import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link BlacklistEntry}.
 */
public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, UUID> {

    List<BlacklistEntry> findByTenantId(UUID tenantId);

    /** Used to keep triage from adding the same indicator twice. */
    boolean existsByTenantIdAndTypeAndValue(UUID tenantId, BlacklistType type, String value);
}
