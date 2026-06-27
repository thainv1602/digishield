package com.digishield.simulation.api;

import com.digishield.simulation.api.dto.SimCampaignDetailDto;
import com.digishield.simulation.api.dto.SimCampaignDto;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimEvent;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the simulation module.
 */
public interface SimulationService {

    /**
     * Creates a new simulation campaign for the current tenant.
     *
     * @param channel    delivery channel
     * @param templateId content template (may be null)
     * @return the newly created campaign
     */
    SimCampaign createCampaign(Channel channel, UUID templateId);

    /**
     * Records a user interaction event.
     * <p>
     * If {@code action == CLICK}, a {@code UserClickedSimulationEvent}
     * will be emitted.
     *
     * @param campaignId the related campaign
     * @param userId     the user performing the action
     * @param action     the action type
     * @return the recorded event
     */
    SimEvent recordEvent(UUID campaignId, UUID userId, SimAction action);

    /**
     * Lists all simulation campaigns for the current tenant.
     *
     * @return campaign summaries
     */
    List<SimCampaignDto> listCampaigns();

    /**
     * Returns a single campaign with its funnel counts and per-user results.
     *
     * @param campaignId the campaign to load
     * @return the detailed campaign view
     * @throws IllegalArgumentException if the campaign does not exist for this tenant
     */
    SimCampaignDetailDto getCampaign(UUID campaignId);
}
