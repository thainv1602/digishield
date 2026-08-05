package com.digishield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Unit tests for {@link CognitoUserDirectory} against a mocked Cognito client.
 */
class CognitoUserDirectoryTest {

    private static final String POOL = "us-east-1_test";

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

        directory.createUser("new@x.vn", "analyst");

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
    void createUser_putsTheAccountInTheRolesGroup() {
        stubCreate(UUID.randomUUID());

        directory.createUser("new@x.vn", "analyst");

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

        assertThat(directory.createUser("new@x.vn", "learner")).contains(subject);
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

        Optional<UUID> result = directory.createUser("old@x.vn", "manager");

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
        assertThatThrownBy(() -> directory.createUser("new@x.vn", "nope"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void createUser_whenCognitoRejectsTheAddress_isABadRequest() {
        when(cognito.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(InvalidParameterException.builder().message("bad email").build());

        assertThatThrownBy(() -> directory.createUser("not-an-email", "learner"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
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
