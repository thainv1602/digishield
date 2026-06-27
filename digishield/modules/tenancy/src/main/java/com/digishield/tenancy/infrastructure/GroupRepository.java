package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Group}.
 */
public interface GroupRepository extends JpaRepository<Group, UUID> {

    /**
     * Get all groups of a tenant, ordered by name.
     */
    List<Group> findByTenantIdOrderByName(UUID tenantId);
}
