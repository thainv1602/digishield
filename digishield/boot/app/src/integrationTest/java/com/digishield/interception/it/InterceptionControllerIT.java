package com.digishield.interception.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digishield.interception.api.InterceptionService;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.web.InterceptionController;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test for {@link InterceptionController}.
 *
 * <p>Only the web layer is loaded; {@link InterceptionService} is mocked so the
 * test focuses purely on request/response wiring and JSON (de)serialisation.
 * Security filters are disabled with {@code addFilters = false} so the mocked
 * controller is reachable without a configured OAuth2 JWT decoder (the real
 * SecurityConfig in shared:security would otherwise require an IdP).
 */
@WebMvcTest(InterceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterceptionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterceptionService interceptionService;

    /**
     * POST /api/v1/interventions/evaluate returns HTTP 200 and serialises the
     * PAUSE decision (with its signals and educational message) into the body.
     */
    @Test
    void evaluateReturnsPauseDecision() throws Exception {
        InterventionDecision pause = new InterventionDecision(
                "pause",
                List.of("ON_CALL", "NEW_PAYEE", "WATCHLIST_HIT"),
                "Giao dịch đang được tạm dừng để bảo vệ bạn.");

        given(interceptionService.evaluate(any(EvaluateRequest.class))).willReturn(pause);

        EvaluateRequest request = new EvaluateRequest(
                UUID.randomUUID(),
                new BigDecimal("50000000"),
                "9999-fraud-account",
                true,
                true);

        mockMvc.perform(post("/api/v1/interventions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("pause"))
                .andExpect(jsonPath("$.signals.length()").value(3))
                .andExpect(jsonPath("$.signals[0]").value("ON_CALL"))
                .andExpect(jsonPath("$.signals[2]").value("WATCHLIST_HIT"))
                .andExpect(jsonPath("$.message").value("Giao dịch đang được tạm dừng để bảo vệ bạn."));
    }
}
