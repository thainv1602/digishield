package com.digishield.security.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the authorization rules actually deny, using the {@code dev-secure}
 * profile.
 *
 * <p>Until that profile existed this could not be written. {@code @PreAuthorize}
 * lives on the {@code @Profile("!dev")} security configuration and
 * {@code DevSecurityConfig} permits everything, so under {@code dev} every one
 * of these calls returned 200 — the matrix in {@code docs/AUTHZ_MATRIX.md} was
 * enforced only in production, where nobody wants to discover a mistake.
 *
 * <p>Needs no Docker: {@code dev-secure} runs on H2 and mints its own tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-secure")
class AuthorizationEnforcementIT {

    private static final String OWN_TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_TENANT = "99999999-9999-9999-9999-999999999999";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private String get(String path, String bearer) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + path));
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> r = client.send(b.GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() + "|" + r.body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String tokenFor(String role) {
        String body = get("/dev/token?email=" + role + "@dev.local", null);
        assertThat(body).startsWith("200|");
        Matcher m = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as("dev token endpoint returned a token").isTrue();
        return m.group(1);
    }

    private int statusOf(String path, String role) {
        String bearer = role == null ? null : tokenFor(role);
        return Integer.parseInt(get(path, bearer).split("\\|", 2)[0]);
    }

    @Test
    @DisplayName("without a token the API answers 401, not 200")
    void anonymousIsRejected() {
        assertThat(statusOf("/api/v1/reports/phishing", null)).isEqualTo(401);
    }

    @ParameterizedTest(name = "{1} on {0} -> {2}")
    @CsvSource({
        // The SOC queue is ANALYST-only; a learner reaching it would see every
        // reported message in the tenant.
        "/api/v1/reports/phishing,          analyst, 200",
        "/api/v1/reports/phishing,          learner, 403",
        // Not 403: the hierarchy is SUPER_ADMIN -> ORG_ADMIN -> (MANAGER,
        // ANALYST, CONTENT_EDITOR) -> LEARNER, and an endpoint names the
        // *minimum* role, so an org admin satisfies an ANALYST rule. This case
        // is kept because it pins that hierarchy: if someone flattens it, the
        // expectation breaks here rather than in production.
        "/api/v1/reports/phishing,          org_admin, 200",
        // A learner's own reports are theirs to read.
        "/api/v1/reports/phishing/mine/11111111-1111-1111-1111-111111111111, learner, 200",
        // Platform operations are SUPER_ADMIN-only.
        "/api/v1/tenants,                   super_admin, 200",
        "/api/v1/tenants,                   org_admin, 403",
        "/api/v1/tenants,                   learner, 403",
    })
    void roleDecidesAccess(String path, String role, int expected) {
        assertThat(statusOf(path, role)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an org admin reaches their own tenant and no other")
    void tenantGuardConfinesAnOrgAdminToItsOwnTenant() {
        // BR-7: one organisation may never read another's data, under any role
        // short of the platform operator.
        assertThat(statusOf("/api/v1/tenants/" + OWN_TENANT + "/settings", "org_admin"))
                .isEqualTo(200);
        assertThat(statusOf("/api/v1/tenants/" + OTHER_TENANT + "/settings", "org_admin"))
                .isEqualTo(403);
        assertThat(statusOf("/api/v1/tenants/" + OTHER_TENANT + "/settings", "super_admin"))
                .isEqualTo(200);
    }
}
