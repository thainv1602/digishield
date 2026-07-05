package com.digishield.auth.web;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
import com.digishield.auth.domain.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller for the Auth module.
 *
 * <p>Endpoints align with the OpenAPI contract and the React frontend:
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} -> {@link TokenPair}</li>
 *   <li>{@code POST /api/v1/auth/refresh} -> {@link TokenPair}</li>
 *   <li>{@code GET /api/v1/auth/me} -> the current user</li>
 * </ul>
 *
 * <p>In the dev profile credentials are not validated; an optional {@code role}
 * (login body) or {@code X-Demo-Role} header selects the demo persona.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    /** Header used in dev to switch the demo persona returned by {@code /me}. */
    static final String DEMO_ROLE_HEADER = "X-Demo-Role";

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Internal login. Dev: returns static demo tokens (no credential check).
     */
    @PostMapping("/login")
    ResponseEntity<TokenPair> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                authService.login(request.email(), request.password(), request.role()));
    }

    /**
     * SSO callback (SAML/OAuth). Dev: accepts {@code {org, assertion}} without
     * verification and returns demo tokens.
     */
    @PostMapping("/sso/callback")
    ResponseEntity<TokenPair> ssoCallback(@RequestBody SsoCallbackRequest request) {
        return ResponseEntity.ok(authService.ssoCallback(request.org(), request.assertion()));
    }

    /**
     * Refresh access token.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenPair> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refresh_token()));
    }

    /**
     * Request a password-reset email. Dev: no-op; always {@code 202} so the
     * response does not reveal whether the account exists.
     */
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    /**
     * Reset a password using a token. Dev: accepts {@code {token, new_password}}.
     */
    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.new_password());
        return ResponseEntity.ok().build();
    }

    /**
     * Initialize MFA: returns {@code {secret, otpauth_url, qr_svg}}.
     */
    @PostMapping("/mfa/setup")
    ResponseEntity<MfaSetupView> mfaSetup() {
        return ResponseEntity.ok(authService.mfaSetup());
    }

    /**
     * Confirm MFA activation. Dev: accepts any code and returns recovery codes.
     */
    @PostMapping("/mfa/verify")
    ResponseEntity<Map<String, List<String>>> mfaVerify(@RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(Map.of("recovery_codes", authService.mfaVerify(request.code())));
    }

    /**
     * Verify an MFA code during login. Dev: accepts any code and returns tokens.
     */
    @PostMapping("/mfa/challenge")
    ResponseEntity<TokenPair> mfaChallenge(@RequestBody MfaChallengeRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(
                authService.mfaChallenge(request.mfa_token(), request.code(), request.trust_device()));
    }

    /**
     * Returns information about the current user.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    ResponseEntity<MeResponse> me(
            @RequestHeader(name = DEMO_ROLE_HEADER, required = false) String demoRole) {
        return authService.currentUser(demoRole)
                .map(MeResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** Login request body. {@code role} is an optional dev demo-persona hint. */
    record LoginRequest(String email, String password, String role) {
    }

    /** Refresh request body (snake_case to match the OpenAPI contract). */
    record RefreshRequest(String refresh_token) {
    }

    /** SSO callback request body. */
    record SsoCallbackRequest(String org, String assertion) {
    }

    /** Forgot-password request body. */
    record ForgotPasswordRequest(String email) {
    }

    /** Reset-password request body (snake_case {@code new_password}). */
    record ResetPasswordRequest(String token, String new_password) {
    }

    /** MFA verify request body. */
    record MfaVerifyRequest(String code) {
    }

    /** MFA challenge request body (snake_case {@code mfa_token}, {@code trust_device}). */
    record MfaChallengeRequest(String mfa_token, String code, boolean trust_device) {
    }

    /**
     * DTO returned by the {@code /me} endpoint, shaped for the frontend:
     * {@code { id, tenantId, email, role, name }} with the snake_case wire role.
     */
    record MeResponse(String id, UUID tenantId, String email, String role, String name) {
        static MeResponse from(CurrentUser user) {
            String wireRole = user.role() == null
                    ? null
                    : Role.valueOf(user.role()).wireName();
            return new MeResponse(
                    String.valueOf(user.id()),
                    user.tenantId(),
                    user.email(),
                    wireRole,
                    user.name()
            );
        }
    }
}
