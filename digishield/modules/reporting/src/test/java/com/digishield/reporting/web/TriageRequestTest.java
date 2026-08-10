package com.digishield.reporting.web;

import com.digishield.reporting.api.TriageDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The triage request body is a contract with a client that already exists: the
 * SOC inbox has been posting {@code {decision, add_to_blacklist}} while the
 * endpoint read a boolean {@code confirmThreat}, so Jackson left the boolean at
 * its default and every "confirm threat" click dismissed the report instead.
 * These tests pin the shape the browser actually sends.
 */
class TriageRequestTest {

    /**
     * Spring Boot's mapper, not a bare one: Boot disables
     * FAIL_ON_UNKNOWN_PROPERTIES and a bare ObjectMapper does not, so a
     * bare one would fail on add_to_blacklist and prove nothing about the
     * running application.
     */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("parses the exact body the SOC inbox sends, extra field and all")
    void parsesTheBodyTheFrontendSends() throws Exception {
        String body = "{\"decision\":\"confirm_threat\",\"add_to_blacklist\":true}";

        ReportingController.TriageRequest request =
                mapper.readValue(body, ReportingController.TriageRequest.class);

        // add_to_blacklist is not modelled yet; it must be ignored, not rejected,
        // or the button would start failing with 400.
        assertThat(request.toDecision()).isEqualTo(TriageDecision.CONFIRM_THREAT);
    }

    @ParameterizedTest
    @CsvSource({
        "confirm_threat, CONFIRM_THREAT",
        "quarantine,     QUARANTINE",
        "dismiss,        DISMISS",
        "QUARANTINE,     QUARANTINE",
        "  dismiss  ,    DISMISS",
    })
    void acceptsEveryDecisionCaseAndSpacingInsensitively(String wire, TriageDecision expected) {
        assertThat(new ReportingController.TriageRequest(wire, null).toDecision()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "confirm", "CONFIRM_THREATS", "delete", "true"})
    @DisplayName("an unrecognised decision is a 400, never a silent dismissal")
    void rejectsUnknownDecisions(String wire) {
        assertThatThrownBy(() -> new ReportingController.TriageRequest(wire, null).toDecision())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("add_to_blacklist is read from the wire name the browser sends")
    void readsTheBlacklistFlagFromTheWireName() throws Exception {
        String body = "{\"decision\":\"confirm_threat\",\"add_to_blacklist\":true}";

        ReportingController.TriageRequest request =
                mapper.readValue(body, ReportingController.TriageRequest.class);

        assertThat(request.blocksSender()).isTrue();
    }

    @Test
    @DisplayName("a missing or null flag means do not block, never null-pointer")
    void treatsAnAbsentFlagAsFalse() throws Exception {
        ReportingController.TriageRequest absent = mapper.readValue(
                "{\"decision\":\"dismiss\"}", ReportingController.TriageRequest.class);
        ReportingController.TriageRequest explicitNull = mapper.readValue(
                "{\"decision\":\"dismiss\",\"add_to_blacklist\":null}",
                ReportingController.TriageRequest.class);

        assertThat(absent.blocksSender()).isFalse();
        assertThat(explicitNull.blocksSender()).isFalse();
    }

    @Test
    void rejectsAMissingDecision() {
        assertThatThrownBy(() -> new ReportingController.TriageRequest(null, null).toDecision())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
