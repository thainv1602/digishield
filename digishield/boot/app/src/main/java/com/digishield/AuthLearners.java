package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.learning.api.LearnerDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the learning module's {@link LearnerDirectory} SPI to the auth module so
 * a leaderboard entry can show a name. Mirrors {@link AuthWorkforce} and
 * {@link AuthParticipants}.
 */
@Component
class AuthLearners implements LearnerDirectory {

    private final AuthService authService;

    AuthLearners(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Optional<Learner> find(UUID userId) {
        return authService.listUsers().stream()
                .filter(u -> userId.equals(u.id()))
                .findFirst()
                .map(u -> new Learner(u.name(), u.department()));
    }
}
