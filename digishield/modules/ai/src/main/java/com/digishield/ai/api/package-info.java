/**
 * Public API of the AI module (e.g. {@code AiService}, including the template
 * library). Exposed as a Spring Modulith named interface so the application
 * shell can bridge it to other modules' SPIs — {@code AiCampaignTemplates}
 * feeds the simulation module's {@code CampaignTemplateProvider}.
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.ai.api;
