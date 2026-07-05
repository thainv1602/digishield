package com.digishield.ai.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the {@code @Primary} {@link AiClient} injected into {@link AiServiceImpl}.
 * Precedence: {@link MlAiClient} (self-hosted models, front-line) → {@link ClaudeAiClient}
 * (when {@code digishield.ai.claude.enabled=true}) → {@link StubAiClient} (default).
 * <p>
 * MlAiClient itself falls back to Claude/Stub per call, so enabling ML keeps Claude
 * as the escalation path. Centralizing the choice here avoids competing {@code @Primary}
 * beans when both ML and Claude are enabled.
 */
@Configuration
public class AiClientConfig {

    @Bean
    @Primary
    public AiClient primaryAiClient(ObjectProvider<MlAiClient> ml,
                                    ObjectProvider<ClaudeAiClient> claude,
                                    StubAiClient stub) {
        MlAiClient mlClient = ml.getIfAvailable();
        if (mlClient != null) {
            return mlClient;
        }
        ClaudeAiClient claudeClient = claude.getIfAvailable();
        if (claudeClient != null) {
            return claudeClient;
        }
        return stub;
    }
}
