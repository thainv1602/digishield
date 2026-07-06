package com.digishield.shared.tenantcontext;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thin helper for resolving backend-produced, user-facing text from the
 * {@code messages[_locale].properties} bundles instead of hardcoded literals.
 *
 * <p>The locale comes from {@link LocaleContextHolder}, which Spring MVC populates
 * from the request {@code Accept-Language} header (the frontend sends its current
 * language); it falls back to Vietnamese (the default bundle) when no locale is
 * resolved. Missing keys degrade to the key itself rather than throwing.
 *
 * <p>Lives in the shared request-context module so every business module can
 * localize its output without depending on another module.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Resolves {@code code} for the current request locale, substituting
     * {@code args} into any {@code {0}} placeholders. Returns {@code code} if the
     * key is not found.
     */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }
}
