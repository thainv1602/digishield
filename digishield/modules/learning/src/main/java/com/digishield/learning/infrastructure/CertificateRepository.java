package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Certificate}.
 */
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Certificate> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
