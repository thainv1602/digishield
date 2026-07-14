package com.digishield.reporting.blacklistvalidation;

import com.digishield.reporting.domain.BlacklistType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * New endpoint for BTL group 01: {@code POST /api/v1/blacklist/validate}.
 *
 * <p>Dedicated path (does not touch the existing {@code POST /api/v1/blacklist})
 * so this feature can be developed without colliding with other groups working
 * in the same module.
 */
@RestController
@RequestMapping("/api/v1/blacklist")
@PreAuthorize("hasRole('ANALYST')")
public class BlacklistValidationController {

    private final BlacklistValueValidator validator;

    public BlacklistValidationController(BlacklistValueValidator validator) {
        this.validator = validator;
    }

    /**
     * Validate + normalize a candidate blacklist value without storing it.
     *
     * @param request the value and its type
     * @return 200 with the validation result (also for invalid values — the
     *         body's {@code valid} flag carries the verdict)
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody ValidateRequest request) {
        BlacklistType type;
        try {
            type = BlacklistType.valueOf(request.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(new ValidationResult(false, "", "unknown_type"));
        }
        return ResponseEntity.ok(validator.validate(type, request.value()));
    }

    /**
     * Validate-blacklist payload.
     *
     * @param type  entry type (e.g. "url", "phone", "domain")
     * @param value the value to validate
     */
    public record ValidateRequest(String type, String value) {
    }
}
