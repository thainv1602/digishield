package com.digishield.reporting.blacklistvalidation;

import com.digishield.reporting.domain.BlacklistType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes a value before it is stored on the blacklist.
 *
 * <p>Pure logic, no Spring / no database - this is the class the group drives to
 * &gt;90% branch coverage. Each {@link BlacklistType} has its own normalization +
 * validation rule; add cases and tests together.
 *
 * <p><b>TODO (group 01):</b>
 * <ul>
 *   <li>Add IPv6 support to {@link #normalizeIp}.</li>
 *   <li>Normalize URLs more thoroughly (drop fragment, sort query) if needed.</li>
 *   <li>Deduplicate: match {@code normalized} against existing entries per tenant
 *       (do this in the service layer, not in this pure class).</li>
 * </ul>
 */
@Component
public class BlacklistValueValidator {

    private static final Pattern DOMAIN =
            Pattern.compile("^(?=.{1,253}$)([a-z0-9](-?[a-z0-9])*\\.)+[a-z]{2,}$");
    private static final Pattern EMAIL =
            Pattern.compile("^[a-z0-9._%+-]+@([a-z0-9](-?[a-z0-9])*\\.)+[a-z]{2,}$");
    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

    /**
     * Validate and normalize {@code rawValue} for the given {@code type}.
     *
     * @param type     the blacklist entry type
     * @param rawValue the value as received from the client (may be null/blank)
     * @return a {@link ValidationResult} with the canonical form or a reason
     */
    public ValidationResult validate(BlacklistType type, String rawValue) {
        if (rawValue == null) {
            return ValidationResult.invalid("", "empty");
        }
        String v = rawValue.trim();
        if (v.isEmpty()) {
            return ValidationResult.invalid("", "empty");
        }
        return switch (type) {
            case DOMAIN -> normalizeDomain(v);
            case URL -> normalizeUrl(v);
            case EMAIL -> normalizeEmail(v);
            case IP -> normalizeIp(v);
            case PHONE -> normalizePhone(v);
            case HASH -> normalizeHash(v);
        };
    }

    private ValidationResult normalizeDomain(String v) {
        String d = v.toLowerCase(Locale.ROOT);
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        return DOMAIN.matcher(d).matches()
                ? ValidationResult.ok(d)
                : ValidationResult.invalid(v, "bad_domain");
    }

    private ValidationResult normalizeUrl(String v) {
        try {
            java.net.URI uri = java.net.URI.create(v);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return ValidationResult.invalid(v, "bad_url");
            }
            String normalized = scheme.toLowerCase(Locale.ROOT) + "://"
                    + host.toLowerCase(Locale.ROOT)
                    + (uri.getPath() == null ? "" : uri.getPath());
            return ValidationResult.ok(normalized);
        } catch (IllegalArgumentException e) {
            return ValidationResult.invalid(v, "bad_url");
        }
    }

    private ValidationResult normalizeEmail(String v) {
        String e = v.toLowerCase(Locale.ROOT);
        return EMAIL.matcher(e).matches()
                ? ValidationResult.ok(e)
                : ValidationResult.invalid(v, "bad_email");
    }

    private ValidationResult normalizeIp(String v) {
        String[] parts = v.split("\\.");
        if (parts.length != 4) {
            return ValidationResult.invalid(v, "bad_ip");
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return ValidationResult.invalid(v, "bad_ip");
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return ValidationResult.invalid(v, "bad_ip");
            }
            if (octet < 0 || octet > 255) {
                return ValidationResult.invalid(v, "bad_ip");
            }
        }
        return ValidationResult.ok(v);
    }

    private ValidationResult normalizePhone(String v) {
        String digits = v.replaceAll("[\\s-]", "");
        boolean plus = digits.startsWith("+");
        String bare = plus ? digits.substring(1) : digits;
        if (bare.isEmpty() || !bare.chars().allMatch(Character::isDigit)) {
            return ValidationResult.invalid(v, "bad_phone");
        }
        if (bare.length() < 8 || bare.length() > 15) {
            return ValidationResult.invalid(v, "bad_phone");
        }
        return ValidationResult.ok("+" + bare);
    }

    private ValidationResult normalizeHash(String v) {
        String h = v.toLowerCase(Locale.ROOT);
        boolean lengthOk = h.length() == 32 || h.length() == 40 || h.length() == 64;
        return lengthOk && HEX.matcher(h).matches()
                ? ValidationResult.ok(h)
                : ValidationResult.invalid(v, "bad_hash");
    }
}
