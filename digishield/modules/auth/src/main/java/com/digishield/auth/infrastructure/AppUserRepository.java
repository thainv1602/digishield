package com.digishield.auth.infrastructure;

import com.digishield.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AppUser}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Finds a user by email within the scope of a tenant.
     */
    Optional<AppUser> findByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Finds a user by id, constrained to the scope of a tenant.
     */
    Optional<AppUser> findByTenantIdAndId(UUID tenantId, UUID id);
}
