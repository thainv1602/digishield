package com.digishield.shared.tenantcontext;

/**
 * Renders a caller-supplied value safe to put in a log line.
 *
 * <p>A log file is parsed by people and by aggregators, and both read a newline
 * as "next entry". A value that arrives in a request body and is logged verbatim
 * can therefore write entries of its own — an email of
 * {@code "x@y.z\n2026-08-05 ERROR nobody did anything wrong"} forges one. The
 * damage is not to the running system but to the record of it, which is exactly
 * what an investigation later depends on.
 *
 * <p>Values are flattened rather than rejected: the point of logging the address
 * is to say which one, so a mangled address still answers that, while a dropped
 * field answers nothing.
 */
public final class LogSafe {

    /** Bounds what one field can push into the logs. */
    private static final int MAX_LENGTH = 120;

    private LogSafe() {
    }

    /**
     * Returns {@code raw} with line breaks and other control characters replaced
     * by {@code _}, truncated to a sane length.
     *
     * @param raw the untrusted value; {@code null} becomes the literal "null"
     */
    public static String value(String raw) {
        if (raw == null) {
            return "null";
        }
        String flattened = raw.replaceAll("[\\r\\n]", "_").replaceAll("\\p{Cntrl}", "_");
        if (flattened.length() <= MAX_LENGTH) {
            return flattened;
        }
        return flattened.substring(0, MAX_LENGTH) + "...";
    }
}
