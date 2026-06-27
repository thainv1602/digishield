package com.digishield.auth.web;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserUpsert;
import com.digishield.auth.api.UserView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal SCIM 2.0 endpoint for IdP user provisioning ({@code /api/v1/scim/v2/Users}).
 *
 * <p>Maps SCIM {@code User} resources onto the module's {@link UserView}/AppUser:
 * <ul>
 *   <li>{@code GET} returns a SCIM {@code ListResponse} of the current tenant's users</li>
 *   <li>{@code POST} creates a user from {@code userName}/{@code emails} and returns
 *       the created SCIM {@code User} ({@code 201})</li>
 * </ul>
 * Scoping is handled by {@link AuthService} via the tenant context. This is a
 * pragmatic subset (no filtering/paging/PATCH) sufficient for dev IdP sync.
 */
@RestController
@RequestMapping("/api/v1/scim/v2/Users")
class ScimController {

    private static final String LIST_RESPONSE_SCHEMA =
            "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private static final String USER_SCHEMA =
            "urn:ietf:params:scim:schemas:core:2.0:User";

    private final AuthService authService;

    ScimController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    ResponseEntity<Map<String, Object>> list() {
        List<UserView> users = authService.listUsers();
        List<Map<String, Object>> resources = users.stream().map(ScimController::toScim).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(LIST_RESPONSE_SCHEMA));
        body.put("totalResults", resources.size());
        body.put("startIndex", 1);
        body.put("itemsPerPage", resources.size());
        body.put("Resources", resources);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> create(@RequestBody ScimUserRequest request) {
        String email = request.resolveEmail();
        UserView created = authService.createUser(
                new UserUpsert(email, "learner", null, null));
        return ResponseEntity.status(HttpStatus.CREATED).body(toScim(created));
    }

    private static Map<String, Object> toScim(UserView u) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("schemas", List.of(USER_SCHEMA));
        resource.put("id", u.id() != null ? u.id().toString() : null);
        resource.put("userName", u.email());
        if (u.name() != null) {
            resource.put("name", Map.of("formatted", u.name()));
            resource.put("displayName", u.name());
        }
        resource.put("emails", List.of(Map.of("value", u.email(), "primary", true)));
        resource.put("active", !"disabled".equalsIgnoreCase(u.status()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "User");
        resource.put("meta", meta);
        return resource;
    }

    /**
     * Minimal SCIM {@code User} create body. Accepts {@code userName} and/or
     * {@code emails:[{value,...}]}; either supplies the login email.
     */
    record ScimUserRequest(String userName, List<ScimEmail> emails) {

        String resolveEmail() {
            if (userName != null && !userName.isBlank()) {
                return userName;
            }
            if (emails != null) {
                for (ScimEmail e : emails) {
                    if (e != null && e.value() != null && !e.value().isBlank()) {
                        return e.value();
                    }
                }
            }
            throw new IllegalArgumentException("SCIM user requires userName or an email value");
        }
    }

    /** A SCIM email entry. */
    record ScimEmail(String value, String type, Boolean primary) {
    }
}
