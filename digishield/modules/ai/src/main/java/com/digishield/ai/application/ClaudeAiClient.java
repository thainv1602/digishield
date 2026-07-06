package com.digishield.ai.application;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real {@link AiClient} backed by Anthropic Claude. Enabled only when
 * {@code digishield.ai.claude.enabled=true} (and {@code ANTHROPIC_API_KEY} is
 * set); otherwise the deterministic {@code StubAiClient} default is used. The
 * front {@code @Primary} bean is selected in {@link AiClientConfig} (ML → Claude
 * → Stub); this class is also the escalation target for {@link MlAiClient}.
 * <p>
 * Each method asks the model for a strict JSON object and parses it — cheap
 * Haiku for classify/moderate, Sonnet for the creative template generation.
 */
@Component
@ConditionalOnProperty(name = "digishield.ai.claude.enabled", havingValue = "true")
public class ClaudeAiClient implements AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeAiClient.class);

    private static final String CLASSIFY_MODEL = "claude-haiku-4-5";
    private static final String MODERATE_MODEL = "claude-haiku-4-5";
    private static final String TEMPLATE_MODEL = "claude-sonnet-4-6";

    private final AnthropicClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeAiClient() {
        // Reads ANTHROPIC_API_KEY from the environment.
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public ClassificationView classify(String payload) {
        String prompt = """
                You classify a reported email/SMS for a phishing-awareness platform.
                Classify the content as exactly one of: clean, spam, threat.
                Respond with ONLY a JSON object, no prose:
                {"label":"clean|spam|threat","confidence":0.0-1.0,"reason":"<short Vietnamese reason>"}

                Content:
                """ + safe(payload);
        JsonNode json = callJson(CLASSIFY_MODEL, prompt, 512);
        String label = json.path("label").asText("clean");
        double confidence = clamp01(json.path("confidence").asDouble(0.5));
        String reason = json.path("reason").asText("");
        return new ClassificationView(label, confidence, reason);
    }

    @Override
    public ModerationView moderate(String content) {
        String prompt = """
                You moderate AI-generated phishing-simulation content for safety.
                Decide a verdict: pass (safe), flag (questionable), or block (unsafe).
                Respond with ONLY a JSON object, no prose:
                {"verdict":"pass|flag|block","reasons":["<short Vietnamese reason>", ...]}

                Content:
                """ + safe(content);
        JsonNode json = callJson(MODERATE_MODEL, prompt, 512);
        String verdict = json.path("verdict").asText("flag");
        List<String> reasons = new ArrayList<>();
        json.path("reasons").forEach(n -> reasons.add(n.asText()));
        return new ModerationView(verdict, List.copyOf(reasons));
    }

    @Override
    public GeneratedTemplate generate(TemplateChannel channel, String industry, String season) {
        String safeIndustry = (industry == null || industry.isBlank()) ? "doanh nghiệp" : industry.trim();
        String safeSeason = (season == null || season.isBlank()) ? null : season.trim();
        String prompt = """
                Generate a realistic phishing-simulation message in Vietnamese for a
                security-awareness training campaign (this is for defensive training only).
                Channel: %s. Industry: %s. Season: %s.
                Include both a subject/hook line and the full message body appropriate to the
                channel (short one-liner with a link for sms/zalo; a short formal message for email).
                Respond with ONLY a JSON object, no prose:
                {"subject":"<subject line>","body":"<message body>","difficulty":"easy|medium|hard"}
                """.formatted(channel.name().toLowerCase(Locale.ROOT), safeIndustry,
                safeSeason == null ? "n/a" : safeSeason);
        JsonNode json = callJson(TEMPLATE_MODEL, prompt, 1024);
        String subject = json.path("subject").asText("[" + safeIndustry + "] Cảnh báo bảo mật");
        String body = json.path("body").asText("");
        Difficulty difficulty = parseDifficulty(json.path("difficulty").asText("medium"));
        return new GeneratedTemplate(subject, buildBodyRef(channel, safeIndustry, safeSeason), body, difficulty);
    }

    private JsonNode callJson(String model, String prompt, long maxTokens) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessage(prompt)
                .build();
        Message response = client.messages().create(params);
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("");
        try {
            return mapper.readTree(extractJson(text));
        } catch (Exception e) {
            LOG.error("Failed to parse Claude JSON response (model={}): {}", model, e.getMessage());
            throw new IllegalStateException("Invalid AI response", e);
        }
    }

    /** Extracts the outermost JSON object from the model text. */
    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : "{}";
    }

    private static Difficulty parseDifficulty(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "easy" -> Difficulty.EASY;
            case "hard" -> Difficulty.HARD;
            default -> Difficulty.MEDIUM;
        };
    }

    private static String buildBodyRef(TemplateChannel channel, String industry, String season) {
        String slug = (industry + (season == null ? "" : "-" + season))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "tmpl/" + channel.name().toLowerCase(Locale.ROOT) + "/" + slug;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
