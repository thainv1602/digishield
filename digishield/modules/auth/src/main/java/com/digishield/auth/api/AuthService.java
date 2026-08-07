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
     * Finds a user by identifier within the scope of the current tenant.
     */
    Optional<CurrentUser> findById(UUID userId);

    /**
     * Returns the signed-in user's own profile, keyed by the JWT subject. When no
     * {@code app_user} row exists yet the identity is derived from the JWT
     * (name/locale null until first save).
     */
    ProfileView getMyProfile();

    /**
     * Updates the signed-in user's own name and/or UI locale, provisioning an
     * {@code app_user} row (keyed by the JWT subject) on first save.
     */
    ProfileView updateMyProfile(String name, String locale, String email);

    /**
     * Lists users for the current tenant (Users screen).
     */
    List<UserView> listUsers();

    /**
     * Returns a single user of the current tenant.
     *
     * @throws java.util.NoSuchElementException if no such user exists in the tenant
     */
    UserView getUser(UUID userId);

    /**
     * Creates a new user in the current tenant.
     *
     * @param input the user attributes ({@code email} and {@code role} expected)
     * @return the created user
     */
    UserView createUser(UserUpsert input);

    /**
     * Applies a partial update (role / department / locale / email) to a user of
     * the current tenant. {@code null} fields are left unchanged.
     *
     * @return the updated user
     * @throws java.util.NoSuchElementException if no such user exists in the tenant
     */
    UserView updateUser(UUID userId, UserUpsert changes);

    /**
     * Removes a user from the current tenant, and their sign-in account with it.
     *
     * <p>The identity provider comes first: a row deleted while the account lives
     * on is a user who no longer appears anywhere in the product and can still
     * log into it, since authorisation reads the token rather than the database.
     *
     * @param userId the user to remove
     * @throws java.util.NoSuchElementException if no such user exists in the tenant
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         caller does not outrank the target, or is the target
     */
    void deleteUser(UUID userId);

    /**
     * Locks a user out, or lets them back in.
     *
     * <p>Suspension disables the sign-in account and ends its sessions, so it
     * bites immediately rather than at the next token. It also cuts the person
     * off from the training they may have been suspended for skipping: there is
     * no self-service way back, only this method with {@code suspended} false.
     *
     * @param userId    the user to lock or restore
     * @param suspended true to lock the account, false to restore it
     * @param reason    short note recorded on the audit entry
     * @return the updated user
     * @throws java.util.NoSuchElementException if no such user exists in the tenant
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         caller does not outrank the target, or is the target
     */
    UserView setSuspended(UUID userId, boolean suspended, String reason);

    /**
     * Bulk-imports users into the current tenant. In dev this is synchronous; the
     * returned {@link ImportResult#accepted()} reflects the number created.
     */
    ImportResult importUsers(List<UserUpsert> users);

    /**
     * Authenticates and returns a token pair. In the dev profile this does not
     * validate credentials and returns static demo tokens; {@code roleHint}
     * selects the demo persona.
     *
     * @param email    the login email
     * @param password the password (ignored in dev)
     * @param roleHint optional snake_case role to pick a demo persona
     */
    TokenPair login(String email, String password);

    /**
     * Issues a fresh token pair from a refresh token.
     */
    TokenPair refresh(String refreshToken);

    /**
     * Completes an SSO (SAML/OAuth) sign-in. In dev the {@code org} and
     * {@code assertion} are accepted without verification and demo tokens are returned.
     */
    TokenPair ssoCallback(String org, String assertion);

    /**
     * Records a password-reset request for the given email. In dev this is a no-op
     * (no email is sent) and never reveals whether the account exists.
     */
    void forgotPassword(String email);

    /**
     * Resets a password using a reset token. In dev the token and new password are
     * accepted without verification.
     */
    void resetPassword(String token, String newPassword);

    /**
     * Initializes MFA enrollment, returning a fresh TOTP secret, an
     * {@code otpauth://} URL and an inline QR SVG.
     */
    MfaSetupView mfaSetup();

    /**
     * Confirms MFA activation with the first code and returns one-time recovery
     * codes. In dev any code is accepted.
     */
    List<String> mfaVerify(String code);

    /**
     * Verifies an MFA challenge during login and returns a token pair. In dev any
     * code is accepted for the given temporary {@code mfaToken}.
     */
    TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice);

    /**
     * Checks whether the current user has the given role.
     *
     * @param role the name of the role to check (e.g. "TENANT_ADMIN")
     */
    boolean hasRole(String role);
}
