package com.digishield;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generates real QR codes as scalable SVG. Public: it only encodes the text the
 * caller supplies (an otpauth URL, a certificate verify link, a simulation
 * tracking link) into a picture — it fetches nothing and exposes no data of its
 * own. Replaces the placeholder "QR" boxes that used to stand in for real codes.
 */
@RestController
public class QrController {

    private static final int MAX_LEN = 1024;
    private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

    /**
     * Renders {@code data} as a QR-code SVG.
     *
     * @param data    the text to encode (e.g. a URL); required, max 1024 chars
     * @param size    the SVG pixel size (square), clamped to 64..1024 (default 220)
     * @param quiet   quiet-zone width in modules (clamped 0..8, default 2)
     */
    @GetMapping(value = "/api/v1/qr", produces = "image/svg+xml")
    public ResponseEntity<String> qr(@RequestParam("data") String data,
                                     @RequestParam(value = "size", defaultValue = "220") int size,
                                     @RequestParam(value = "quiet", defaultValue = "2") int quiet) {
        if (data == null || data.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "data is required");
        }
        if (data.length() > MAX_LEN) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "data too long");
        }
        int px = Math.max(64, Math.min(1024, size));
        int quietZone = Math.max(0, Math.min(8, quiet));

        String svg = toSvg(encode(data), quietZone, px);
        return ResponseEntity.ok()
                .contentType(SVG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(svg);
    }

    private ByteMatrix encode(String data) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            QRCode code = Encoder.encode(data, ErrorCorrectionLevel.M, hints);
            return code.getMatrix();
        } catch (WriterException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "cannot encode data", e);
        }
    }

    /** Renders the module matrix as an SVG (one rect per dark module + quiet zone). */
    private static String toSvg(ByteMatrix matrix, int quiet, int px) {
        int n = matrix.getWidth();
        int total = n + quiet * 2;
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(px)
                .append("\" height=\"").append(px)
                .append("\" viewBox=\"0 0 ").append(total).append(' ').append(total)
                .append("\" shape-rendering=\"crispEdges\" role=\"img\">");
        sb.append("<rect width=\"").append(total).append("\" height=\"").append(total)
                .append("\" fill=\"#ffffff\"/>");
        sb.append("<path fill=\"#000000\" d=\"");
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (matrix.get(x, y) == 1) {
                    sb.append('M').append(x + quiet).append(' ').append(y + quiet).append("h1v1h-1z");
                }
            }
        }
        sb.append("\"/></svg>");
        return sb.toString();
    }
}
