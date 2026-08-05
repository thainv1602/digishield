package com.digishield.auth.api;

import java.util.Optional;
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
     * @param email login email; also where the invitation is delivered
     * @param role  snake_case role name, matching the provider's group name
     * @return the provider's subject id when it is known, so the application row
     *         can carry the same id the token will present; empty when the
     *         directory cannot say (the dev no-op, for one)
     */
    Optional<UUID> createUser(String email, String role);
}
