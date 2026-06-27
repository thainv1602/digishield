package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.CoachingPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CoachingPage}.
 */
public interface CoachingPageRepository extends JpaRepository<CoachingPage, UUID> {

    List<CoachingPage> findByTenantId(UUID tenantId);
}
