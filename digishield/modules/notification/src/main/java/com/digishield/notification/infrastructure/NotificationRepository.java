package com.digishield.notification.infrastructure;

import com.digishield.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link Notification}. Every query should be scoped by tenant.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Notification> findByTenantId(UUID tenantId);
}
