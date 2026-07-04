package com.digishield.simulation.application;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.simulation.api.SimulationService;
import com.digishield.simulation.api.dto.SimCampaignDetailDto;
import com.digishield.simulation.api.dto.SimCampaignDto;
import com.digishield.simulation.domain.CampaignStatus;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimCampaignFunnel;
import com.digishield.simulation.domain.SimEvent;
import com.digishield.simulation.domain.SimResult;
import com.digishield.simulation.infrastructure.SimCampaignFunnelRepository;
import com.digishield.simulation.infrastructure.SimCampaignRepository;
import com.digishield.simulation.infrastructure.SimEventRepository;
import com.digishield.simulation.infrastructure.SimResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link SimulationService}.
 */
@Service
@Transactional
public class SimulationServiceImpl implements SimulationService {

    private final SimCampaignRepository campaignRepository;
    private final SimEventRepository eventRepository;
    private final SimResultRepository resultRepository;
    private final SimCampaignFunnelRepository funnelRepository;
    private final EventPublisher eventPublisher;

    public SimulationServiceImpl(SimCampaignRepository campaignRepository,
                                 SimEventRepository eventRepository,
                                 SimResultRepository resultRepository,
                                 SimCampaignFunnelRepository funnelRepository,
                                 EventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.eventRepository = eventRepository;
        this.resultRepository = resultRepository;
        this.funnelRepository = funnelRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SimCampaign createCampaign(Channel channel, UUID templateId, UUID groupId) {
        UUID tenantId = TenantContext.requireUuid();
        SimCampaign campaign = new SimCampaign(
                UUID.randomUUID(), tenantId, channel, CampaignStatus.DRAFT, templateId, groupId, null);
        return campaignRepository.save(campaign);
    }

    @Override
    public SimEvent recordEvent(UUID campaignId, UUID userId, SimAction action) {
        UUID tenantId = TenantContext.requireUuid();
        SimEvent event = new SimEvent(
                UUID.randomUUID(), tenantId, campaignId, userId, action, Instant.now());
        SimEvent saved = eventRepository.save(event);

        if (action == SimAction.CLICK) {
            eventPublisher.publish(new UserClickedSimulationEvent(tenantId, userId, campaignId));
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimCampaignDto> listCampaigns() {
        UUID tenantId = TenantContext.requireUuid();
        return campaignRepository.findByTenantId(tenantId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SimCampaignDetailDto getCampaign(UUID campaignId) {
        UUID tenantId = TenantContext.requireUuid();
        SimCampaign campaign = campaignRepository.findById(campaignId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy chiến dịch mô phỏng: " + campaignId));

        SimCampaignDetailDto.Funnel funnel = funnel(tenantId, campaignId);

        List<SimCampaignDetailDto.ResultRow> rows =
                resultRepository.findByTenantIdAndCampaignId(tenantId, campaignId).stream()
                        .map(this::toResultRow)
                        .toList();

        return new SimCampaignDetailDto(
                campaign.getId(),
                campaign.getName(),
                campaign.getChannel().name().toLowerCase(),
                campaign.getStatus().name().toLowerCase(),
                campaign.getTemplateId(),
                funnel,
                rows);
    }

    /**
     * Returns the funnel from the stored aggregate when present, otherwise
     * computes it live from the raw event stream.
     */
    private SimCampaignDetailDto.Funnel funnel(UUID tenantId, UUID campaignId) {
        return funnelRepository.findByTenantIdAndCampaignId(tenantId, campaignId)
                .map(f -> new SimCampaignDetailDto.Funnel(
                        f.getDelivered(), f.getOpened(), f.getClicked(),
                        f.getSubmitted(), f.getReported()))
                .orElseGet(() -> computeFunnel(tenantId, campaignId));
    }

    private SimCampaignDetailDto.Funnel computeFunnel(UUID tenantId, UUID campaignId) {
        List<SimEvent> events = eventRepository.findByTenantIdAndCampaignId(tenantId, campaignId);
        long delivered = count(events, SimAction.DELIVERED);
        long open = count(events, SimAction.OPEN);
        long click = count(events, SimAction.CLICK);
        long submit = count(events, SimAction.SUBMIT);
        long report = count(events, SimAction.REPORT);
        return new SimCampaignDetailDto.Funnel(delivered, open, click, submit, report);
    }

    private long count(List<SimEvent> events, SimAction action) {
        return events.stream().filter(e -> e.getAction() == action).count();
    }

    private SimCampaignDto toSummary(SimCampaign c) {
        return new SimCampaignDto(
                c.getId(),
                c.getName(),
                c.getChannel().name().toLowerCase(),
                c.getStatus().name().toLowerCase(),
                c.getTemplateId());
    }

    private SimCampaignDetailDto.ResultRow toResultRow(SimResult r) {
        return new SimCampaignDetailDto.ResultRow(
                r.getUserName(),
                r.getDepartment(),
                r.getAction().name().toLowerCase(),
                r.getLearningStatus().name().toLowerCase());
    }
}
