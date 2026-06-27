package com.digishield.interception.application;

import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.domain.AccountWatchEntry;
import com.digishield.interception.domain.Decision;
import com.digishield.interception.domain.InterventionEvent;
import com.digishield.interception.domain.RiskLevel;
import com.digishield.interception.domain.WatchType;
import com.digishield.interception.infrastructure.AccountWatchEntryRepository;
import com.digishield.interception.infrastructure.InterventionEventRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InterceptionServiceImpl} — the core decision logic.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database. The service reads
 * the tenant via {@code TenantContext.requireUuid()}, so the tenant id is treated as a
 * UUID. Each branch of {@code evaluate(...)} is covered by a dedicated test method.
 */
@ExtendWith(MockitoExtension.class)
class InterceptionServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String DEST_ACCOUNT = "1234567890";

    @Mock
    private AccountWatchEntryRepository watchRepository;

    @Mock
    private InterventionEventRepository eventRepository;

    @InjectMocks
    private InterceptionServiceImpl interceptionService;

    @Captor
    private ArgumentCaptor<InterventionEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
        lenient().when(eventRepository.save(any(InterventionEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evaluate_whenOnCallAndNewPayeeAndWatchlistHit_pausesWithEducationalMessage() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AccountWatchEntry watchHit = new AccountWatchEntry(
                UUID.randomUUID(), TENANT_ID, WatchType.BANK_ACCOUNT, DEST_ACCOUNT,
                RiskLevel.CONFIRMED, "fraud-feed");
        when(watchRepository.findByTenantIdAndValue(TENANT_ID, DEST_ACCOUNT))
                .thenReturn(Optional.of(watchHit));
        EvaluateRequest request = new EvaluateRequest(
                userId, new BigDecimal("5000000"), DEST_ACCOUNT, true, true);

        // Act
        InterventionDecision decision = interceptionService.evaluate(request);

        // Assert: decision shape
        assertThat(decision.decision()).isEqualTo(Decision.PAUSE.name());
        assertThat(decision.signals()).containsExactly("ON_CALL", "NEW_PAYEE", "WATCHLIST_HIT");
        assertThat(decision.message()).isNotBlank();

        // Assert: persisted intervention event payload
        verify(eventRepository).save(eventCaptor.capture());
        InterventionEvent persisted = eventCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getDecision()).isEqualTo(Decision.PAUSE);
        assertThat(persisted.getSignals()).isEqualTo("ON_CALL,NEW_PAYEE,WATCHLIST_HIT");
        assertThat(persisted.getTs()).isNotNull();
    }

    @Test
    void evaluate_whenWatchlistHitButNotOnCallNorNewPayee_warns() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AccountWatchEntry watchHit = new AccountWatchEntry(
                UUID.randomUUID(), TENANT_ID, WatchType.BANK_ACCOUNT, DEST_ACCOUNT,
                RiskLevel.HIGH, "fraud-feed");
        when(watchRepository.findByTenantIdAndValue(TENANT_ID, DEST_ACCOUNT))
                .thenReturn(Optional.of(watchHit));
        EvaluateRequest request = new EvaluateRequest(
                userId, new BigDecimal("100"), DEST_ACCOUNT, false, false);

        // Act
        InterventionDecision decision = interceptionService.evaluate(request);

        // Assert
        assertThat(decision.decision()).isEqualTo(Decision.WARN.name());
        assertThat(decision.signals()).containsExactly("WATCHLIST_HIT");

        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDecision()).isEqualTo(Decision.WARN);
        assertThat(eventCaptor.getValue().getSignals()).isEqualTo("WATCHLIST_HIT");
    }

    @Test
    void evaluate_whenOnCallAndNewPayeeButNoWatchlistHit_warnsIsNotTriggeredAndAllows() {
        // Arrange: high-risk behavioural signals but the destination is clean
        UUID userId = UUID.randomUUID();
        when(watchRepository.findByTenantIdAndValue(TENANT_ID, DEST_ACCOUNT))
                .thenReturn(Optional.empty());
        EvaluateRequest request = new EvaluateRequest(
                userId, new BigDecimal("9999"), DEST_ACCOUNT, true, true);

        // Act
        InterventionDecision decision = interceptionService.evaluate(request);

        // Assert: without a watchlist hit the rule falls through to ALLOW
        assertThat(decision.decision()).isEqualTo(Decision.ALLOW.name());
        assertThat(decision.signals()).containsExactly("ON_CALL", "NEW_PAYEE");

        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDecision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void evaluate_whenNoSignals_allowsAndPersistsEmptySignals() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(watchRepository.findByTenantIdAndValue(TENANT_ID, DEST_ACCOUNT))
                .thenReturn(Optional.empty());
        EvaluateRequest request = new EvaluateRequest(
                userId, new BigDecimal("10"), DEST_ACCOUNT, false, false);

        // Act
        InterventionDecision decision = interceptionService.evaluate(request);

        // Assert
        assertThat(decision.decision()).isEqualTo(Decision.ALLOW.name());
        assertThat(decision.signals()).isEmpty();

        verify(eventRepository).save(eventCaptor.capture());
        InterventionEvent persisted = eventCaptor.getValue();
        assertThat(persisted.getDecision()).isEqualTo(Decision.ALLOW);
        assertThat(persisted.getSignals()).isEmpty();
    }

    @Test
    void checkAccount_delegatesToRepositoryScopedByTenant() {
        // Arrange
        AccountWatchEntry entry = new AccountWatchEntry(
                UUID.randomUUID(), TENANT_ID, WatchType.PHONE, "+84900000000",
                RiskLevel.WATCH, "user-report");
        when(watchRepository.findByTenantIdAndValue(TENANT_ID, "+84900000000"))
                .thenReturn(Optional.of(entry));

        // Act
        Optional<AccountWatchEntry> result = interceptionService.checkAccount("+84900000000");

        // Assert
        assertThat(result).containsSame(entry);
        verify(watchRepository).findByTenantIdAndValue(TENANT_ID, "+84900000000");
    }
}
