package com.digishield.ai.application;

import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.domain.TemplateChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * {@link AiClient} that routes <em>classification</em> to the self-hosted phishing
 * AI microservice (research/service/phishing-ai) — the fine-tuned URL/SMS/email
 * models — and falls back to a delegate for anything the ML service can't answer.
 * <p>
 * Active only when {@code digishield.ai.ml.enabled=true}. On any of: HTTP error,
 * timeout, the service reporting {@code escalate=true} (model not trained / low
 * confidence), or confidence below {@code digishield.ai.ml.min-confidence}, the
 * call falls back to {@link ClaudeAiClient} when available, otherwise
 * {@link StubAiClient}. {@code moderate}/{@code generate} always delegate — the ML
 * service only classifies. The front bean is chosen in {@link AiClientConfig}.
 */
@Component
@ConditionalOnProperty(name = "digishield.ai.ml.enabled", havingValue = "true")
public class MlAiClient implements AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(MlAiClient.class);

    private final AiClient fallback;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final double minConfidence;
    private final Duration timeout;

    public MlAiClient(
            @Value("${digishield.ai.ml.base-url:http://localhost:8085}") String baseUrl,
            @Value("${digishield.ai.ml.min-confidence:0.6}") double minConfidence,
            @Value("${digishield.ai.ml.timeout-ms:2000}") long timeoutMs,
            ObjectProvider<ClaudeAiClient> claude,
            StubAiClient stub) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.minConfidence = minConfidence;
        this.timeout = Duration.ofMillis(timeoutMs);
        ClaudeAiClient claudeClient = claude.getIfAvailable();
        this.fallback = (claudeClient != null) ? claudeClient : stub;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1000)).build();
        LOG.info("MlAiClient active: base-url={}, fallback={}",
                this.baseUrl, this.fallback.getClass().getSimpleName());
    }

    @Override
    public ClassificationView classify(String payload) {
        try {
            String body = mapper.writeValueAsString(Map.of("payload", payload == null ? "" : payload));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/classify"))
                    .timeout(timeout)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOG.warn("ML service HTTP {} — falling back to {}",
                        response.statusCode(), fallback.getClass().getSimpleName());
                return fallback.classify(payload);
            }
            JsonNode json = mapper.readTree(response.body());
            boolean escalate = json.path("escalate").asBoolean(false);
            String label = json.path("label").isMissingNode() ? null : json.path("label").asText(null);
            double confidence = json.path("confidence").asDouble(0.0);
            if (escalate || label == null || confidence < minConfidence) {
                return fallback.classify(payload);  // model missing / low confidence → Claude/Stub
            }
            return new ClassificationView(label, confidence, json.path("reason").asText(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("ML classify interrupted — falling back to {}", fallback.getClass().getSimpleName());
            return fallback.classify(payload);
        } catch (Exception e) {
            LOG.warn("ML classify failed ({}) — falling back to {}",
                    e.getMessage(), fallback.getClass().getSimpleName());
            return fallback.classify(payload);
        }
    }

    @Override
    public ModerationView moderate(String content) {
        return fallback.moderate(content);  // ML service only classifies
    }

    @Override
    public GeneratedTemplate generate(TemplateChannel channel, String industry, String season) {
        return fallback.generate(channel, industry, season);  // ML service only classifies
    }
}
