package com.digishield.simulation.web;

import com.digishield.simulation.api.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public (unauthenticated) tracking endpoint for simulation links.
 * <p>
 * A recipient's simulation email/SMS contains a link to
 * {@code /api/v1/sim/track/{token}}. Following it records a CLICK for that
 * recipient (which drives the adaptive-learning loop: auto-enrolment + risk
 * recompute) and returns a short awareness page revealing it was a drill.
 *
 * <p>Not under {@code SimulationController}'s {@code hasRole('MANAGER')} guard —
 * the clicker is an ordinary employee following a link, often unauthenticated.
 * Whitelisted in the security config; the opaque token is the only secret.
 */
@RestController
public class SimTrackingController {

    private final SimulationService simulationService;

    public SimTrackingController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping(value = "/api/v1/sim/track/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> track(@PathVariable("token") UUID token) {
        boolean resolved = simulationService.trackClick(token);
        return ResponseEntity
                .status(resolved ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .body(resolved ? clickedPage() : invalidPage());
    }

    private String clickedPage() {
        return page(
                "#B91C1C",
                "⚠️ Đây là một bài kiểm tra an toàn thông tin",
                "Bạn vừa bấm vào một liên kết trong email <strong>mô phỏng lừa đảo</strong> của DigiShield. "
                        + "Nếu đây là email thật, thông tin của bạn có thể đã bị đánh cắp."
                        + "<ul style=\"text-align:left;line-height:1.9;margin-top:16px\">"
                        + "<li>Kiểm tra kỹ địa chỉ người gửi và đường link trước khi bấm.</li>"
                        + "<li>Không nhập mật khẩu/OTP từ liên kết trong email.</li>"
                        + "<li>Báo cáo email đáng ngờ cho đội an ninh của bạn.</li>"
                        + "</ul>"
                        + "<p style=\"margin-top:16px\">Một khóa học ôn tập ngắn đã được thêm vào tài khoản của bạn.</p>");
    }

    private String invalidPage() {
        return page(
                "#334155",
                "Liên kết không hợp lệ",
                "Liên kết này đã hết hạn hoặc không tồn tại.");
    }

    private String page(String accent, String title, String body) {
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>DigiShield</title></head>"
                + "<body style=\"margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
                + "background:#F1F5F9;color:#0F172A;display:flex;min-height:100vh;align-items:center;justify-content:center\">"
                + "<div style=\"max-width:520px;margin:24px;padding:32px;background:#fff;border-radius:16px;"
                + "box-shadow:0 10px 30px rgba(2,6,23,.12);text-align:center\">"
                + "<div style=\"font-size:13px;font-weight:700;letter-spacing:.08em;color:" + accent + "\">DIGISHIELD</div>"
                + "<h1 style=\"font-size:22px;margin:12px 0 8px;color:" + accent + "\">" + title + "</h1>"
                + "<div style=\"font-size:14.5px;line-height:1.6;color:#334155\">" + body + "</div>"
                + "</div></body></html>";
    }
}
