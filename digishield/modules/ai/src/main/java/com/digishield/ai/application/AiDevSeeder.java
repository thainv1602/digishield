package com.digishield.ai.application;

import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds demo AI-generated template drafts for the {@code dev} profile so the
 * simulation builder / template library renders against a real datasource. All
 * rows are scoped to the fixed demo tenant.
 */
@Component
@Profile("dev")
@Order(20)
public class AiDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;

    private final AiTemplateRepository templateRepository;

    public AiDevSeeder(AiTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void run(String... args) {
        if (!templateRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            return;
        }

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.EMAIL,
                "[Ngân hàng] Cảnh báo bảo mật tài khoản",
                "tmpl/email/ngan-hang", Difficulty.MEDIUM, TemplateStatus.DRAFT));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.SMS,
                "Thông báo từ bộ phận nhân sự",
                "tmpl/sms/nhan-su", Difficulty.EASY, TemplateStatus.APPROVED));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.ZALO,
                "Quét mã nhận ưu đãi mùa hè ngành bán lẻ",
                "tmpl/zalo/ban-le-he", Difficulty.HARD, TemplateStatus.DRAFT));
    }
}
