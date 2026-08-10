package com.digishield.simulation.api;

import com.digishield.simulation.api.dto.SendResultDto;
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
     * @param groupId    target audience group (may be null)
     * @return the newly created campaign
     */
    SimCampaignDto createCampaign(Channel channel, UUID templateId, UUID groupId);

    /**
     * Launches ("sends") a campaign to the given recipients: creates a tracking
     * row + a DELIVERED event per user and moves the campaign to RUNNING. Real
     * email delivery isn't wired, so the returned tracking links let the flow be
     * exercised (following a link records a CLICK).
     *
     * @param campaignId  the campaign to launch
     * @param userIds     the recipient user ids (resolved from the target group)
     * @param linkBaseUrl absolute base URL for building tracking links (e.g. request origin)
     * @return the launch result incl. per-recipient tracking links
     */
    SendResultDto sendCampaign(UUID campaignId, List<UUID> userIds, String linkBaseUrl);

    /**
     * Resolves a tracking token to its recipient and records a CLICK (idempotent
     * — a second follow of the same link does not re-fire the event). Used by the
     * public tracking endpoint, which has no tenant context; implementations must
     * resolve the token without RLS and set the tenant before recording.
     *
     * @param token the opaque tracking token
     * @return true if the token resolved (whether or not it was the first click)
     */
    boolean trackClick(UUID token);

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
