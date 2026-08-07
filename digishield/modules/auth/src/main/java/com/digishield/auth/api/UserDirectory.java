package com.digishield.auth.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SPI for the identity provider's user directory — the accounts people actually
 * sign in with. Distinct from {@link AuthProvider}, which covers only the
 * credential/token flows of an account that already exists.
 *
 * <p>The auth module ships a no-op {@code LoggingUserDirectory}; the boot
 * application supplies a real {@code CognitoUserDirectory} (AWS Cognito) when
 * {@code digishield.auth.cognito.directory.enabled=true}. Keeps the AWS SDK out
 * of the business module, mirroring the notification email/SMS gateways.
 */
public interface UserDirectory {

    /**
     * Provisions the sign-in account for a user the Users screen just added and
     * puts it in the group named after {@code role}.
     *
     * <p>The group matters as much as the account: authorisation reads the
     * token's groups claim, not {@code app_user.role}, so an account outside its
     * group can sign in and reach nothing.
     *
     * <p>Idempotent — an account that already exists (created by hand before this
     * existed, say) is adopted rather than rejected, and its group membership is
     * asserted again.
     *
     * @param email    login email; also where the invitation is delivered
     * @param role     snake_case role name, matching the provider's group name
     * @param tenantId the organisation the account belongs to. Recorded on the
     *                 account itself, because the token's tenant claim is built
     *                 from it and everything the person can read is filtered by
     *                 that claim -- an account without one cannot sign in at all
     * @return the provider's subject id when it is known, so the application row
     *         can carry the same id the token will present; empty when the
     *         directory cannot say (the dev no-op, for one)
     */
    Optional<UUID> createUser(String email, String role, UUID tenantId);

    /**
     * Moves an existing account to {@code role}: takes away every group in
     * {@code otherRoles} it currently holds, then grants {@code role}.
     *
     * <p>Revoking first is deliberate. The two calls cannot be made atomic, and
     * of the two ways to fail — a moment holding both the old role and the new,
     * or a moment holding neither — only the second one fails closed. A demotion
     * that leaves the old group behind is a demotion that did not happen: the
     * token carries every group, and the app reads the highest one.
     *
     * <p>The caller owns the role vocabulary; this only moves group membership
     * around. Roles the account does not hold are left alone, not "removed".
     *
     * <p>Implementations should also end the account's existing sessions, since a
     * token already issued carries the old groups until it expires.
     *
     * @param email      login email, which is also the account's username
     * @param role       snake_case role name to grant
     * @param otherRoles every other role's group name, to revoke if held
     */
    void setRole(String email, String role, Set<String> otherRoles);

    /**
     * Removes the account entirely, so the address can be added again later.
     *
     * <p>Deleting rather than disabling is deliberate: {@link #createUser} adopts
     * an account that already exists, so a merely disabled one would be adopted
     * by the next person re-adding that address — reported as created, unable to
     * sign in, and nobody the wiser.
     *
     * <p>An account that is already gone is not an error. The caller is removing
     * a user either way, and a directory that has nothing to remove has already
     * reached the state being asked for.
     *
     * @param email login email, which is also the account's username
     */
    void deleteUser(String email);

    /**
     * Turns sign-in on or off for an account that stays in place.
     *
     * <p>Disabling ends the account's sessions as well, for the reason
     * {@link #setRole} does: a token already issued keeps working against an API
     * that validates offline, so without a sign-out the lock does not take hold
     * until the token expires.
     *
     * <p>Worth being blunt about what this costs. A disabled account cannot log
     * in <em>at all</em>, including into the training it was disabled for not
     * completing. There is no path where the person clears the block themselves;
     * someone has to enable them again first.
     *
     * @param email   login email, which is also the account's username
     * @param enabled false to lock the account out, true to let it back in
     */
    void setEnabled(String email, boolean enabled);
}
