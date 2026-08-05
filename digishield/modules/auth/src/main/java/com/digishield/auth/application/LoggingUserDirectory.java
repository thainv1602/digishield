package com.digishield.auth.application;

import com.digishield.auth.api.UserDirectory;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link UserDirectory} used when no identity provider is wired: records
 * the intent and creates nothing. The boot app's {@code CognitoUserDirectory} is
 * {@code @Primary} and wins injection when active.
 *
 * <p>It logs rather than silently succeeding because the difference matters — a
 * user added here exists on the Users screen and has no way to sign in.
 */
@Component
public class LoggingUserDirectory implements UserDirectory {

    private static final Logger log = LoggerFactory.getLogger(LoggingUserDirectory.class);

    @Override
    public Optional<UUID> createUser(String email, String role) {
        log.info("[auth] No user directory configured — {} was added with role {} but has no "
                + "sign-in account and cannot log in", email, role);
        return Optional.empty();
    }

    @Override
    public void setRole(String email, String role, Set<String> otherRoles) {
        log.info("[auth] No user directory configured — {} is recorded as {} but the tokens "
                + "they sign in with are unchanged", email, role);
    }
}
