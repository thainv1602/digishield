package com.digishield.reporting.blacklistvalidation;

import com.digishield.reporting.domain.BlacklistType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlacklistValueValidator} - pure logic, no Spring.
 *
 * <p>These tests already drive the validator to high branch coverage; they are
 * the model the group extends when adding rules. Techniques on show:
 * equivalence partitioning, boundary-value analysis, and data-driven tests
 * ({@code @ParameterizedTest}) - exactly the session 3-5 material.
 */
class BlacklistValueValidatorTest {

    private final BlacklistValueValidator validator = new BlacklistValueValidator();

    // --- normalization (valid, canonical form) --------------------------------

    @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> \"{2}\"")
    @CsvSource({
            "DOMAIN, '  Evil.COM. ',            evil.com",
            "EMAIL,  'Boss@Company.VN',         boss@company.vn",
            "IP,     '10.0.0.255',              10.0.0.255",
            "PHONE,  '+84 909-123-456',         +84909123456",
            "PHONE,  '0909123456',              +0909123456",
            "HASH,   'ABCDEF0123456789ABCDEF0123456789', abcdef0123456789abcdef0123456789",
    })
    void normalizesValidValues(BlacklistType type, String raw, String expected) {
        ValidationResult r = validator.validate(type, raw);
        assertThat(r.valid()).isTrue();
        assertThat(r.normalized()).isEqualTo(expected);
        assertThat(r.reason()).isEqualTo("ok");
    }

    @Test
    void normalizesUrlSchemeAndHost() {
        ValidationResult r = validator.validate(BlacklistType.URL, "HTTPS://Evil.COM/Path");
        assertThat(r.valid()).isTrue();
        assertThat(r.normalized()).isEqualTo("https://evil.com/Path");
    }

    // --- rejection (bad format) -----------------------------------------------

    @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> {2}")
    @CsvSource({
            "DOMAIN, 'no spaces here',   bad_domain",
            "DOMAIN, 'nodot',            bad_domain",
            "EMAIL,  'not-an-email',     bad_email",
            "URL,    'evil.com',         bad_url",
            "IP,     '10.0.0',           bad_ip",
            "IP,     '256.0.0.1',        bad_ip",
            "IP,     '10.0.0.x',         bad_ip",
            "PHONE,  '+84-abc',          bad_phone",
            "HASH,   'xyz',              bad_hash",
            "HASH,   'abcd',             bad_hash",
    })
    void rejectsBadValues(BlacklistType type, String raw, String reason) {
        ValidationResult r = validator.validate(type, raw);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo(reason);
        assertThat(r.normalized()).isEqualTo(raw);
    }

    // --- boundary-value analysis on the IP octet 0..255 -----------------------

    @ParameterizedTest(name = "octet {0} valid={1}")
    @CsvSource({"0, true", "1, true", "255, true", "256, false", "-1, false"})
    void ipOctetBoundaries(String octet, boolean valid) {
        assertThat(validator.validate(BlacklistType.IP, "10.0.0." + octet).valid())
                .isEqualTo(valid);
    }

    // --- empty / null are always invalid, for every type ----------------------

    @ParameterizedTest
    @EnumSource(BlacklistType.class)
    void nullAndBlankRejectedForEveryType(BlacklistType type) {
        assertThat(validator.validate(type, null).reason()).isEqualTo("empty");
        assertThat(validator.validate(type, "   ").reason()).isEqualTo("empty");
    }

    // TODO (group 01): add cases for IPv6, phone length boundaries 8..15,
    //                  and SHA-1 (40) / SHA-256 (64) hash lengths.
}
