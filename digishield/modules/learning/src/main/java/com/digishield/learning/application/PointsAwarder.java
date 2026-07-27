package com.digishield.learning.application;

import com.digishield.learning.api.LearnerDirectory;
import com.digishield.learning.domain.GamificationProfile;
import com.digishield.learning.domain.PointAction;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import com.digishield.learning.infrastructure.PointRuleRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Moves a person's score when they do something that counts.
 * <p>
 * Nothing did this before. {@code point_rule} carried a designed set of values,
 * {@code gamification_profile} had a points column and a setter, and the
 * leaderboard read both — but no code ever called the setter, so on a real
 * tenant every score stayed at zero and the leaderboard was permanently empty.
 */
@Component
public class PointsAwarder {

    private static final Logger log = LoggerFactory.getLogger(PointsAwarder.class);

    private final GamificationProfileRepository profileRepository;
    private final PointRuleRepository pointRuleRepository;
    private final ObjectProvider<LearnerDirectory> directory;

    PointsAwarder(GamificationProfileRepository profileRepository,
                  PointRuleRepository pointRuleRepository,
                  ObjectProvider<LearnerDirectory> directory) {
        this.profileRepository = profileRepository;
        this.pointRuleRepository = pointRuleRepository;
        this.directory = directory;
    }

    /**
     * Applies {@code action} to the user's score and returns the new total.
     * <p>
     * Callers are responsible for only calling this when the thing actually
     * happened — completing an already-completed course must not pay twice.
     */
    public int award(UUID tenantId, UUID userId, PointAction action) {
        int points = pointsFor(tenantId, action);
        GamificationProfile profile = profileRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> newProfile(tenantId, userId));

        profile.setPoints(profile.getPoints() + points);
        GamificationProfile saved = profileRepository.save(profile);

        log.info("Points {}{} to user {} for {} (tenant {}), now {}",
                points >= 0 ? "+" : "", points, userId, action.wireName(), tenantId, saved.getPoints());
        return saved.getPoints();
    }

    /** The tenant's own rule when it has one, otherwise the built-in default. */
    private int pointsFor(UUID tenantId, PointAction action) {
        return pointRuleRepository.findByTenantIdAndAction(tenantId, action.wireName())
                .map(rule -> rule.getPoints())
                .orElseGet(action::defaultPoints);
    }

    private GamificationProfile newProfile(UUID tenantId, UUID userId) {
        LearnerDirectory lookup = directory.getIfAvailable();
        var learner = lookup == null ? java.util.Optional.<LearnerDirectory.Learner>empty()
                : lookup.find(userId);
        return new GamificationProfile(
                UUID.randomUUID(),
                tenantId,
                userId,
                learner.map(LearnerDirectory.Learner::displayName).orElse(null),
                learner.map(LearnerDirectory.Learner::department).orElse(null),
                0);
    }
}
