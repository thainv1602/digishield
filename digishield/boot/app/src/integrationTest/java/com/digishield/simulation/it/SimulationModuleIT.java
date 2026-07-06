package com.digishield.simulation.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.simulation.api.SimulationService;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration test for the {@code simulation} module's outbound contract.
 *
 * <p>Verifies that recording a {@link SimAction#CLICK} publishes a
 * {@link UserClickedSimulationEvent} so downstream modules (learning, analytics,
 * ...) can react, while non-CLICK actions publish nothing.
 *
 * <p>Booted as a full {@link SpringBootTest} (like the reporting/interception
 * ITs) rather than a Spring Modulith slice: the boot app's composition-root
 * adapters (in the base {@code com.digishield} package) inject {@code AuthService}
 * and leak into any single-module slice, so a slice either misses that bean or —
 * once mocked from a module-owned test class — trips Modulith's architecture
 * verification with a forbidden {@code simulation -> auth :: api} dependency. The
 * full context provides the real {@code AuthService}; only a {@code JwtDecoder} is
 * stubbed for the OAuth2 resource-server security chain.
 *
 * <p>The module persists a {@code SimEvent} via JPA, so a real PostgreSQL is
 * provided by Testcontainers. Hibernate creates the schema
 * ({@code ddl-auto=create-drop}) since Flyway is disabled here.
 *
 * <p>Requires Docker and JDK 25.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        // No Redis in this IT: use a no-op cache and skip the Redis health check.
        "spring.cache.type=none",
        "management.health.redis.enabled=false"
})
class SimulationModuleIT {

    private static final String TENANT = "33333333-3333-3333-3333-333333333333";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestEventConfig {
        @org.springframework.context.annotation.Bean
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }

        // The full context auto-configures an OAuth2 resource-server security chain
        // that needs a JwtDecoder; the real one talks to an external IdP, so stub it.
        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        }
    }

    static class TestEventListener {
        private final java.util.List<Object> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        public void onEvent(Object event) {
            events.add(event);
        }

        public void clear() {
            events.clear();
        }

        public <T> java.util.List<T> ofType(Class<T> type) {
            return events.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }
    }

    @Autowired
    private SimulationService simulationService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.digishield.shared.messaging.EventPublisher eventPublisher;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private TestEventListener testEventListener;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUpMockPublisher() {
        testEventListener.clear();
        org.mockito.Mockito.doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            applicationEventPublisher.publishEvent(event);
            return null;
        }).when(eventPublisher).publish(org.mockito.Mockito.any());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * Recording a CLICK event publishes exactly one UserClickedSimulationEvent
     * carrying the current tenant, user and campaign.
     */
    @Test
    void recordingClickPublishesUserClickedEvent() {
        TenantContext.set(TENANT);

        UUID userId = UUID.randomUUID();
        SimCampaign campaign = simulationService.createCampaign(Channel.EMAIL, null, null);

        simulationService.recordEvent(campaign.getId(), userId, SimAction.CLICK);

        var published = testEventListener.ofType(UserClickedSimulationEvent.class);
        assertThat(published).hasSize(1);

        UserClickedSimulationEvent event = published.iterator().next();
        assertThat(event.tenantId().toString()).isEqualTo(TENANT);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.campaignId()).isEqualTo(campaign.getId());
    }

    /**
     * Non-CLICK actions (e.g. DELIVERED) must NOT emit the click event.
     */
    @Test
    void recordingNonClickPublishesNoEvent() {
        TenantContext.set(TENANT);

        SimCampaign campaign = simulationService.createCampaign(Channel.SMS, null, null);
        simulationService.recordEvent(campaign.getId(), UUID.randomUUID(), SimAction.DELIVERED);

        assertThat(testEventListener.ofType(UserClickedSimulationEvent.class)).isEmpty();
    }
}
