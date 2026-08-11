package com.digishield;

import com.digishield.auth.api.AuthProvider;
import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mints real, signed tokens for the {@code dev-secure} profile.
 *
 * <p>The stub provider returns the opaque string {@code dev-access-token},
 * which is fine while {@code dev} permits everything but carries no role, so
 * nothing can be authorized with it. This one issues a JWT with the claims the
 * production chain reads — {@code cognito:groups} for roles and {@code tid} for
 * the tenant — so {@code @PreAuthorize} and {@code TenantAccessGuard} behave
 * locally exactly as they do in production.
 *
 * <p>The role comes from the email local part, matching the seeded demo users
 * and the dev sign-in form ({@code analyst@…}, {@code org_admin@…}). That is a
 * convention for local runs, stated here rather than inferred: no password is
 * checked and no directory is consulted.
 */
@Component
@Primary
@Profile("dev-secure")
class DevSecureAuthProvider implements AuthProvider {

    private static final Duration TTL = Duration.ofHours(1);

    /** Aliases for the seeded demo accounts, whose local part is not the role. */
    private static final Map<String, String> ALIASES = Map.of(
            "admin", "ORG_ADMIN",
            "superadmin", "SUPER_ADMIN",
            "editor", "CONTENT_EDITOR");

    private final JwtEncoder encoder;

    DevSecureAuthProvider(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * Derives the role from the email local part: {@code analyst@x} and
     * {@code content_editor@x} both give CONTENT_EDITOR/ANALYST, and the seeded
     * {@code admin@}/{@code editor@}/{@code superadmin@} accounts map through
     * {@link #ALIASES}. Anything unrecognised gets LEARNER — the least
     * privileged role, so a typo cannot hand out access.
     */
    private static String roleOf(String email) {
        String local = email == null ? "" : email.split("@", 2)[0].trim().toLowerCase(Locale.ROOT);
        String alias = ALIASES.get(local);
        if (alias != null) {
            return alias;
        }
        String candidate = local.replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (candidate) {
            case "SUPER_ADMIN", "ORG_ADMIN", "MANAGER", "CONTENT_EDITOR", "ANALYST", "LEARNER" ->
                    candidate;
            default -> "LEARNER";
        };
    }

    private TokenPair mint(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("digishield-dev-secure")
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .subject(email)
                .claim("email", email)
                .claim("tid", DemoTenants.DEMO_TENANT_ID.toString())
                .claim("cognito:groups", List.of(roleOf(email)))
                .build();
        String token = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims))
                .getTokenValue();
        return new TokenPair(token, token, TTL.toSeconds());
    }

    @Override
    public TokenPair login(String email, String password) {
        return mint(email);
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        // The refresh token is the access token here; re-issue for the same subject.
        return mint(subjectOf(refreshToken));
    }

    @Override
    public TokenPair ssoCallback(String org, String assertion) {
        return mint(assertion);
    }

    private static String subjectOf(String token) {
        try {
            String payload = new String(java.util.Base64.getUrlDecoder()
                    .decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
            return m.find() ? m.group(1) : "learner@dev.digishield.local";
        } catch (RuntimeException e) {
            return "learner@dev.digishield.local";
        }
    }

    @Override
    public void forgotPassword(String email) {
        // No mail transport locally.
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        // No credential store locally.
    }

    @Override
    public MfaSetupView mfaSetup(String accountEmail) {
        throw new UnsupportedOperationException("MFA is not simulated in dev-secure");
    }

    @Override
    public List<String> mfaVerify(String code) {
        throw new UnsupportedOperationException("MFA is not simulated in dev-secure");
    }

    @Override
    public TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice) {
        throw new UnsupportedOperationException("MFA is not simulated in dev-secure");
    }
}
