package com.digishield.simulation.application;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.simulation.domain.CampaignStatus;
import com.digishield.simulation.domain.Channel;
import com.digishield.simulation.domain.SimAction;
import com.digishield.simulation.domain.SimCampaign;
import com.digishield.simulation.domain.SimEvent;
import com.digishield.simulation.infrastructure.SimCampaignFunnelRepository;
import com.digishield.simulation.infrastructure.SimCampaignRepository;
import com.digishield.simulation.infrastructure.SimEventRepository;
import com.digishield.simulation.infrastructure.SimResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimulationServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class SimulationServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SimCampaignRepository campaignRepository;

    @Mock
    private SimEventRepository eventRepository;

    @Mock
    private SimResultRepository resultRepository;

    @Mock
    private SimCampaignFunnelRepository funnelRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SimulationServiceImpl simulationService;

    @Captor
    private ArgumentCaptor<SimCampaign> campaignCaptor;

    @Captor
    private ArgumentCaptor<UserClickedSimulationEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCampaign_persistsDraftCampaignForCurrentTenant() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(campaignRepository.save(any(SimCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SimCampaign result = simulationService.createCampaign(Channel.EMAIL, templateId, groupId);

        // Assert: a DRAFT campaign was persisted for the current tenant
        verify(campaignRepository).save(campaignCaptor.capture());
        SimCampaign persisted = campaignCaptor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(persisted.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(persisted.getTemplateId()).isEqualTo(templateId);
        assertThat(persisted.getGroupId()).isEqualTo(groupId);
        assertThat(result).isSameAs(persisted);
    }

    @Test
    void recordEvent_whenClick_publishesUserClickedEvent() {
        // Arrange
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(eventRepository.save(any(SimEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SimEvent saved = simulationService.recordEvent(campaignId, userId, SimAction.CLICK);

        // Assert: the event entity was persisted with action CLICK
        ArgumentCaptor<SimEvent> simEventCaptor = ArgumentCaptor.forClass(SimEvent.class);
        verify(eventRepository).save(simEventCaptor.capture());
        SimEvent persisted = simEventCaptor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getCampaignId()).isEqualTo(campaignId);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getAction()).isEqualTo(SimAction.CLICK);
        assertThat(saved).isSameAs(persisted);

        // Assert: a UserClickedSimulationEvent was published with correct fields
        verify(eventPublisher).publish(eventCaptor.capture());
        UserClickedSimulationEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.campaignId()).isEqualTo(campaignId);
    }

    @Test
    void recordEvent_whenNonClickAction_doesNotPublishEvent() {
        // Arrange
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(eventRepository.save(any(SimEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        simulationService.recordEvent(campaignId, userId, SimAction.OPEN);

        // Assert: persisted but no event published for a non-CLICK action
        verify(eventRepository).save(any(SimEvent.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void recordEvent_whenTenantNotSet_throwsIllegalState() {
        // Arrange
        TenantContext.clear();

        // Act + Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> simulationService.recordEvent(
                                UUID.randomUUID(), UUID.randomUUID(), SimAction.CLICK))
                .isInstanceOf(IllegalStateException.class);
        verify(eventRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
