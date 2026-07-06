package com.digishield.auth.api;

import java.util.List;

/**
 * SPI for the credential / token operations of authentication. The auth module
 * ships a dev {@code StubAuthProvider}; the boot application supplies a real
 * {@code CognitoAuthProvider} (AWS Cognito) when
 * {@code digishield.auth.cognito.enabled=true}. Keeps the AWS SDK out of the
 * business module, mirroring the notification email/SMS gateways.
 *
 * <p>User management (list/create/current-user) is <em>not</em> here — that stays
 * in {@code AuthService}; this covers only login / token / MFA / password reset.
 */
public interface AuthProvider {

    /**
     * Authenticates a user and returns access/refresh tokens.
     *
     * @throws MfaChallengeRequiredException if the account requires an MFA code to
     *         finish signing in (the caller then calls {@link #mfaChallenge}).
     */
    TokenPair login(String email, String password);

    /** Exchanges a refresh token for a fresh access token. */
    TokenPair refresh(String refreshToken);

    /** Completes a federated (SAML/OIDC) sign-in from a callback assertion. */
    TokenPair ssoCallback(String org, String assertion);

    /** Starts a password reset (sends a code to the account's email). */
    void forgotPassword(String email);

    /** Completes a password reset with the code carried by {@code token}. */
    void resetPassword(String token, String newPassword);

    /** Begins MFA (TOTP) enrollment for the given account. */
    MfaSetupView mfaSetup(String accountEmail);

    /** Confirms MFA enrollment with a code; returns one-time recovery codes. */
    List<String> mfaVerify(String code);

    /** Completes an MFA-gated login by answering the challenge with a code. */
    TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice);
}
