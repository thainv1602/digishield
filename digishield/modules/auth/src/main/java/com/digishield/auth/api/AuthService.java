package com.digishield.auth.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the Auth module, used by other modules and the web layer.
 */
public interface AuthService {

    /**
     * Gets the current user (inferred from the tenant context and login session).
     *
     * @return the current user if it can be determined, otherwise {@link Optional#empty()}
     */
    Optional<CurrentUser> currentUser();

    /**
     * Gets the current user, optionally selecting a demo persona by role
     * (used in the dev profile via the {@code X-Demo-Role} header). In prod the
     * {@code roleHint} is ignored.
     *
     * @param roleHint snake_case role to pick a demo persona, may be {@code null}
     */
    Optional<CurrentUser> currentUser(String roleHint);

    /**
     * Finds a user by identifier within the scope of the current tenant.
     */
    Optional<CurrentUser> findById(UUID userId);

    /**
     * Lists users for the current tenant (Users screen).
     */
    List<UserView> listUsers();

    /**
     * Authenticates and returns a token pair. In the dev profile this does not
     * validate credentials and returns static demo tokens; {@code roleHint}
     * selects the demo persona.
     *
     * @param email    the login email
     * @param password the password (ignored in dev)
     * @param roleHint optional snake_case role to pick a demo persona
     */
    TokenPair login(String email, String password, String roleHint);

    /**
     * Issues a fresh token pair from a refresh token.
     */
    TokenPair refresh(String refreshToken);

    /**
     * Checks whether the current user has the given role.
     *
     * @param role the name of the role to check (e.g. "TENANT_ADMIN")
     */
    boolean hasRole(String role);
}
