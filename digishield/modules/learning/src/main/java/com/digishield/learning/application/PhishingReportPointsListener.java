package com.digishield.learning.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.learning.domain.PointAction;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Rewards someone whose phishing report triage confirmed as genuine.
 * <p>
 * This is the behaviour the whole product is trying to produce, and it is worth
 * ten times what clicking a simulation costs. Only confirmed reports count —
 * paying for every report would reward forwarding everything, which is the
 * opposite of the judgement being taught.
 */
@Component
class PhishingReportPointsListener {

    private final PointsAwarder pointsAwarder;

    PhishingReportPointsListener(PointsAwarder pointsAwarder) {
        this.pointsAwarder = pointsAwarder;
    }

    @ApplicationModuleListener
    void on(PhishingReportConfirmedEvent event) {
        pointsAwarder.award(event.tenantId(), event.userId(), PointAction.REPORT_CONFIRMED);
    }
}
