package com.digishield.interception.infrastructure;

import com.digishield.interception.domain.InterventionEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link InterventionEvent}.
 */
public interface InterventionEventRepository extends JpaRepository<InterventionEvent, UUID> {
}
