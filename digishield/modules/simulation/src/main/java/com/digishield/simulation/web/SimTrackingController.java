package com.digishield.simulation.web;

import com.digishield.simulation.api.SimTrackingPage;
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
                "danger",
                "⚠️ Đây là một bài kiểm tra an toàn thông tin",
                "Bạn vừa bấm vào một liên kết trong email <strong>mô phỏng lừa đảo</strong> của DigiShield. "
                        + "Nếu đây là email thật, thông tin của bạn có thể đã bị đánh cắp.");
    }

    private String invalidPage() {
        return page(
                "muted",
                "Liên kết không hợp lệ",
                "Liên kết này đã hết hạn hoặc không tồn tại.");
    }

    private String page(String accent, String title, String body) {
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>DigiShield</title><style>" + SimTrackingPage.STYLE_SHEET + "</style></head>"
                + "<body><div class=\"card\">"
                + "<div class=\"brand " + accent + "\">DIGISHIELD</div>"
                + "<h1 class=\"title " + accent + "\">" + title + "</h1>"
                + "<div class=\"body\">" + body + "</div>"
                + "</div></body></html>";
    }
}
