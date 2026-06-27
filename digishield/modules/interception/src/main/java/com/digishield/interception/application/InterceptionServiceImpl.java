package com.digishield.interception.application;

import com.digishield.interception.api.InterceptionService;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.domain.AccountWatchEntry;
import com.digishield.interception.domain.Decision;
import com.digishield.interception.domain.InterventionEvent;
import com.digishield.interception.infrastructure.AccountWatchEntryRepository;
import com.digishield.interception.infrastructure.InterventionEventRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public InterceptionServiceImpl(AccountWatchEntryRepository watchRepository,
                                  InterventionEventRepository eventRepository) {
        this.watchRepository = watchRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public InterventionDecision evaluate(EvaluateRequest request) {
        UUID tenantId = TenantContext.requireUuid();

        List<String> signals = new ArrayList<>();
        if (request.onCall()) {
            signals.add("ON_CALL");
        }
        if (request.newPayee()) {
            signals.add("NEW_PAYEE");
        }

        Optional<AccountWatchEntry> hit = watchRepository.findByTenantIdAndValue(tenantId, request.destAccount());
        boolean watchlistHit = hit.isPresent();
        if (watchlistHit) {
            signals.add("WATCHLIST_HIT");
        }

        Decision decision;
        String message;
        if (request.onCall() && request.newPayee() && watchlistHit) {
            decision = Decision.PAUSE;
            message = "Giao dịch đang được tạm dừng để bảo vệ bạn. Bạn đang chuyển tiền cho người nhận lần đầu "
                    + "trong khi đang nghe điện thoại, và tài khoản đích nằm trong danh sách cảnh báo lừa đảo. "
                    + "Hãy gác máy và xác minh trực tiếp trước khi tiếp tục.";
        } else if (watchlistHit) {
            decision = Decision.WARN;
            message = "Cảnh báo: tài khoản đích nằm trong danh sách theo dõi. Hãy kiểm tra kỹ trước khi chuyển.";
        } else {
            decision = Decision.ALLOW;
            message = "Không phát hiện dấu hiệu rủi ro đáng kể.";
        }

        // Record the intervention event.
        InterventionEvent event = new InterventionEvent(
                UUID.randomUUID(), tenantId, request.userId(),
                String.join(",", signals), decision, Instant.now());
        eventRepository.save(event);

        return new InterventionDecision(decision.name(), signals, message);
    }

    @Override
    public Optional<AccountWatchEntry> checkAccount(String value) {
        UUID tenantId = TenantContext.requireUuid();
        return watchRepository.findByTenantIdAndValue(tenantId, value);
    }
}
