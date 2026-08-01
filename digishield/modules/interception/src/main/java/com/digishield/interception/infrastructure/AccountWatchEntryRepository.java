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

    /**
     * The most recent entry for a value. Duplicates are legal — there is no
     * unique constraint on (tenant_id, value) and POST /account-watchlist may
     * insert the same value twice — so lookups must not assume uniqueness
     * (a plain findBy throws NonUniqueResultException and turns into a 500).
     */
    Optional<AccountWatchEntry> findFirstByTenantIdAndValueOrderByAddedAtDesc(UUID tenantId, String value);

    List<AccountWatchEntry> findByTenantIdOrderByAddedAtDesc(UUID tenantId);

    List<AccountWatchEntry> findByTenantId(UUID tenantId);
}
