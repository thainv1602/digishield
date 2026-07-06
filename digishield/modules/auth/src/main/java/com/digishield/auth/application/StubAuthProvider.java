package com.digishield.auth.application;

import com.digishield.auth.api.AuthProvider;
import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic dev {@link AuthProvider}: returns static demo tokens without
 * validating credentials, and generates a local (non-persisted) TOTP secret /
 * recovery codes. Used in the {@code dev}/prod-like profiles and as the fallback
 * when the real {@code CognitoAuthProvider} is not enabled. The boot app's
 * Cognito provider is {@code @Primary} and wins injection when active.
 */
@Component
public class StubAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(StubAuthProvider.class);

    static final String DEV_ACCESS_TOKEN = "dev-access-token";
    static final String DEV_REFRESH_TOKEN = "dev-refresh-token";
    static final long DEV_EXPIRES_IN_SECONDS = 3600L;

    /** RFC 4648 base32 alphabet used for the (dev) TOTP secret. */
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int TOTP_SECRET_LENGTH = 32;
    private static final int RECOVERY_CODE_COUNT = 8;

    private final SecureRandom random = new SecureRandom();

    private static TokenPair devTokens() {
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
    }

    @Override
    public TokenPair login(String email, String password) {
        return devTokens();
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        return devTokens();
    }

    @Override
    public TokenPair ssoCallback(String org, String assertion) {
        return devTokens();
    }

    @Override
    public void forgotPassword(String email) {
        log.info("[auth] Password reset requested (dev no-op)");
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        log.info("[auth] Password reset completed (dev no-op)");
    }

    @Override
    public MfaSetupView mfaSetup(String accountEmail) {
        String account = (accountEmail == null || accountEmail.isBlank()) ? "user@digishield.vn" : accountEmail;
        String secret = generateBase32Secret();
        String label = URLEncoder.encode("DigiShield:" + account, StandardCharsets.UTF_8);
        String otpauthUrl = "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=DigiShield&algorithm=SHA1&digits=6&period=30";
        return new MfaSetupView(secret, otpauthUrl, sampleQrSvg(otpauthUrl));
    }

    @Override
    public List<String> mfaVerify(String code) {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomRecoveryCode());
        }
        return codes;
    }

    @Override
    public TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice) {
        return devTokens();
    }

    private String generateBase32Secret() {
        StringBuilder sb = new StringBuilder(TOTP_SECRET_LENGTH);
        for (int i = 0; i < TOTP_SECRET_LENGTH; i++) {
            sb.append(BASE32[random.nextInt(BASE32.length)]);
        }
        return sb.toString();
    }

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(BASE32[random.nextInt(BASE32.length)]);
        }
        return sb.toString();
    }

    private static String sampleQrSvg(String otpauthUrl) {
        String safe = otpauthUrl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"160\" height=\"160\" "
                + "viewBox=\"0 0 160 160\"><title>" + safe + "</title>"
                + "<rect width=\"160\" height=\"160\" fill=\"#ffffff\"/>"
                + "<rect x=\"16\" y=\"16\" width=\"128\" height=\"128\" fill=\"none\" "
                + "stroke=\"#000000\" stroke-width=\"8\"/>"
                + "<text x=\"80\" y=\"86\" text-anchor=\"middle\" font-family=\"monospace\" "
                + "font-size=\"12\" fill=\"#000000\">QR</text></svg>";
    }
}
