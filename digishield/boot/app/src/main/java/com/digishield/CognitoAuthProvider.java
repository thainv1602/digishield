package com.digishield;

import com.digishield.auth.api.AuthProvider;
import com.digishield.auth.api.MfaChallengeRequiredException;
import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real {@link AuthProvider} backed by AWS Cognito (SDK v2). Active only when
 * {@code digishield.auth.cognito.enabled=true}; {@code @Primary} so it wins over
 * the dev {@code StubAuthProvider}. Lives in the boot app so the AWS SDK stays out
 * of the auth module (same pattern as the SES/SNS gateways).
 *
 * <p>Implements the password-based flows: login ({@code USER_PASSWORD_AUTH}),
 * refresh, MFA challenge completion, and forgot/reset password. SSO federation
 * and TOTP enrollment go through the Cognito hosted UI and are reported as
 * unsupported here.
 */
@Component
@Primary
@ConditionalOnProperty(name = "digishield.auth.cognito.enabled", havingValue = "true")
class CognitoAuthProvider implements AuthProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CognitoAuthProvider.class);
    private static final String MFA_TOKEN_SEP = "|";

    private final CognitoIdentityProviderClient cognito;
    private final String clientId;
    private final String clientSecret;

    @org.springframework.beans.factory.annotation.Autowired
    CognitoAuthProvider(@Value("${digishield.auth.cognito.client-id}") String clientId,
                        @Value("${digishield.auth.cognito.client-secret:}") String clientSecret,
                        @Value("${digishield.auth.cognito.region:}") String region) {
        this(buildClient(region), clientId, clientSecret);
        LOG.info("CognitoAuthProvider active (clientId={}, secretHash={})",
                clientId, StringUtils.hasText(clientSecret) ? "on" : "off");
    }

    /** Test seam: inject a (mock) Cognito client directly. */
    CognitoAuthProvider(CognitoIdentityProviderClient cognito, String clientId, String clientSecret) {
        this.cognito = cognito;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private static CognitoIdentityProviderClient buildClient(String region) {
        var builder = CognitoIdentityProviderClient.builder();
        if (StringUtils.hasText(region)) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    @Override
    public TokenPair login(String email, String password) {
        Map<String, String> params = new HashMap<>();
        params.put("USERNAME", email);
        params.put("PASSWORD", password);
        secretHash(email).ifPresent(h -> params.put("SECRET_HASH", h));
        try {
            InitiateAuthResponse resp = cognito.initiateAuth(b -> b
                    .clientId(clientId)
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(params));
            if (resp.authenticationResult() != null) {
                return toTokenPair(resp.authenticationResult(), null);
            }
            // A challenge (MFA / new-password) — hand the session back to the client.
            String challenge = resp.challengeNameAsString();
            String token = String.join(MFA_TOKEN_SEP, challenge, email, resp.session());
            throw new MfaChallengeRequiredException(challenge, token);
        } catch (NotAuthorizedException | UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        Map<String, String> params = new HashMap<>();
        params.put("REFRESH_TOKEN", refreshToken);
        // SECRET_HASH for refresh uses the token's username, which we don't have here;
        // secret-hash + refresh requires it be baked into the app client config instead.
        try {
            InitiateAuthResponse resp = cognito.initiateAuth(b -> b
                    .clientId(clientId)
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .authParameters(params));
            // Cognito does not rotate the refresh token on refresh — echo the one supplied.
            return toTokenPair(resp.authenticationResult(), refreshToken);
        } catch (NotAuthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }
    }

    @Override
    public TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice) {
        String[] parts = mfaToken.split("\\" + MFA_TOKEN_SEP, 3);
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed mfa_token");
        }
        ChallengeNameType challenge = ChallengeNameType.fromValue(parts[0]);
        String username = parts[1];
        String session = parts[2];
        String codeKey = challenge == ChallengeNameType.SMS_MFA ? "SMS_MFA_CODE" : "SOFTWARE_TOKEN_MFA_CODE";
        Map<String, String> responses = new HashMap<>();
        responses.put("USERNAME", username);
        responses.put(codeKey, code);
        secretHash(username).ifPresent(h -> responses.put("SECRET_HASH", h));
        try {
            var resp = cognito.respondToAuthChallenge(b -> b
                    .clientId(clientId)
                    .challengeName(challenge)
                    .session(session)
                    .challengeResponses(responses));
            return toTokenPair(resp.authenticationResult(), null);
        } catch (CodeMismatchException | ExpiredCodeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired MFA code");
        } catch (NotAuthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA session expired");
        }
    }

    @Override
    public void forgotPassword(String email) {
        try {
            cognito.forgotPassword(b -> {
                b.clientId(clientId).username(email);
                secretHash(email).ifPresent(b::secretHash);
            });
        } catch (UserNotFoundException e) {
            // Do not reveal whether the account exists.
            LOG.info("[auth] Forgot-password requested for unknown account");
        }
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        // token carries "email|code" from the reset link/form.
        String[] parts = token == null ? new String[0] : token.split("\\" + MFA_TOKEN_SEP, 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reset token must be 'email|code'");
        }
        String email = parts[0];
        String code = parts[1];
        try {
            cognito.confirmForgotPassword(b -> {
                b.clientId(clientId).username(email).confirmationCode(code).password(newPassword);
                secretHash(email).ifPresent(b::secretHash);
            });
        } catch (CodeMismatchException | ExpiredCodeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
        }
    }

    @Override
    public TokenPair ssoCallback(String org, String assertion) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "SSO is handled by the Cognito hosted UI, not this endpoint");
    }

    @Override
    public MfaSetupView mfaSetup(String accountEmail) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "TOTP enrollment requires the signed-in access token — use the hosted UI");
    }

    @Override
    public List<String> mfaVerify(String code) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "TOTP enrollment requires the signed-in access token — use the hosted UI");
    }

    private TokenPair toTokenPair(AuthenticationResultType result, String fallbackRefresh) {
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication did not complete");
        }
        String refresh = result.refreshToken() != null ? result.refreshToken() : fallbackRefresh;
        long expires = result.expiresIn() != null ? result.expiresIn() : 3600L;
        return new TokenPair(result.accessToken(), refresh, expires);
    }

    /** Cognito SECRET_HASH = Base64(HmacSHA256(clientSecret, username + clientId)). */
    private java.util.Optional<String> secretHash(String username) {
        if (!StringUtils.hasText(clientSecret)) {
            return java.util.Optional.empty();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((username + clientId).getBytes(StandardCharsets.UTF_8));
            return java.util.Optional.of(Base64.getEncoder().encodeToString(digest));
        } catch (Exception e) {  // noqa
            throw new IllegalStateException("Could not compute Cognito SECRET_HASH", e);
        }
    }
}
