package com.digishield.shared.tenantcontext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link LogSafe}. */
class LogSafeTest {

    @Test
    void ordinaryValuesArePassedThroughUnchanged() {
        assertThat(LogSafe.value("admin@coquan.gov.vn")).isEqualTo("admin@coquan.gov.vn");
    }

    @Test
    void lineBreaksCannotForgeASecondEntry() {
        String forged = "x@y.vn\n2026-08-05 ERROR nobody did anything wrong";

        String safe = LogSafe.value(forged);

        assertThat(safe).doesNotContain("\n").doesNotContain("\r");
        // Flattened, not dropped: the entry still says which address it was about.
        assertThat(safe).startsWith("x@y.vn_");
    }

    @Test
    void carriageReturnsAndOtherControlCharactersGoToo() {
        assertThat(LogSafe.value("a\rb\tc")).isEqualTo("a_b_c");
    }

    @Test
    void aLongValueIsTruncatedRatherThanFillingTheLog() {
        String safe = LogSafe.value("x".repeat(500));

        assertThat(safe).hasSize(123).endsWith("...");
    }

    @Test
    void nullIsRenderedRatherThanThrowing() {
        assertThat(LogSafe.value(null)).isEqualTo("null");
    }
}
