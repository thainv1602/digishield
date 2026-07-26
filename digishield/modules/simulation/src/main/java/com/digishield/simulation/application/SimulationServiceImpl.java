package com.digishield.simulation.application;

import com.digishield.contracts.events.SimulationDeliveryRequestedEvent;
import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.simulation.api.SimulationService;
import com.digishield.simulation.api.dto.SendResultDto;
import com.digishield.simulation.api.dto.SimCampaignDetailDto;
import com.digishield.simulation.api.dto.SimCampaignDto;
import com.digishield.simulation.domain.CampaignStatus;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimCampaignFunnel;
import com.digishield.simulation.domain.SimEvent;
import com.digishield.simulation.domain.SimRecipient;
import com.digishield.simulation.domain.SimResult;
import com.digishield.simulation.infrastructure.SimCampaignFunnelRepository;
import com.digishield.simulation.infrastructure.SimCampaignRepository;
import com.digishield.simulation.infrastructure.SimEventRepository;
import com.digishield.simulation.infrastructure.SimRecipientRepository;
import com.digishield.simulation.infrastructure.SimResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final SimRecipientRepository recipientRepository;
    private final EventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    public SimulationServiceImpl(SimCampaignRepository campaignRepository,
                                 SimEventRepository eventRepository,
                                 SimResultRepository resultRepository,
                                 SimCampaignFunnelRepository funnelRepository,
                                 SimRecipientRepository recipientRepository,
                                 EventPublisher eventPublisher,
                                 JdbcTemplate jdbcTemplate) {
        this.campaignRepository = campaignRepository;
        this.eventRepository = eventRepository;
        this.resultRepository = resultRepository;
        this.funnelRepository = funnelRepository;
        this.recipientRepository = recipientRepository;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
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
    public SendResultDto sendCampaign(UUID campaignId, List<UUID> userIds, String linkBaseUrl) {
        UUID tenantId = TenantContext.requireUuid();
        SimCampaign campaign = campaignRepository.findById(campaignId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy chiến dịch: " + campaignId));

        String base = linkBaseUrl == null ? "" : linkBaseUrl.replaceAll("/+$", "");
        List<UUID> recipients = userIds == null ? List.of() : userIds;
        List<SendResultDto.Recipient> links = new ArrayList<>();
        Instant now = Instant.now();
        for (UUID userId : recipients) {
            UUID token = UUID.randomUUID();
            recipientRepository.save(new SimRecipient(token, tenantId, campaignId, userId, now));
            // A "delivered" event so the funnel reflects the send immediately.
            eventRepository.save(new SimEvent(
                    UUID.randomUUID(), tenantId, campaignId, userId, SimAction.DELIVERED, now));
            String trackPath = "/api/v1/sim/track/" + token;
            // Ask the notification module to actually deliver this link over the
            // campaign channel (email/SMS). Decoupled via a domain event.
            eventPublisher.publish(new SimulationDeliveryRequestedEvent(
                    tenantId, userId, campaignId, campaign.getChannel().name(), trackPath));
            links.add(new SendResultDto.Recipient(userId, token, base + trackPath));
        }

        campaign.setStatus(CampaignStatus.RUNNING);
        campaignRepository.save(campaign);

        return new SendResultDto(
                campaignId,
                CampaignStatus.RUNNING.name().toLowerCase(),
                links.size(),
                links);
    }

    @Override
    public boolean trackClick(UUID token) {
        // This runs on the PUBLIC tracking endpoint, which has no tenant context.
        // Resolve the token with a plain JdbcTemplate read: it does not go through
        // the RLS repository aspect and runs as the superuser connection, so it
        // bypasses row-level security (the token itself is the unguessable secret).
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tenant_id, campaign_id, user_id, clicked_at "
                        + "FROM sim_recipient WHERE id = ?", token);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        boolean alreadyClicked = row.get("clicked_at") != null;

        // Stamp the click once (superuser update, still no RLS). If another request
        // won the race (0 rows updated) we simply don't re-fire the event.
        int updated = alreadyClicked ? 0 : jdbcTemplate.update(
                "UPDATE sim_recipient SET clicked_at = now() WHERE id = ? AND clicked_at IS NULL", token);
        if (updated == 0) {
            return true; // token valid, but the click was already recorded
        }

        UUID tenantId = (UUID) row.get("tenant_id");
        UUID campaignId = (UUID) row.get("campaign_id");
        UUID userId = (UUID) row.get("user_id");
        // Set the tenant so the SimEvent insert (and downstream listeners) are
        // correctly scoped, then record the CLICK through the normal path.
        TenantContext.set(tenantId.toString());
        recordEvent(campaignId, userId, SimAction.CLICK);
        return true;
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
                campaign.getGroupId(),
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
