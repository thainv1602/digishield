package com.digishield.ai.application;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.Difficulty;
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
                generated.subject(), generated.bodyRef(), generated.body(),
                industry, generated.difficulty(), TemplateStatus.DRAFT);
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

    @Override
    public SimTemplateView createTemplate(TemplateChannel channel, String subject, String body,
                                          String category, Difficulty difficulty, boolean approved) {
        UUID tenantId = TenantContext.requireUuid();
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        Difficulty diff = difficulty != null ? difficulty : Difficulty.MEDIUM;
        AiTemplate template = new AiTemplate(
                UUID.randomUUID(), tenantId, channel,
                subject.trim(), slugFor(channel, subject), body,
                normalize(category), diff,
                approved ? TemplateStatus.APPROVED : TemplateStatus.DRAFT);
        return toView(templateRepository.save(template));
    }

    @Override
    public SimTemplateView updateTemplate(UUID id, TemplateChannel channel, String subject, String body,
                                          String category, Difficulty difficulty) {
        AiTemplate template = requireOwned(id);
        if (channel != null) {
            template.setChannel(channel);
        }
        if (subject != null && !subject.isBlank()) {
            template.setSubject(subject.trim());
        }
        if (body != null) {
            template.setBody(body);
        }
        if (category != null) {
            template.setCategory(normalize(category));
        }
        if (difficulty != null) {
            template.setDifficulty(difficulty);
        }
        return toView(templateRepository.save(template));
    }

    @Override
    public SimTemplateView submitTemplate(UUID id) {
        AiTemplate template = requireOwned(id);
        template.setStatus(TemplateStatus.APPROVED);
        return toView(templateRepository.save(template));
    }

    @Override
    public void deleteTemplate(UUID id) {
        templateRepository.delete(requireOwned(id));
    }

    /**
     * Loads a template by id and asserts it belongs to the current tenant.
     * (Belt-and-braces alongside RLS so a cross-tenant id 404s rather than NPEs.)
     */
    private AiTemplate requireOwned(UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        return templateRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Stable slug reference for the body, derived from channel + subject. */
    private static String slugFor(TemplateChannel channel, String subject) {
        String slug = subject.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.length() > 40 ? slug.substring(0, 40) : slug;
        return "tmpl/" + channel.name().toLowerCase(Locale.ROOT) + "/" + (slug.isBlank() ? "custom" : slug);
    }

    private SimTemplateView toView(AiTemplate t) {
        return new SimTemplateView(
                t.getId(),
                t.getChannel().name().toLowerCase(Locale.ROOT),
                t.getSubject(),
                t.getBodyRef(),
                t.getBody(),
                t.getCategory(),
                t.getDifficulty().name().toLowerCase(Locale.ROOT),
                t.getStatus().name().toLowerCase(Locale.ROOT));
    }
}
