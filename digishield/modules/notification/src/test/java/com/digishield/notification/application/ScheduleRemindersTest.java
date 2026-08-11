package com.digishield.notification.application;

import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Scheduling reminders: who receives one, and when it comes due.
 *
 * <p>Both halves fail quietly when wrong. A malformed id in the target filter
 * that silently widens the audience sends a reminder to people who were never
 * selected; an unparsed due rule that quietly means "three days" schedules a
 * deadline nobody chose.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleRemindersTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private NotificationRepository repository;

    @Mock
    private com.digishield.notification.api.NotificationGateway gateway;

    @Mock
    private com.digishield.notification.api.RecipientResolver recipients;

    @Mock
    private com.digishield.notification.api.UserDirectory userDirectory;

    @Mock
    private com.digishield.notification.api.RealtimeNotifier realtime;

    @Mock
    private com.digishield.shared.tenantcontext.Messages messages;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT.toString());
        lenient().when(messages.get(anyString())).thenReturn("Nhắc hoàn thành khoá học");
        lenient().when(messages.get(anyString(), any())).thenReturn("Hạn theo quy tắc");
        lenient().when(repository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private List<Notification> schedule(Map<String, Object> filter, String rule) {
        return service.scheduleReminders(filter, rule, null);
    }

    @Test
    @DisplayName("the named recipients are the ones scheduled")
    void explicitRecipientsAreUsed() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        List<Notification> scheduled = schedule(Map.of("user_ids", List.of(a, b.toString())), "3d");

        assertThat(scheduled).hasSize(2);
        assertThat(scheduled).extracting(Notification::getUserId).containsExactlyInAnyOrder(a, b);
        assertThat(scheduled).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
            assertThat(n.getType()).isEqualTo(NotificationType.REMINDER);
            // No channel asked for means in-app, the one that needs no transport.
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        });
    }

    @Test
    @DisplayName("an unparseable id is dropped, not turned into a wider audience")
    void aMalformedIdIsIgnored() {
        UUID valid = UUID.randomUUID();

        List<Notification> scheduled = schedule(
                Map.of("user_ids", List.of(valid.toString(), "not-a-uuid", "  ")), "1d");

        assertThat(scheduled).extracting(Notification::getUserId).containsExactly(valid);
    }

    @Test
    @DisplayName("the same recipient twice gets one reminder")
    void duplicatesAreCollapsed() {
        UUID once = UUID.randomUUID();

        List<Notification> scheduled = schedule(
                Map.of("user_ids", List.of(once, once.toString())), "1d");

        assertThat(scheduled).hasSize(1);
    }

    @Test
    @DisplayName("with no filter, everyone the tenant has notified before is reminded")
    void anEmptyFilterFallsBackToKnownRecipients() {
        UUID known = UUID.randomUUID();
        when(repository.findByTenantId(TENANT)).thenReturn(List.of(
                new Notification(UUID.randomUUID(), TENANT, known, NotificationType.ALERT,
                        NotificationChannel.IN_APP, NotificationStatus.SENT, "t", "b", null)));

        assertThat(schedule(null, "1d")).extracting(Notification::getUserId).containsExactly(known);
    }

    /*
     * Notification has one timestamp column, created_at, and a scheduled
     * reminder stores its due time there — so these assertions read a value in
     * the future. Named here because the getter says "created" and means
     * "due" for this type.
     */
    @Test
    @DisplayName("a relative rule sets the deadline it names")
    void relativeRulesAreHonoured() {
        UUID user = UUID.randomUUID();
        Instant before = Instant.now();

        Instant inHours = schedule(Map.of("user_ids", List.of(user)), "6h")
                .getFirst().getCreatedAt();
        Instant inDays = schedule(Map.of("user_ids", List.of(user)), "2d")
                .getFirst().getCreatedAt();
        Instant inMinutes = schedule(Map.of("user_ids", List.of(user)), "30m")
                .getFirst().getCreatedAt();

        assertThat(Duration.between(before, inHours).toHours()).isBetween(5L, 6L);
        assertThat(Duration.between(before, inDays).toDays()).isEqualTo(2L);
        assertThat(Duration.between(before, inMinutes).toMinutes()).isBetween(29L, 30L);
    }

    @Test
    @DisplayName("no rule means three days; an unrecognised one means one day, not never")
    void unparsedRulesFallBackToAStatedDefault() {
        UUID user = UUID.randomUUID();
        Instant before = Instant.now();

        Instant noRule = schedule(Map.of("user_ids", List.of(user)), null)
                .getFirst().getCreatedAt();
        Instant nonsense = schedule(Map.of("user_ids", List.of(user)), "khi nào rảnh")
                .getFirst().getCreatedAt();

        assertThat(Duration.between(before, noRule).toDays()).isEqualTo(3L);
        assertThat(Duration.between(before, nonsense).toDays()).isEqualTo(1L);
    }

    @Test
    @DisplayName("an explicit channel is kept")
    void anExplicitChannelIsUsed() {
        UUID user = UUID.randomUUID();

        List<Notification> scheduled = service.scheduleReminders(
                Map.of("user_ids", List.of(user)), "1d", NotificationChannel.EMAIL);

        assertThat(scheduled.getFirst().getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
