package com.digishield.simulation.api;

/**
 * The stylesheet of the simulation landing page, exposed so the security layer
 * can name it in the Content-Security-Policy.
 * <p>
 * The page used to style every element with a {@code style=} attribute, which
 * meant the policy had to allow {@code unsafe-inline} across the whole
 * application to keep one page readable — the last warning the ZAP baseline
 * reported. As a single block it can be hashed, so the policy permits exactly
 * this text and nothing else.
 * <p>
 * Lives in the module's public API because the boot application bridges it to
 * {@code shared:security}; the hash is computed there from this text, never
 * written down, so editing a colour cannot leave the two disagreeing.
 */
public final class SimTrackingPage {

    /** Exact stylesheet text, byte for byte as the page emits it. */
    public static final String STYLE_SHEET =
            "body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
            + "background:#F1F5F9;color:#0F172A;display:flex;min-height:100vh;"
            + "align-items:center;justify-content:center}"
            + ".card{max-width:520px;margin:24px;padding:32px;background:#fff;border-radius:16px;"
            + "box-shadow:0 10px 30px rgba(2,6,23,.12);text-align:center}"
            + ".brand{font-size:13px;font-weight:700;letter-spacing:.08em}"
            + ".title{font-size:22px;margin:12px 0 8px}"
            + ".body{font-size:14.5px;line-height:1.6;color:#334155}"
            + ".danger{color:#B91C1C}.muted{color:#334155}";

    private SimTrackingPage() {
    }
}
