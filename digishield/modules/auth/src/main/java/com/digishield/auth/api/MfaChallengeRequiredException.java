package com.digishield.auth.api;

/**
 * Thrown by {@link AuthProvider#login} when the credentials are valid but the
 * account must answer an MFA challenge to finish signing in. Carries the
 * provider challenge name and an opaque {@code mfaToken} the client passes back
 * to {@link AuthProvider#mfaChallenge}. The web layer renders this as a
 * {@code 401} with {@code {challenge_name, mfa_token}}.
 */
public class MfaChallengeRequiredException extends RuntimeException {

    private final String challengeName;
    private final String mfaToken;

    public MfaChallengeRequiredException(String challengeName, String mfaToken) {
        super("MFA challenge required: " + challengeName);
        this.challengeName = challengeName;
        this.mfaToken = mfaToken;
    }

    public String getChallengeName() {
        return challengeName;
    }

    public String getMfaToken() {
        return mfaToken;
    }
}
