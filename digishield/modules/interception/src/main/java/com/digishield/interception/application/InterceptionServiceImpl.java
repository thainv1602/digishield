package com.digishield.interception.application;

import com.digishield.interception.api.InterceptionService;
import com.digishield.interception.api.dto.AccountWatchEntryView;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.api.dto.InterventionEventView;
import com.digishield.interception.domain.AccountWatchEntry;
import com.digishield.interception.domain.Decision;
import com.digishield.interception.domain.InterventionEvent;
import com.digishield.interception.domain.InterventionSignal;
import com.digishield.interception.domain.RiskLevel;
import com.digishield.interception.domain.WatchType;
import com.digishield.interception.infrastructure.AccountWatchEntryRepository;
import com.digishield.interception.infrastructure.InterventionEventRepository;
import com.digishield.shared.tenantcontext.Messages;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of {@link InterceptionService} with sample logic.
 * <p>
 * Sample rule: if the user is on a call (onCall) AND transferring to a new payee
 * (newPayee) AND the destination account matches the watchlist, then PAUSE with an educational message.
 */
@Service
@Transactional
public class InterceptionServiceImpl implements InterceptionService {

    private final AccountWatchEntryRepository watchRepository;
    private final InterventionEventRepository eventRepository;
    private final Messages messages;

    public InterceptionServiceImpl(AccountWatchEntryRepository watchRepository,
                                  InterventionEventRepository eventRepository,
                                  Messages messages) {
        this.watchRepository = watchRepository;
        this.eventRepository = eventRepository;
        this.messages = messages;
    }

    @Override
    public InterventionDecision evaluate(EvaluateRequest request) {
        UUID tenantId = TenantContext.requireUuid();

        List<InterventionSignal> signals = new ArrayList<>();
        if (request.onCall()) {
            signals.add(InterventionSignal.ON_CALL);
        }
        if (request.newPayee()) {
            signals.add(InterventionSignal.NEW_PAYEE);
        }

        Optional<AccountWatchEntry> hit = watchRepository.findFirstByTenantIdAndValueOrderByAddedAtDesc(tenantId, request.destAccount());
        boolean watchlistHit = hit.isPresent();
        if (watchlistHit) {
            signals.add(InterventionSignal.WATCHLIST_HIT);
        }

        Decision decision;
        String message;
        if (request.onCall() && request.newPayee() && watchlistHit) {
            decision = Decision.PAUSE;
            message = messages.get("intervention.hold");
        } else if (watchlistHit) {
            decision = Decision.WARN;
            message = messages.get("intervention.watch");
        } else {
            decision = Decision.ALLOW;
            message = messages.get("intervention.clear");
        }

        // Persisted by enum name, upper case, exactly as the rows written before
        // InterventionSignal existed — so nothing has to be rewritten, and the
        // read path below keeps normalising either spelling.
        InterventionEvent event = new InterventionEvent(
                UUID.randomUUID(), tenantId, request.userId(),
                signals.stream().map(Enum::name).collect(Collectors.joining(",")),
                decision, Instant.now());
        eventRepository.save(event);

        // Lower case, like InterventionEventView below and like the spec. The
        // decision was already fixed this way; the signals beside it were not,
        // so one event came back ["ON_CALL"] here and ["on_call"] from
        // GET /interventions.
        return new InterventionDecision(
                decision.name().toLowerCase(Locale.ROOT),
                signals.stream().map(InterventionSignal::wireName).toList(),
                message);
    }

    @Override
    public Optional<AccountWatchEntry> checkAccount(String value) {
        UUID tenantId = TenantContext.requireUuid();
        return watchRepository.findFirstByTenantIdAndValueOrderByAddedAtDesc(tenantId, value);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountWatchEntryView> listWatchlist() {
        UUID tenantId = TenantContext.requireUuid();
        return watchRepository.findByTenantIdOrderByAddedAtDesc(tenantId).stream()
                .map(InterceptionServiceImpl::toView)
                .toList();
    }

    @Override
    public AccountWatchEntryView addWatchEntry(AccountWatchEntryView request) {
        UUID tenantId = TenantContext.requireUuid();

        // Validated rather than dereferenced: a body that omits risk_level used to
        // NPE inside valueOf and surface as a 500, blaming the server for a
        // malformed request.
        WatchType type = requiredEnum(WatchType.class, request.type(), "type");
        RiskLevel riskLevel = requiredEnum(RiskLevel.class, request.riskLevel(), "risk_level");
        if (request.value() == null || request.value().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value is required");
        }
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        Instant addedAt = request.addedAt() != null ? request.addedAt() : Instant.now();

        AccountWatchEntry entry = new AccountWatchEntry(
                id, tenantId, type, request.value(), riskLevel, request.source(), addedAt);
        return toView(watchRepository.save(entry));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterventionEventView> listInterventions(int page, int size) {
        UUID tenantId = TenantContext.requireUuid();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        return eventRepository.findByTenantIdOrderByTsDesc(tenantId, pageable).stream()
                .map(InterceptionServiceImpl::toView)
                .toList();
    }

    private static AccountWatchEntryView toView(AccountWatchEntry entry) {
        return new AccountWatchEntryView(
                entry.getId(),
                entry.getType().name().toLowerCase(Locale.ROOT),
                entry.getValue(),
                entry.getRiskLevel().name().toLowerCase(Locale.ROOT),
                entry.getSource(),
                entry.getAddedAt());
    }

    /**
     * Parses a required enum field of the request body, case-insensitively.
     *
     * @throws ResponseStatusException {@code 400} when the field is absent, blank
     *     or outside the enum — all of them caller mistakes, not server faults
     */
    private static <E extends Enum<E>> E requiredEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be one of " + Arrays.stream(type.getEnumConstants())
                            .map(c -> c.name().toLowerCase(Locale.ROOT))
                            .toList());
        }
    }

    private static InterventionEventView toView(InterventionEvent event) {
        List<String> signals = (event.getSignals() == null || event.getSignals().isBlank())
                ? List.of()
                : Arrays.stream(event.getSignals().split(","))
                        .map(s -> s.trim().toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .toList();
        return new InterventionEventView(
                event.getId(),
                event.getTenantId(),
                event.getUserId(),
                signals,
                event.getDecision().name().toLowerCase(Locale.ROOT),
                event.getTs());
    }
}
