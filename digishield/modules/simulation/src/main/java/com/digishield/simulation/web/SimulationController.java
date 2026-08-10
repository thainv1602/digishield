package com.digishield.simulation.web;

import com.digishield.simulation.api.SimulationService;
import com.digishield.simulation.api.dto.SendResultDto;
import com.digishield.simulation.api.dto.SimCampaignDetailDto;
import com.digishield.simulation.api.dto.SimCampaignDto;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Sample REST controller for the simulation module.
 */
@RestController
@RequestMapping("/api/v1/sim")
@PreAuthorize("hasRole('MANAGER')")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<SimCampaignDto>> listCampaigns() {
        return ResponseEntity.ok(simulationService.listCampaigns());
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<SimCampaignDetailDto> getCampaign(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(simulationService.getCampaign(id));
    }

    @PostMapping("/campaigns")
    public ResponseEntity<SimCampaignDto> createCampaign(
            @RequestBody CreateCampaignRequest request) {
        SimCampaignDto campaign = simulationService.createCampaign(
                request.channel(), request.templateId(), request.groupId());
        return ResponseEntity.status(HttpStatus.CREATED).body(campaign);
    }

    /**
     * Records a tracked interaction. Accepted rather than created: the event is
     * a fact about the campaign, and the caller has nothing to do with the row.
     *
     * @param request the campaign, user and action
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/events")
    public ResponseEntity<Void> recordEvent(@RequestBody RecordEventRequest request) {
        simulationService.recordEvent(
                request.campaignId(), request.userId(), request.action());
        return ResponseEntity.accepted().build();
    }

    /**
     * Launches ("sends") a campaign to a set of recipients (resolved from the
     * target group by the caller). Returns per-recipient tracking links.
     */
    @PostMapping("/campaigns/{id}/send")
    public ResponseEntity<SendResultDto> send(@PathVariable("id") UUID id,
                                              @RequestBody SendCampaignRequest request) {
        // Tracking links are returned relative (the token path); the client
        // prepends its own origin. Real email delivery would use an absolute URL.
        return ResponseEntity.ok(simulationService.sendCampaign(id, request.userIds(), ""));
    }

    /**
     * Campaign creation payload.
     */
    public record CreateCampaignRequest(Channel channel, UUID templateId, UUID groupId) {
    }

    /**
     * Event recording payload.
     */
    public record RecordEventRequest(UUID campaignId, UUID userId, SimAction action) {
    }

    /**
     * Send payload — the recipient user ids (resolved from the target group).
     */
    public record SendCampaignRequest(List<UUID> userIds) {
    }
}
