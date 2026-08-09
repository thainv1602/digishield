package com.digishield.learning.application;

import com.digishield.learning.api.BehaviourHistory;
import com.digishield.learning.domain.Badge;
import com.digishield.learning.domain.BadgeCatalog;
import com.digishield.learning.domain.BadgeCriteriaType;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.infrastructure.BadgeCatalogRepository;
import com.digishield.learning.infrastructure.BadgeRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Awards the badges a person has actually earned.
 * <p>
 * The catalogue used to describe its conditions only in prose — "Hoàn thành 3
 * khoá học đầu tiên" — which nothing could evaluate, so {@code badge} had one
 * writer: a dev seeder. Now each catalogue entry carries a measure and a
 * threshold, and this checks them against what the platform recorded.
 * <p>
 * Entries with no criteria are skipped rather than guessed at. A badge nobody
 * defined a rule for is one nothing awards automatically, which is the honest
 * reading of every row that existed before this.
 */
@Component
public class BadgeAwarder {

    private static final Logger LOG = LoggerFactory.getLogger(BadgeAwarder.class);

    private final BadgeCatalogRepository catalogRepository;
    private final BadgeRepository badgeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GamificationProfileRepository profileRepository;
    private final BehaviourHistory behaviourHistory;

    BadgeAwarder(BadgeCatalogRepository catalogRepository,
                 BadgeRepository badgeRepository,
                 EnrollmentRepository enrollmentRepository,
                 GamificationProfileRepository profileRepository,
                 // Lazy for the same reason as elsewhere in this module: this
                 // reaches analytics, whose dashboard metrics reach back here.
                 @Lazy BehaviourHistory behaviourHistory) {
        this.catalogRepository = catalogRepository;
        this.badgeRepository = badgeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.profileRepository = profileRepository;
        this.behaviourHistory = behaviourHistory;
    }

    /**
     * Awards any catalogue badge whose threshold this user has now reached and
     * which they do not already hold.
     */
    public void evaluate(UUID tenantId, UUID userId) {
        var catalogue = catalogRepository.findByTenantIdOrderByName(tenantId).stream()
                .filter(b -> b.getCriteriaType() != null && b.getCriteriaThreshold() != null)
                .toList();
        if (catalogue.isEmpty()) {
            return;
        }
        Set<String> held = badgeRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(Badge::getName)
                .collect(Collectors.toSet());

        for (BadgeCatalog entry : catalogue) {
            if (held.contains(entry.getName())) {
                continue;
            }
            int measured = measure(tenantId, userId, entry.getCriteriaType());
            if (measured < entry.getCriteriaThreshold()) {
                continue;
            }
            badgeRepository.save(new Badge(UUID.randomUUID(), tenantId, userId,
                    entry.getName(), entry.getDescription(), entry.getIconRef(),
                    true, Instant.now()));
            LOG.info("Badge '{}' awarded to user {} (tenant {}): {} reached {}",
                    entry.getName(), userId, tenantId, entry.getCriteriaType(), measured);
        }
    }

    /** Reads the measure the criteria names. Every one of these is recorded. */
    private int measure(UUID tenantId, UUID userId, BadgeCriteriaType type) {
        return switch (type) {
            case COURSES_COMPLETED -> (int) enrollmentRepository
                    .findByTenantIdAndUserId(tenantId, userId).stream()
                    .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED)
                    .count();
            case REPORTS_CONFIRMED -> behaviourHistory.confirmedReports(tenantId, userId);
            case POINTS -> profileRepository.findByTenantIdAndUserId(tenantId, userId)
                    .map(p -> p.getPoints())
                    .orElse(0);
        };
    }
}
