package com.digishield;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The QR code a quishing campaign puts on a poster.
 *
 * <p>It is the one lure nobody can inspect before acting on it: a phone camera
 * either resolves the matrix or it does not. So the checks here are the ones a
 * scanner cares about — a quiet zone around the pattern, a viewBox that matches
 * the module count, and a refusal rather than a broken image when the payload
 * cannot be encoded.
 */
class QrControllerTest {

    private final QrController controller = new QrController();

    private String svg(String data, int size, int quiet) {
        ResponseEntity<String> response = controller.qr(data, size, quiet);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("image/svg+xml");
        return response.getBody();
    }

    @Test
    @DisplayName("the response is an SVG carrying the encoded matrix")
    void rendersAnSvg() {
        String body = svg("https://digishield.vn/t/abc", 220, 2);

        assertThat(body).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        assertThat(body).endsWith("</svg>");
        // The dark modules are drawn as one path; an empty one would be a blank code.
        assertThat(body).contains("<path fill=\"#000000\"").contains("h1v1h-1z");
    }

    @Test
    @DisplayName("the quiet zone widens the viewBox on both sides")
    void theQuietZoneSurroundsThePattern() {
        String withoutQuiet = svg("https://digishield.vn/t/abc", 220, 0);
        String withQuiet = svg("https://digishield.vn/t/abc", 220, 4);

        int plain = viewBoxSize(withoutQuiet);
        int quieted = viewBoxSize(withQuiet);
        // Four modules of margin on each side: a scanner needs the border to
        // find the pattern at all.
        assertThat(quieted).isEqualTo(plain + 8);
    }

    private static int viewBoxSize(String svg) {
        String marker = "viewBox=\"0 0 ";
        String rest = svg.substring(svg.indexOf(marker) + marker.length());
        return Integer.parseInt(rest.substring(0, rest.indexOf(' ')));
    }

    @Test
    @DisplayName("the rendered size is clamped, so a poster request cannot ask for a 1px code")
    void sizeIsClamped() {
        assertThat(svg("abc", 1, 2)).contains("width=\"64\"");
        assertThat(svg("abc", 99999, 2)).contains("width=\"1024\"");
        assertThat(svg("abc", 300, 2)).contains("width=\"300\"");
    }

    @Test
    @DisplayName("the quiet zone is clamped too, and a negative one is treated as none")
    void quietZoneIsClamped() {
        int none = viewBoxSize(svg("abc", 220, -5));
        int capped = viewBoxSize(svg("abc", 220, 999));
        int eight = viewBoxSize(svg("abc", 220, 8));

        assertThat(capped).isEqualTo(eight);
        assertThat(none).isLessThan(eight);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("an empty payload is refused rather than rendered as an empty code")
    void blankDataIsRejected(String data) {
        assertThatThrownBy(() -> controller.qr(data, 220, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void nullDataIsRejected() {
        assertThatThrownBy(() -> controller.qr(null, 220, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("an oversized payload is refused before the encoder is asked")
    void tooMuchDataIsRejected() {
        String tooLong = "x".repeat(1025);

        assertThatThrownBy(() -> controller.qr(tooLong, 220, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("a payload at the limit still encodes")
    void theLimitItselfIsAllowed() {
        // 1024 is the documented maximum, so it must not be off by one.
        assertThat(svg("x".repeat(1024), 220, 2)).contains("<path");
    }

    @Test
    @DisplayName("the image is cacheable: a poster's code never changes")
    void theResponseIsCacheable() {
        ResponseEntity<String> response = controller.qr("https://digishield.vn/t/abc", 220, 2);

        assertThat(response.getHeaders().getCacheControl()).contains("max-age=86400");
    }
}
