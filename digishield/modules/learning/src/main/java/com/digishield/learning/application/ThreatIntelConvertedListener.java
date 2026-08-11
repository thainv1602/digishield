package com.digishield.learning.application;

import com.digishield.contracts.events.ThreatIntelConvertedEvent;
import com.digishield.learning.api.CoachingPageView;
import com.digishield.learning.api.LearningService;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Creates the actual coaching page when a threat-intel record is "flipped" into
 * training content by the SOC. The reporting module mints the coaching-page id
 * and emits {@link ThreatIntelConvertedEvent}; this creates the page with that
 * id so the id it returned to the caller resolves to real content.
 *
 * <p>Runs asynchronously in its own transaction, so it sets the tenant on the
 * current thread before writing.
 */
@Component
class ThreatIntelConvertedListener {

    private static final Logger LOG = LoggerFactory.getLogger(ThreatIntelConvertedListener.class);

    private final LearningService learningService;

    ThreatIntelConvertedListener(LearningService learningService) {
        this.learningService = learningService;
    }

    @ApplicationModuleListener
    void on(ThreatIntelConvertedEvent event) {
        String previousTenant = TenantContext.get();
        TenantContext.set(event.tenantId().toString());
        try {
            learningService.createCoachingPage(
                    event.tenantId(),
                    new CoachingPageView(event.coachingPageId(), event.templateId(), event.contentRef(), null));
        } catch (Exception e) {
            LOG.warn("Creating coaching page {} from threat intel failed: {}",
                    event.coachingPageId(), e.toString());
        } finally {
            TenantContext.restore(previousTenant);
        }
    }
}
