package com.digishield;

import com.digishield.auth.api.UserDirectory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Real {@link UserDirectory} backed by AWS Cognito (SDK v2): the Users screen
 * creates the pool account, not just the application row. Active only when
 * {@code digishield.auth.cognito.directory.enabled=true}; {@code @Primary} so it
 * wins over the no-op {@code LoggingUserDirectory}. Lives in the boot app so the
 * AWS SDK stays out of the auth module (same pattern as the SES/SNS gateways).
 *
 * <p>Cognito mails the invitation itself — {@code AdminCreateUser} without
 * {@code messageAction=SUPPRESS} sends the temporary password to the address
 * being created. That is deliberately the whole delivery mechanism: an invite
 * the application composed would need its own SES identity and its own way of
 * handing over a credential, and Cognito already does both.
 *
 * <p>Needs {@code cognito-idp:AdminCreateUser}, {@code AdminGetUser},
 * {@code AdminAddUserToGroup}, {@code AdminListGroupsForUser} and
 * {@code AdminRemoveUserFromGroup} on the pool.
 */
@Component
@Primary
@ConditionalOnProperty(name = "digishield.auth.cognito.directory.enabled", havingValue = "true")
class CognitoUserDirectory implements UserDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(CognitoUserDirectory.class);

    private final CognitoIdentityProviderClient cognito;
    private final String userPoolId;

    @org.springframework.beans.factory.annotation.Autowired
    CognitoUserDirectory(@Value("${digishield.auth.cognito.user-pool-id:}") String userPoolId,
                         @Value("${digishield.auth.cognito.region:}") String region) {
        this(buildClient(region), userPoolId);
        LOG.info("CognitoUserDirectory active (userPoolId={})", userPoolId);
    }

    /** Test seam: inject a (mock) Cognito client directly. */
    CognitoUserDirectory(CognitoIdentityProviderClient cognito, String userPoolId) {
        if (!StringUtils.hasText(userPoolId)) {
            // Refusing to start beats starting and failing on the first invite: the
            // person who turned the directory on is here now, the admin adding a
            // user later is not.
            throw new IllegalStateException(
                    "digishield.auth.cognito.user-pool-id is required when the Cognito "
                            + "user directory is enabled");
        }
        this.cognito = cognito;
        this.userPoolId = userPoolId;
    }

    private static CognitoIdentityProviderClient buildClient(String region) {
        var builder = CognitoIdentityProviderClient.builder();
        if (StringUtils.hasText(region)) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    @Override
    public Optional<UUID> createUser(String email, String role) {
        UUID subject = createAccount(email);
        addToGroup(email, role);
        return Optional.ofNullable(subject);
    }

    @Override
    public void setRole(String email, String role, Set<String> otherRoles) {
        // Revoke before granting: see the SPI's note on which way this fails safe.
        for (String held : groupsHeldAmong(email, otherRoles)) {
            removeFromGroup(email, held);
        }
        addToGroup(email, role);
    }

    /**
     * Which of {@code candidates} the account is actually in.
     *
     * <p>Asking is one call and usually saves several: a demotion revokes one
     * group, not the five the account never held. It also catches memberships
     * nobody recorded — an account added to a group by hand does not stop
     * carrying it because the database says otherwise.
     */
    private List<String> groupsHeldAmong(String email, Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            AdminListGroupsForUserResponse held = cognito.adminListGroupsForUser(
                    AdminListGroupsForUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(email)
                            .build());
            return held.groups().stream()
                    .map(g -> g.groupName())
                    .filter(candidates::contains)
                    .toList();
        } catch (UserNotFoundException e) {
            throw noSuchAccount(email);
        } catch (CognitoIdentityProviderException e) {
            LOG.error("Cognito rejected AdminListGroupsForUser for {}: {}", email, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not read the current roles at the identity provider");
        }
    }

    private void removeFromGroup(String email, String group) {
        try {
            cognito.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .groupName(group)
                    .build());
            LOG.info("Revoked group {} from {}", group, email);
        } catch (CognitoIdentityProviderException e) {
            LOG.error("Cognito rejected AdminRemoveUserFromGroup for {} ({}): {}",
                    email, group, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not revoke the previous role at the identity provider");
        }
    }

    /**
     * Users who predate the directory, or whose email was edited after the account
     * was created — the row's address no longer names any account.
     */
    private ResponseStatusException noSuchAccount(String email) {
        LOG.error("No Cognito account for {} — its role cannot be changed", email);
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "This user has no sign-in account at the identity provider, so their role "
                        + "cannot be changed; create the account first");
    }

    /** Creates the pool account (Cognito emails the temporary password), or adopts an existing one. */
    private UUID createAccount(String email) {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        // The address is the one an admin typed, and the invitation
                        // proves they can read it; leaving it unverified would only
                        // block the password reset that recovers the account.
                        AttributeType.builder().name("email_verified").value("true").build())
                .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                .build();
        try {
            AdminCreateUserResponse response = cognito.adminCreateUser(request);
            LOG.info("Created Cognito account for {} (invitation emailed)", email);
            return subjectOf(response.user() != null ? response.user().attributes() : List.of());
        } catch (UsernameExistsException e) {
            // Accounts made by hand before this existed, and repeat POSTs of the same
            // address. Adopt the account rather than refusing the whole request.
            LOG.info("Cognito account for {} already exists — reusing it", email);
            return subjectOfExisting(email);
        } catch (InvalidParameterException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The identity provider rejected that email address");
        } catch (TooManyRequestsException | LimitExceededException e) {
            // Includes the daily cap on Cognito's built-in mailer (50/day unless the
            // pool sends through SES), which is a wait, not a broken request.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The identity provider is rate limiting account creation — try again later");
        } catch (CognitoIdentityProviderException e) {
            LOG.error("Cognito rejected AdminCreateUser for {}: {}", email, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create the sign-in account");
        }
    }

    private UUID subjectOfExisting(String email) {
        try {
            AdminGetUserResponse existing = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
            return subjectOf(existing.userAttributes());
        } catch (CognitoIdentityProviderException e) {
            // Not fatal: without the subject the application row keeps a generated id
            // and currentUser() still resolves the caller by email claim.
            LOG.warn("Could not read the existing Cognito account for {}: {}", email, e.toString());
            return null;
        }
    }

    private void addToGroup(String email, String role) {
        if (!StringUtils.hasText(role)) {
            return;
        }
        try {
            cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .groupName(role)
                    .build());
        } catch (UserNotFoundException e) {
            throw noSuchAccount(email);
        } catch (ResourceNotFoundException e) {
            // The pool has no such group. The account exists but its token would carry
            // no authority at all, so this fails loudly rather than leaving an admin to
            // discover it at the new user's first login.
            LOG.error("Cognito pool {} has no group '{}'", userPoolId, role);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The identity provider has no group for role '" + role + "'");
        } catch (CognitoIdentityProviderException e) {
            LOG.error("Cognito rejected AdminAddUserToGroup for {} ({}): {}", email, role, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not grant the role at the identity provider");
        }
    }

    /** Cognito's {@code sub} attribute — the {@code sub} claim its tokens will carry. */
    private static UUID subjectOf(List<AttributeType> attributes) {
        if (attributes == null) {
            return null;
        }
        for (AttributeType attribute : attributes) {
            if ("sub".equals(attribute.name())) {
                try {
                    return UUID.fromString(attribute.value());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
