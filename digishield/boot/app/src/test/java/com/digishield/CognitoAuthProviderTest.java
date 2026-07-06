package com.digishield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.digishield.auth.api.MfaChallengeRequiredException;
import com.digishield.auth.api.TokenPair;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

/**
 * Unit tests for {@link CognitoAuthProvider} against a mocked Cognito client.
 */
class CognitoAuthProviderTest {

    private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
    private final CognitoAuthProvider provider = new CognitoAuthProvider(cognito, "client-id", "");

    @SuppressWarnings("unchecked")
    private void stubInitiateAuth(InitiateAuthResponse response) {
        when(cognito.initiateAuth(any(Consumer.class))).thenReturn(response);
    }

    @Test
    void login_returnsTokensOnSuccess() {
        stubInitiateAuth(InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("access-1").refreshToken("refresh-1").expiresIn(3600).build())
                .build());

        TokenPair tokens = provider.login("user@x.vn", "pw");

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
        assertThat(tokens.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void login_whenMfaRequired_throwsWithChallengeAndToken() {
        stubInitiateAuth(InitiateAuthResponse.builder()
                .challengeName(ChallengeNameType.SOFTWARE_TOKEN_MFA)
                .session("sess-123")
                .build());

        assertThatThrownBy(() -> provider.login("user@x.vn", "pw"))
                .isInstanceOf(MfaChallengeRequiredException.class)
                .satisfies(ex -> {
                    MfaChallengeRequiredException mfa = (MfaChallengeRequiredException) ex;
                    assertThat(mfa.getChallengeName()).isEqualTo("SOFTWARE_TOKEN_MFA");
                    assertThat(mfa.getMfaToken()).isEqualTo("SOFTWARE_TOKEN_MFA|user@x.vn|sess-123");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_whenNotAuthorized_throws401() {
        when(cognito.initiateAuth(any(Consumer.class)))
                .thenThrow(NotAuthorizedException.builder().message("bad").build());

        assertThatThrownBy(() -> provider.login("user@x.vn", "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void mfaChallenge_rejectsMalformedToken() {
        assertThatThrownBy(() -> provider.mfaChallenge("no-separators", "000000", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void ssoCallback_isNotSupported() {
        assertThatThrownBy(() -> provider.ssoCallback("acme", "assertion"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(501));
    }
}
