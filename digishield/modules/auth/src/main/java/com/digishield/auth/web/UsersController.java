package com.digishield.auth.web;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.ImportResult;
import com.digishield.auth.api.UserUpsert;
import com.digishield.auth.api.UserView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller backing the Users screen.
 *
 * <p>Endpoints (all under {@code /api/v1}):
 * <ul>
 *   <li>{@code GET /users} — list users of the current tenant</li>
 *   <li>{@code POST /users} — create a user (returns {@code 201})</li>
 *   <li>{@code GET /users/{id}} — user details</li>
 *   <li>{@code PATCH /users/{id}} — update role / department / locale</li>
 *   <li>{@code POST /users/import} — bulk import (returns {@code 202})</li>
 * </ul>
 *
 * <p>The returned {@link UserView} carries both the OpenAPI {@code User} fields
 * ({@code org_id}, {@code department_id}, {@code risk_score}, {@code locale},
 * snake_case {@code role}) and the {@code name}/{@code department}/{@code riskScore}
 * fields the frontend Users screen reads.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ORG_ADMIN')")
class UsersController {

    private final AuthService authService;

    UsersController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    ResponseEntity<List<UserView>> list() {
        return ResponseEntity.ok(authService.listUsers());
    }

    @PostMapping
    ResponseEntity<UserView> create(@RequestBody UserInputRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(request.toUpsert()));
    }

    @GetMapping("/{id}")
    ResponseEntity<UserView> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(authService.getUser(id));
    }

    @PatchMapping("/{id}")
    ResponseEntity<UserView> update(@PathVariable("id") UUID id,
                                    @RequestBody UserInputRequest request) {
        return ResponseEntity.ok(authService.updateUser(id, request.toUpsert()));
    }

    @PostMapping("/import")
    ResponseEntity<ImportResult> importUsers(@RequestBody ImportRequest request) {
        List<UserUpsert> users = request.users() == null
                ? List.of()
                : request.users().stream().map(UserInputRequest::toUpsert).toList();
        return ResponseEntity.accepted().body(authService.importUsers(users));
    }

    /**
     * Request body matching the OpenAPI {@code UserInput} schema (snake_case wire
     * field {@code department_id}).
     */
    record UserInputRequest(
            String email,
            String role,
            @JsonProperty("department_id") UUID departmentId,
            String locale) {

        UserUpsert toUpsert() {
            return new UserUpsert(email, role, departmentId, locale);
        }
    }

    /** Bulk-import request body ({@code { "users": [ UserInput, ... ] }}). */
    record ImportRequest(List<UserInputRequest> users) {
    }
}
