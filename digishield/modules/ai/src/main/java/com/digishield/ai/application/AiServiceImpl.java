package com.digishield.ai.application;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.contracts.events.AidaOrchestrationRequestedEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Default implementation of {@link AiService}. Owns persistence (template
 * library, AIDA run history) and delegates the AI behaviour (classification,
 * moderation, template generation) to an injected {@link AiClient} — the
 * deterministic {@code StubAiClient} by default, or the real {@code ClaudeAiClient}
 * when {@code digishield.ai.claude.enabled=true}.
 */
@Service
@Transactional
public class AiServiceImpl implements AiService {

    private static final Logger LOG = LoggerFactory.getLogger(AiServiceImpl.class);

    private final AiTemplateRepository templateRepository;
    private final AidaRunRepository aidaRunRepository;
    private final AiClient aiClient;
    private final EventPublisher eventPublisher;

    public AiServiceImpl(AiTemplateRepository templateRepository,
                         AidaRunRepository aidaRunRepository,
                         AiClient aiClient,
                         EventPublisher eventPublisher) {
        this.templateRepository = templateRepository;
        this.aidaRunRepository = aidaRunRepository;
        this.aiClient = aiClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SimTemplateView generateTemplate(TemplateChannel channel, String industry, String season) {
        UUID tenantId = TenantContext.requireUuid();
        GeneratedTemplate generated = aiClient.generate(channel, industry, season);
        AiTemplate template = new AiTemplate(
                UUID.randomUUID(), tenantId, channel,
                generated.subject(), generated.bodyRef(), generated.difficulty(),
                TemplateStatus.DRAFT);
        return toView(templateRepository.save(template));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimTemplateView> listTemplates() {
        UUID tenantId = TenantContext.requireUuid();
        return templateRepository.findByTenantId(tenantId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassificationView classify(String payload) {
        return aiClient.classify(payload);
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationView moderate(String content) {
        return aiClient.moderate(content);
    }

    @Override
    public void runOrchestration(String scope, UUID scopeId) {
        // Kick off the real AIDA pipeline: persist a "running" record, then hand off
        // to analytics (recompute risk -> flag at-risk users) which drives learning
        // (auto-enroll) and reports back a completion event that finalises this run.
        UUID tenantId = TenantContext.requireUuid();
        String safeScope = (scope == null || scope.isBlank()) ? "org" : scope.trim();
        UUID runId = UUID.randomUUID();
        aidaRunRepository.save(new AidaRun(
                runId, tenantId, safeScope, scopeId, "running",
                "Đang tính lại rủi ro và tự động đăng ký cho phạm vi \"" + safeScope + "\"…",
                Instant.now()));
        eventPublisher.publish(new AidaOrchestrationRequestedEvent(tenantId, runId, safeScope, scopeId));
        LOG.info("AIDA orchestration started run={} tenant={} scope={} scopeId={}",
                runId, tenantId, safeScope, scopeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AidaRunView> listRuns() {
        UUID tenantId = TenantContext.requireUuid();
        return aidaRunRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toRunView)
                .toList();
    }

    private AidaRunView toRunView(AidaRun r) {
        return new AidaRunView(
                r.getId(), r.getScope(), r.getScopeId(),
                r.getStatus(), r.getSummary(), r.getCreatedAt());
    }

    private SimTemplateView toView(AiTemplate t) {
        return new SimTemplateView(
                t.getId(),
                t.getChannel().name().toLowerCase(Locale.ROOT),
                t.getSubject(),
                t.getBodyRef(),
                t.getDifficulty().name().toLowerCase(Locale.ROOT),
                t.getStatus().name().toLowerCase(Locale.ROOT));
    }
}
