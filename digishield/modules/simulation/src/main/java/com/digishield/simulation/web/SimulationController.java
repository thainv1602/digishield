package com.digishield.simulation.web;

import com.digishield.simulation.api.SimulationService;
import com.digishield.simulation.api.dto.SimCampaignDetailDto;
import com.digishield.simulation.api.dto.SimCampaignDto;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimEvent;
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
    public ResponseEntity<SimCampaign> createCampaign(@RequestBody CreateCampaignRequest request) {
        SimCampaign campaign = simulationService.createCampaign(
                request.channel(), request.templateId(), request.groupId());
        return ResponseEntity.ok(campaign);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/events")
    public ResponseEntity<SimEvent> recordEvent(@RequestBody RecordEventRequest request) {
        SimEvent event = simulationService.recordEvent(
                request.campaignId(), request.userId(), request.action());
        return ResponseEntity.ok(event);
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
}
