package com.digishield.shared.tenantcontext;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is making the current request, named the way a person reading an audit log
 * would recognise them.
 *
 * <p>{@code authentication.getName()} on a Cognito JWT returns the {@code sub}
 * claim — a UUID. An audit trail exists to answer "who did this", and a UUID
 * answers it with another question, so the claims a human recognises come first
 * and the opaque identifier is only the fallback.
 *
 * <p>Cognito access tokens do not carry {@code email} unless the pre-token
 * trigger adds it, hence the several candidates.
 */
public final class CurrentActor {

    private static final String[] PREFERRED_CLAIMS = {
        "email", "cognito:username", "username", "preferred_username"
    };

    private CurrentActor() {
    }

    /** Best available identity for the caller, or {@code null} when unauthenticated. */
    public static String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            for (String claim : PREFERRED_CLAIMS) {
                String value = jwt.getClaimAsString(claim);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return authentication.getName();
    }
}
