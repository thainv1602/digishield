package com.digishield.auth.web;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.api.TokenPair;
import com.digishield.auth.domain.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
     * Refresh access token.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenPair> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refresh_token()));
    }

    /**
     * Returns information about the current user.
     */
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
