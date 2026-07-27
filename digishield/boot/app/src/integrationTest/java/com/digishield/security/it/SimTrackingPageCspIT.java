package com.digishield.security.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the Content-Security-Policy names the stylesheet the landing page
 * actually sends.
 *
 * <p>The page used to style every element with a {@code style=} attribute,
 * which forced {@code unsafe-inline} into the policy for the whole application
 * — the last warning the ZAP baseline reported after #156. It now emits one
 * {@code <style>} block, and the policy names that block by hash.
 *
 * <p>The hash below is recomputed from the bytes that were served, not read
 * from the constant the policy was built from. A hash compared only against
 * itself would prove nothing: a browser checks it against what arrives, and the
 * way a mismatch shows up in production is an unstyled page that nobody
 * notices.
 *
 * <p>Runs on the dev profile because the landing page has to be reachable to be
 * inspected, and that is also the profile the weekly scan boots — so this is
 * the configuration whose warning is being answered. H2 rather than a
 * container, so no Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SimTrackingPageCspIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thePolicyNamesTheHashOfTheStyleSheetThePageSends() throws Exception {
        var response = mockMvc.perform(get("/api/v1/sim/track/{token}", UUID.randomUUID()))
                .andReturn().getResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = response.getContentAsString();

        assertThat(body).contains("<style>");
        String emitted = body.substring(body.indexOf("<style>") + "<style>".length(),
                body.indexOf("</style>"));

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(emitted.getBytes(StandardCharsets.UTF_8));
        String expected = "'sha256-" + Base64.getEncoder().encodeToString(digest) + "'";

        assertThat(response.getHeader("Content-Security-Policy")).contains(expected);
    }

    @Test
    void theRenderedPageUsesNoInlineStyleAttributes() throws Exception {
        var response = mockMvc.perform(get("/api/v1/sim/track/{token}", UUID.randomUUID()))
                .andReturn().getResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // One attribute left behind would put the page back to needing
        // unsafe-inline, and the hash would still match — so the policy would
        // look strict while the page quietly depended on it not being.
        assertThat(response.getContentAsString()).doesNotContain("style=\"");
    }

    @Test
    void thePolicyNoLongerAllowsInlineStyles() throws Exception {
        var response = mockMvc.perform(get("/api/v1/sim/track/{token}", UUID.randomUUID()))
                .andReturn().getResponse();

        assertThat(response.getHeader("Content-Security-Policy")).doesNotContain("unsafe-inline");
    }
}
