package com.digishield.ai.application;

import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.contracts.events.AidaOrchestrationCompletedEvent;
import com.digishield.shared.tenantcontext.Messages;
import com.digishield.shared.tenantcontext.TenantContext;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Finalises an {@code AidaRun} once the analytics module reports the orchestration
 * complete: flips the record from {@code running} to {@code success} and writes a
 * summary with the real evaluated/enrolled counts, so the AIDA console reflects
 * the actual outcome rather than a placeholder.
 * <p>
 * {@link ApplicationModuleListener} runs asynchronously in its own transaction,
 * so the tenant is set from the event for the duration of the update (RLS scope).
 */
@Component
class AidaOrchestrationCompletedListener {

    private static final Logger LOG = LoggerFactory.getLogger(AidaOrchestrationCompletedListener.class);

    private final AidaRunRepository aidaRunRepository;
    private final Messages messages;

    AidaOrchestrationCompletedListener(AidaRunRepository aidaRunRepository, Messages messages) {
        this.aidaRunRepository = aidaRunRepository;
        this.messages = messages;
    }

    @ApplicationModuleListener
    void on(AidaOrchestrationCompletedEvent event) {
        TenantContext.set(event.tenantId().toString());
        // This runs on an async listener thread with no request locale, so restore the
        // triggering request's language (carried on the event) before resolving the summary;
        // otherwise it would drift to the default locale and mismatch the "running" summary.
        applyLocale(event.locale());
        try {
            AidaRun run = aidaRunRepository.findById(event.runId()).orElse(null);
            if (run == null) {
                LOG.warn("AIDA completion for unknown run={} tenant={}", event.runId(), event.tenantId());
                return;
            }
            String summary = event.usersEvaluated() == 0
                    ? messages.get("aida.summary.none")
                    : messages.get("aida.summary.done", event.usersEvaluated(), event.usersEnrolled());
            run.complete("success", summary);
            aidaRunRepository.save(run);
            LOG.info("AIDA run={} completed: evaluated={} enrolled={}",
                    event.runId(), event.usersEvaluated(), event.usersEnrolled());
        } finally {
            // Clear the locale too: the listener thread is pooled and would otherwise leak
            // this locale into the next, unrelated task.
            LocaleContextHolder.resetLocaleContext();
            TenantContext.clear();
        }
    }

    private static void applyLocale(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return;
        }
        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));
    }
}
