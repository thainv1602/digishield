/**
 * Public API of the simulation module, including the
 * {@code CampaignTemplateProvider} SPI. Exposed as a Spring Modulith named
 * interface so the application shell can supply a concrete implementation
 * (bridged to the AI module's template library).
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.simulation.api;
