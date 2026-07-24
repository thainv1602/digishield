package com.digishield.interception.application;

import com.digishield.interception.domain.AccountWatchEntry;
import com.digishield.interception.domain.Decision;
import com.digishield.interception.domain.InterventionEvent;
import com.digishield.interception.domain.RiskLevel;
import com.digishield.interception.domain.WatchType;
import com.digishield.interception.infrastructure.AccountWatchEntryRepository;
import com.digishield.interception.infrastructure.InterventionEventRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Seeds demo watchlist entries and intervention events for the {@code dev} profile
 * so the money-transfer watchlist and the SOC intervention log render against a real
 * datasource. All rows are scoped to the fixed demo tenant
 * {@link DemoTenants#DEMO_TENANT_ID}.
 */
@Component
@Profile("dev | seed")
@Order(20)
public class InterceptionDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;
    private static final UUID DEMO_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AccountWatchEntryRepository watchRepository;
    private final InterventionEventRepository eventRepository;

    public InterceptionDevSeeder(AccountWatchEntryRepository watchRepository,
                                 InterventionEventRepository eventRepository) {
        this.watchRepository = watchRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(String... args) {
        Instant now = Instant.now();

        if (watchRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            seedWatch(WatchType.BANK_ACCOUNT, "1903123456789", RiskLevel.CONFIRMED, "SIMO",
                    now.minus(3, ChronoUnit.DAYS));
            seedWatch(WatchType.BANK_ACCOUNT, "0071000654321", RiskLevel.HIGH, "NAPAS",
                    now.minus(2, ChronoUnit.DAYS));
            seedWatch(WatchType.PHONE, "+84900111222", RiskLevel.HIGH, "A05",
                    now.minus(1, ChronoUnit.DAYS));
            seedWatch(WatchType.WALLET, "momo-0938777666", RiskLevel.WATCH, "Internal",
                    now.minus(6, ChronoUnit.HOURS));
            seedWatch(WatchType.PHONE, "+84355987654", RiskLevel.WATCH, "user-report",
                    now.minus(2, ChronoUnit.HOURS));
        }

        if (eventRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            seedEvent("ON_CALL,NEW_PAYEE,WATCHLIST_HIT", Decision.PAUSE,
                    now.minus(5, ChronoUnit.MINUTES));
            seedEvent("WATCHLIST_HIT", Decision.WARN,
                    now.minus(42, ChronoUnit.MINUTES));
            seedEvent("ON_CALL,NEW_PAYEE", Decision.ALLOW,
                    now.minus(3, ChronoUnit.HOURS));
            seedEvent("NEW_PAYEE,WATCHLIST_HIT", Decision.WARN,
                    now.minus(8, ChronoUnit.HOURS));
            seedEvent("", Decision.ALLOW,
                    now.minus(1, ChronoUnit.DAYS));
            seedEvent("ON_CALL,WATCHLIST_HIT", Decision.PAUSE,
                    now.minus(2, ChronoUnit.DAYS));
        }
    }

    private void seedWatch(WatchType type, String value, RiskLevel riskLevel, String source, Instant addedAt) {
        watchRepository.save(new AccountWatchEntry(
                UUID.randomUUID(), DEMO_TENANT, type, value, riskLevel, source, addedAt));
    }

    private void seedEvent(String signals, Decision decision, Instant ts) {
        eventRepository.save(new InterventionEvent(
                UUID.randomUUID(), DEMO_TENANT, DEMO_USER, signals, decision, ts));
    }
}
