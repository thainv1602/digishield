package com.digishield;

import com.digishield.shared.security.CspStyleSource;
import com.digishield.simulation.api.SimTrackingPage;
import org.springframework.stereotype.Component;

/**
 * Tells the security layer about the one stylesheet the application serves, so
 * the Content-Security-Policy can name it by hash instead of allowing inline
 * styles everywhere. Bridged here because this is the only place that may see
 * both the simulation module and {@code shared:security}.
 */
@Component
class SimTrackingPageStyles implements CspStyleSource {

    @Override
    public String stylesheet() {
        return SimTrackingPage.STYLE_SHEET;
    }
}
