package com.digishield;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.simulation.api.CampaignTemplateProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the simulation module's {@link CampaignTemplateProvider} SPI to the AI
 * module's template library, so a launched campaign delivers the lure authored in
 * Content Studio instead of a generic notice. Lives in the boot app to keep
 * simulation decoupled from AI (mirrors {@link ReportingRecentReports}).
 */
@Component
class AiCampaignTemplates implements CampaignTemplateProvider {

    private final AiService aiService;

    AiCampaignTemplates(AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public Optional<TemplateContent> findById(UUID templateId) {
        return aiService.findTemplate(templateId).map(AiCampaignTemplates::toContent);
    }

    private static TemplateContent toContent(SimTemplateView t) {
        return new TemplateContent(t.subject(), t.body(), t.bodyFormat());
    }
}
