package com.digishield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Unit tests for {@link CognitoUserDirectory} against a mocked Cognito client.
 */
class CognitoUserDirectoryTest {

    private static final String POOL = "us-east-1_test";
    private static final UUID TENANT = UUID.randomUUID();

    private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
    private final CognitoUserDirectory directory = new CognitoUserDirectory(cognito, POOL);

    private void stubCreate(UUID subject) {
        when(cognito.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(
                AdminCreateUserResponse.builder()
                        .user(UserType.builder()
                                .attributes(AttributeType.builder()
                                        .name("sub").value(subject.toString()).build())
                                .build())
                        .build());
    }

    @Test
    void createUser_asksCognitoToMailTheInvitation() {
        stubCreate(UUID.randomUUID());

        directory.createUser("new@x.vn", "analyst", TENANT);

        ArgumentCaptor<AdminCreateUserRequest> request =
                ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        verify(cognito).adminCreateUser(request.capture());
        assertThat(request.getValue().userPoolId()).isEqualTo(POOL);
        assertThat(request.getValue().username()).isEqualTo("new@x.vn");
        assertThat(request.getValue().desiredDeliveryMediums())
                .containsExactly(DeliveryMediumType.EMAIL);
        // SUPPRESS is what the by-hand runbook uses, and it is exactly what makes
        // adding a user silent — the invitation is the delivery mechanism here.
        assertThat(request.getValue().messageAction()).isNotEqualTo(MessageActionType.SUPPRESS);
    }

    @Test
    void createUser_stampsTheTenantOnTheAccount() {
        stubCreate(UUID.randomUUID());

        directory.createUser("new@x.vn", "learner", TENANT);

        ArgumentCaptor<AdminCreateUserRequest> request =
                ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        verify(cognito).adminCreateUser(request.capture());
        // The pre-token Lambda builds the token's tid claim from this and refuses
        // to issue a token without it, so an account created without the
        // attribute is an account nobody can sign in to.
        assertThat(request.getValue().userAttributes())
                .anySatisfy(a -> {
                    assertThat(a.name()).isEqualTo("custom:tenant_id");
                    assertThat(a.value()).isEqualTo(TENANT.toString());
                });
    }

    @Test
    void createUser_putsTheAccountInTheRolesGroup() {
        stubCreate(UUID.randomUUID());

        directory.createUser("new@x.vn", "analyst", TENANT);

        ArgumentCaptor<AdminAddUserToGroupRequest> request =
                ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        verify(cognito).adminAddUserToGroup(request.capture());
        assertThat(request.getValue().groupName()).isEqualTo("analyst");
        assertThat(request.getValue().username()).isEqualTo("new@x.vn");
    }

    @Test
    void createUser_returnsTheSubjectCognitoAssigned() {
        UUID subject = UUID.randomUUID();
        stubCreate(subject);

        assertThat(directory.createUser("new@x.vn", "learner", TENANT)).contains(subject);
    }

    @Test
    void createUser_whenTheAccountAlreadyExists_adoptsItAndStillAssertsTheGroup() {
        when(cognito.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("exists").build());
        UUID subject = UUID.randomUUID();
        when(cognito.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(
                AdminGetUserResponse.builder()
                        .userAttributes(AttributeType.builder()
                                .name("sub").value(subject.toString()).build())
                        .build());

        Optional<UUID> result = directory.createUser("old@x.vn", "manager", TENANT);

        // Accounts created by hand before this existed still have to be usable.
        assertThat(result).contains(subject);
        verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    @Test
    void createUser_whenTheGroupIsMissing_fails() {
        stubCreate(UUID.randomUUID());
        when(cognito.adminAddUserToGroup(any(AdminAddUserToGroupRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("no group").build());

        // The account would exist carrying no authority; a silent skip looks like a
        // working account right up to the new user's first login.
        assertThatThrownBy(() -> directory.createUser("new@x.vn", "nope", TENANT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void createUser_whenCognitoRejectsTheAddress_isABadRequest() {
        when(cognito.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(InvalidParameterException.builder().message("bad email").build());

        assertThatThrownBy(() -> directory.createUser("not-an-email", "learner", TENANT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    private void stubGroupsHeld(String... groups) {
        when(cognito.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class))).thenReturn(
                AdminListGroupsForUserResponse.builder()
                        .groups(java.util.Arrays.stream(groups)
                                .map(g -> GroupType.builder().groupName(g).build())
                                .toList())
                        .build());
    }

    @Test
    void setRole_revokesTheOldGroupBeforeGrantingTheNewOne() {
        stubGroupsHeld("org_admin");

        directory.setRole("boss@x.vn", "learner", Set.of("org_admin", "manager", "analyst"));

        // Of the two ways this can be interrupted, only ending with no group at all
        // fails closed — a demotion that leaves org_admin behind did not happen.
        InOrder order = inOrder(cognito);
        order.verify(cognito).adminRemoveUserFromGroup(any(AdminRemoveUserFromGroupRequest.class));
        order.verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    @Test
    void setRole_revokesOnlyTheGroupsTheAccountActuallyHolds() {
        stubGroupsHeld("analyst");

        directory.setRole("a@x.vn", "manager", Set.of("org_admin", "analyst", "learner"));

        ArgumentCaptor<AdminRemoveUserFromGroupRequest> removed =
                ArgumentCaptor.forClass(AdminRemoveUserFromGroupRequest.class);
        verify(cognito).adminRemoveUserFromGroup(removed.capture());
        assertThat(removed.getAllValues()).singleElement()
                .satisfies(r -> assertThat(r.groupName()).isEqualTo("analyst"));
    }

    @Test
    void setRole_grantsTheNewGroup() {
        stubGroupsHeld("learner");

        directory.setRole("a@x.vn", "content_editor", Set.of("learner"));

        ArgumentCaptor<AdminAddUserToGroupRequest> added =
                ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        verify(cognito).adminAddUserToGroup(added.capture());
        assertThat(added.getValue().groupName()).isEqualTo("content_editor");
        assertThat(added.getValue().username()).isEqualTo("a@x.vn");
    }

    @Test
    void setRole_leavesAGroupTheAccountIsAlreadyInAlone() {
        // Nothing to revoke; the grant is idempotent at Cognito.
        stubGroupsHeld();

        directory.setRole("a@x.vn", "learner", Set.of("org_admin", "analyst"));

        verify(cognito, never()).adminRemoveUserFromGroup(any(AdminRemoveUserFromGroupRequest.class));
        verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    @Test
    void setRole_endsTheAccountsSessionsOnceTheGroupsHaveMoved() {
        stubGroupsHeld("org_admin");

        directory.setRole("boss@x.vn", "learner", Set.of("org_admin"));

        // Without this the demoted admin keeps minting tokens carrying org_admin
        // for as long as they keep refreshing.
        InOrder order = inOrder(cognito);
        order.verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
        order.verify(cognito).adminUserGlobalSignOut(any(AdminUserGlobalSignOutRequest.class));
    }

    @Test
    void setRole_whenSessionsCannotBeEnded_stillReportsTheRoleChange() {
        stubGroupsHeld("org_admin");
        when(cognito.adminUserGlobalSignOut(any(AdminUserGlobalSignOutRequest.class)))
                .thenThrow(TooManyRequestsException.builder().message("slow down").build());

        // The group move already happened. Failing the request here would roll the
        // row back and leave the pool and the database disagreeing about the role.
        directory.setRole("boss@x.vn", "learner", Set.of("org_admin"));

        verify(cognito).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    @Test
    void setRole_whenThereIsNoAccount_saysSoRatherThanReportingSuccess() {
        when(cognito.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("no user").build());

        // Users predating the directory have no account to move. Reporting a role
        // change that changed nothing is the bug this whole path exists to fix.
        assertThatThrownBy(() -> directory.setRole("legacy@x.vn", "analyst", Set.of("learner")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }

    @Test
    void deleteUser_removesTheAccount() {
        directory.deleteUser("gone@x.vn");

        ArgumentCaptor<AdminDeleteUserRequest> req =
                ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
        verify(cognito).adminDeleteUser(req.capture());
        assertThat(req.getValue().userPoolId()).isEqualTo(POOL);
        assertThat(req.getValue().username()).isEqualTo("gone@x.vn");
    }

    @Test
    void deleteUser_whenTheAccountIsAlreadyGone_isNotAnError() {
        when(cognito.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("no user").build());

        // Users predating the directory never had one, and a retried delete finds
        // nothing the second time. The caller wants them gone; they are gone.
        directory.deleteUser("legacy@x.vn");
    }

    @Test
    void deleteUser_whenCognitoRefuses_surfacesTheFailure() {
        when(cognito.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(TooManyRequestsException.builder().message("slow down").build());

        // The row must not be deleted behind a failure here, so this has to throw.
        assertThatThrownBy(() -> directory.deleteUser("x@x.vn"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void enablingTheDirectoryWithoutAPoolIsRefusedAtStartup() {
        // Later is worse: the admin who finds out is the one adding a user, and the
        // person who configured it is long gone.
        assertThatThrownBy(() -> new CognitoUserDirectory(cognito, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-pool-id");
    }
}
