package com.digishield.interception.infrastructure;

import com.digishield.interception.domain.AccountWatchEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link AccountWatchEntry}.
 */
public interface AccountWatchEntryRepository extends JpaRepository<AccountWatchEntry, UUID> {

    Optional<AccountWatchEntry> findByTenantIdAndValue(UUID tenantId, String value);

    List<AccountWatchEntry> findByTenantIdOrderByAddedAtDesc(UUID tenantId);

    List<AccountWatchEntry> findByTenantId(UUID tenantId);
}
