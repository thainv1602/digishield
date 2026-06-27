package com.digishield.auth.web;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller backing the Users screen.
 *
 * <p>{@code GET /api/v1/users} lists the users of the current tenant. The
 * returned {@link UserView} carries both the OpenAPI {@code User} fields
 * ({@code org_id}, {@code risk_score}, snake_case {@code role}) and the
 * {@code name}/{@code department}/{@code riskScore} fields the frontend reads.
 */
@RestController
@RequestMapping("/api/v1/users")
class UsersController {

    private final AuthService authService;

    UsersController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    ResponseEntity<List<UserView>> list() {
        return ResponseEntity.ok(authService.listUsers());
    }
}
