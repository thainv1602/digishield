package com.digishield.ai.application;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Default implementation of {@link AiService}.
 * <p>
 * All "AI" behaviour here is deterministic, dependency-free stub logic so the
 * frontend has predictable demo data. The real LLM / model integration is
 * marked TODO in each method and intentionally omitted (no SDK dependency).
 */
@Service
@Transactional
public class AiServiceImpl implements AiService {

    private static final Logger LOG = LoggerFactory.getLogger(AiServiceImpl.class);

    /** Obvious unsafe words that cause moderation to flag/block content. */
    private static final List<String> UNSAFE_WORDS = List.of(
            "malware", "ransomware", "exploit", "credential dump", "ddos",
            "child", "suicide", "bomb", "weapon", "kill");

    /** High-risk phishing signals used by the classifier stub. */
    private static final List<String> THREAT_SIGNALS = List.of(
            "password", "verify your account", "wire transfer", "bitcoin",
            "gift card", "urgent", "suspended", "click here", "otp", "login");

    /** Promotional / bulk signals used by the classifier stub. */
    private static final List<String> SPAM_SIGNALS = List.of(
            "sale", "discount", "free", "winner", "unsubscribe", "promotion", "%");

    private final AiTemplateRepository templateRepository;

    public AiServiceImpl(AiTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public SimTemplateView generateTemplate(TemplateChannel channel, String industry, String season) {
        // TODO: call the LLM (AI provider) to generate channel-specific content
        //       from the industry/season prompt. The logic below is a deterministic stub.
        UUID tenantId = TenantContext.requireUuid();

        String safeIndustry = (industry == null || industry.isBlank()) ? "doanh nghiệp" : industry.trim();
        String safeSeason = (season == null || season.isBlank()) ? null : season.trim();

        String subject = buildSubject(channel, safeIndustry, safeSeason);
        String bodyRef = buildBodyRef(channel, safeIndustry, safeSeason);
        Difficulty difficulty = pickDifficulty(channel, safeIndustry);

        AiTemplate template = new AiTemplate(
                UUID.randomUUID(), tenantId, channel, subject, bodyRef,
                difficulty, TemplateStatus.DRAFT);
        AiTemplate saved = templateRepository.save(template);
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassificationView classify(String payload) {
        // TODO: call the real classification model. Deterministic keyword stub below.
        String text = payload == null ? "" : payload.toLowerCase(Locale.ROOT);

        long threatHits = countHits(text, THREAT_SIGNALS);
        long spamHits = countHits(text, SPAM_SIGNALS);

        if (threatHits > 0) {
            double confidence = clamp(0.6 + 0.1 * threatHits);
            return new ClassificationView("threat", confidence,
                    "Phát hiện " + threatHits + " dấu hiệu lừa đảo (ví dụ: yêu cầu thông tin đăng nhập, tính khẩn cấp).");
        }
        if (spamHits > 0) {
            double confidence = clamp(0.5 + 0.1 * spamHits);
            return new ClassificationView("spam", confidence,
                    "Phát hiện " + spamHits + " dấu hiệu quảng cáo/rác (khuyến mãi, đường dẫn hủy đăng ký).");
        }
        return new ClassificationView("clean", 0.92,
                "Không phát hiện dấu hiệu lừa đảo hay quảng cáo trong nội dung.");
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationView moderate(String content) {
        // TODO: call the content-safety moderation service. Rule-based stub below.
        String text = content == null ? "" : content.toLowerCase(Locale.ROOT);

        List<String> reasons = new ArrayList<>();
        for (String word : UNSAFE_WORDS) {
            if (text.contains(word)) {
                reasons.add("Nội dung chứa từ ngữ không an toàn: \"" + word + "\".");
            }
        }

        String verdict;
        if (reasons.isEmpty()) {
            verdict = "pass";
        } else if (reasons.size() >= 2) {
            verdict = "block";
        } else {
            verdict = "flag";
        }
        return new ModerationView(verdict, List.copyOf(reasons));
    }

    @Override
    public void runOrchestration(String scope, UUID scopeId) {
        // TODO: trigger the AIDA pipeline (recompute risk scores -> auto-enroll
        //       at-risk users into remediation). In dev this is a logged no-op.
        UUID tenantId = TenantContext.requireUuid();
        String safeScope = (scope == null || scope.isBlank()) ? "org" : scope.trim();
        LOG.info("AIDA orchestration requested for tenant={} scope={} scopeId={} (dev no-op)",
                tenantId, safeScope, scopeId);
    }

    private SimTemplateView toView(AiTemplate t) {
        return new SimTemplateView(
                t.getId(),
                t.getChannel().name().toLowerCase(Locale.ROOT),
                t.getSubject(),
                t.getBodyRef(),
                t.getDifficulty().name().toLowerCase(Locale.ROOT),
                t.getStatus().name().toLowerCase(Locale.ROOT));
    }

    private String buildSubject(TemplateChannel channel, String industry, String season) {
        String seasonal = season == null ? "" : " mùa " + season;
        return switch (channel) {
            case SMS, ZALO -> "Thông báo" + seasonal + " từ bộ phận " + industry;
            case VOICE -> "Cuộc gọi xác minh tài khoản " + industry;
            case QR -> "Quét mã nhận ưu đãi" + seasonal + " ngành " + industry;
            case USB -> "Tài liệu mật ngành " + industry;
            default -> "[" + industry + "] Cảnh báo bảo mật tài khoản" + seasonal;
        };
    }

    private String buildBodyRef(TemplateChannel channel, String industry, String season) {
        // A stable, human-readable reference key the renderer can resolve later.
        String slug = (industry + (season == null ? "" : "-" + season))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "tmpl/" + channel.name().toLowerCase(Locale.ROOT) + "/" + slug;
    }

    private Difficulty pickDifficulty(TemplateChannel channel, String industry) {
        // Deterministic spread so demo data is varied but reproducible.
        int hash = Math.abs((channel.name() + industry).hashCode()) % 3;
        return switch (hash) {
            case 0 -> Difficulty.EASY;
            case 1 -> Difficulty.MEDIUM;
            default -> Difficulty.HARD;
        };
    }

    private long countHits(String text, List<String> signals) {
        return signals.stream().filter(text::contains).count();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
