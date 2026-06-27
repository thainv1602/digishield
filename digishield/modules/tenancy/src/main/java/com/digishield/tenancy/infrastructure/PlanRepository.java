package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Plan} (global billing catalogue).
 */
public interface PlanRepository extends JpaRepository<Plan, UUID> {
}
