package com.digishield.simulation.application;

import com.digishield.simulation.domain.CampaignStatus;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.LearningStatus;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimCampaignFunnel;
import com.digishield.simulation.domain.SimResult;
import com.digishield.simulation.infrastructure.SimCampaignFunnelRepository;
import com.digishield.simulation.infrastructure.SimCampaignRepository;
import com.digishield.simulation.infrastructure.SimResultRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds demo simulation campaigns, a funnel and per-user results for the
 * {@code dev} profile so the campaigns/results screens render against a real
 * datasource. All rows are scoped to the fixed demo tenant.
 */
@Component
@Profile("dev | seed")
@Order(20)
public class SimulationDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;

    private final SimCampaignRepository campaignRepository;
    private final SimCampaignFunnelRepository funnelRepository;
    private final SimResultRepository resultRepository;

    public SimulationDevSeeder(SimCampaignRepository campaignRepository,
                               SimCampaignFunnelRepository funnelRepository,
                               SimResultRepository resultRepository) {
        this.campaignRepository = campaignRepository;
        this.funnelRepository = funnelRepository;
        this.resultRepository = resultRepository;
    }

    @Override
    public void run(String... args) {
        if (!campaignRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            return;
        }

        // 1) Completed campaign with a full funnel (1000 -> 540 -> 132 -> 41 -> 88).
        UUID completedId = UUID.randomUUID();
        campaignRepository.save(new SimCampaign(
                completedId, DEMO_TENANT, Channel.EMAIL, CampaignStatus.COMPLETED,
                UUID.randomUUID(), "Hoàn tiền học phí"));
        funnelRepository.save(new SimCampaignFunnel(
                completedId, DEMO_TENANT, 1000, 540, 132, 41, 88));

        resultRepository.save(new SimResult(
                UUID.randomUUID(), DEMO_TENANT, completedId, null, "Nguyễn Minh An",
                "Kế toán", SimAction.CLICK, LearningStatus.IN_PROGRESS));
        resultRepository.save(new SimResult(
                UUID.randomUUID(), DEMO_TENANT, completedId, null, "Trần Thị Bình",
                "Kinh doanh", SimAction.REPORT, LearningStatus.NONE));
        resultRepository.save(new SimResult(
                UUID.randomUUID(), DEMO_TENANT, completedId, null, "Lê Văn Cường",
                "Kế toán", SimAction.DELIVERED, LearningStatus.NONE));

        // 2) A campaign currently running.
        campaignRepository.save(new SimCampaign(
                UUID.randomUUID(), DEMO_TENANT, Channel.SMS, CampaignStatus.RUNNING,
                UUID.randomUUID(), "Cập nhật bảo mật ngân hàng"));

        // 3) A draft campaign awaiting scheduling.
        campaignRepository.save(new SimCampaign(
                UUID.randomUUID(), DEMO_TENANT, Channel.ZALO, CampaignStatus.DRAFT,
                UUID.randomUUID(), "Khuyến mãi nội bộ Q3"));
    }
}
