package com.digishield.ai.application;

import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link AiClient}: deterministic, dependency-free stub logic so the
 * frontend has predictable demo data without calling (or paying for) a real
 * model. Replaced by {@code ClaudeAiClient} (registered {@code @Primary}) when
 * {@code digishield.ai.claude.enabled=true}.
 */
@Component
public class StubAiClient implements AiClient {

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

    @Override
    public ClassificationView classify(String payload) {
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
    public ModerationView moderate(String content) {
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
    public GeneratedTemplate generate(TemplateChannel channel, String industry, String season) {
        String safeIndustry = (industry == null || industry.isBlank()) ? "doanh nghiệp" : industry.trim();
        String safeSeason = (season == null || season.isBlank()) ? null : season.trim();
        String subject = buildSubject(channel, safeIndustry, safeSeason);
        return new GeneratedTemplate(
                subject,
                buildBodyRef(channel, safeIndustry, safeSeason),
                buildBody(channel, safeIndustry, subject),
                pickDifficulty(channel, safeIndustry));
    }

    /**
     * Deterministic, realistic-looking Vietnamese body for the given channel/industry.
     * SMS/Zalo get a short one-liner with a link; email gets a short formal message.
     * This is training content only (defensive security awareness).
     */
    private String buildBody(TemplateChannel channel, String industry, String subject) {
        return switch (channel) {
            case SMS, ZALO -> "[" + industry + "] Tài khoản của bạn cần xác minh trong 24h để tránh bị khóa. "
                    + "Nhấp: https://xac-minh-" + slug(industry) + ".example.vn";
            case VOICE -> "Kịch bản gọi: Xin chào, đây là bộ phận " + industry
                    + ". Chúng tôi phát hiện giao dịch bất thường trên tài khoản của quý khách và cần xác minh danh tính ngay.";
            case QR -> "Quét mã QR để nhận thông báo từ " + industry
                    + ". Mã dẫn tới trang đăng nhập giả mạo yêu cầu nhập thông tin tài khoản.";
            case USB -> "Tập tin đính kèm: \"Tài liệu mật - " + industry + ".docx\" (macro độc hại mô phỏng).";
            default -> "Kính gửi Quý khách,\n\n"
                    + "Hệ thống " + industry + " ghi nhận một yêu cầu cần xác minh liên quan tới " + subject.toLowerCase(Locale.ROOT) + ". "
                    + "Vui lòng truy cập liên kết bên dưới và đăng nhập để hoàn tất xác minh trong vòng 24 giờ, "
                    + "nếu không tài khoản có thể bị tạm khóa.\n\n"
                    + "Xác minh ngay: https://xac-minh-" + slug(industry) + ".example.vn\n\n"
                    + "Trân trọng,\nBộ phận An toàn " + industry;
        };
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
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
        String slug = (industry + (season == null ? "" : "-" + season))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "tmpl/" + channel.name().toLowerCase(Locale.ROOT) + "/" + slug;
    }

    private Difficulty pickDifficulty(TemplateChannel channel, String industry) {
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
