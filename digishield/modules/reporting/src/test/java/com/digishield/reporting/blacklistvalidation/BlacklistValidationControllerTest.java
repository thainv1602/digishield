package com.digishield.reporting.blacklistvalidation;

import com.digishield.reporting.blacklistvalidation.BlacklistValidationController.ValidateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller unit test - constructs the controller with a real validator and
 * asserts the HTTP mapping logic (status + body). Fast, no Spring context.
 *
 * <p><b>TODO (group 01):</b> upgrade to {@code @WebMvcTest(BlacklistValidationController.class)}
 * to also exercise JSON (de)serialization and {@code @PreAuthorize('ANALYST')} - remember to add
 * {@code testImplementation("org.springframework.boot:spring-boot-starter-web")} to the module's
 * {@code build.gradle.kts} for that slice test.
 */
class BlacklistValidationControllerTest {

    private final BlacklistValidationController controller =
            new BlacklistValidationController(new BlacklistValueValidator());

    @Test
    void returns200AndNormalizedValueForValidInput() {
        ResponseEntity<ValidationResult> resp =
                controller.validate(new ValidateRequest("domain", "  Evil.COM "));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().valid()).isTrue();
        assertThat(resp.getBody().normalized()).isEqualTo("evil.com");
    }

    @Test
    void returns200ButInvalidFlagForBadValue() {
        ResponseEntity<ValidationResult> resp =
                controller.validate(new ValidateRequest("ip", "999.1.1.1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().valid()).isFalse();
        assertThat(resp.getBody().reason()).isEqualTo("bad_ip");
    }

    @Test
    void returns400ForUnknownType() {
        ResponseEntity<ValidationResult> resp =
                controller.validate(new ValidateRequest("bank", "123"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().reason()).isEqualTo("unknown_type");
    }
}
