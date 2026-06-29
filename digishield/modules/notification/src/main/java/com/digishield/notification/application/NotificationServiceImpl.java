package com.digishield.notification.application;

import com.digishield.notification.api.NotificationGateway;
import com.digishield.notification.api.NotificationService;
import com.digishield.notification.api.RecipientResolver;
import com.digishield.notification.api.UserDirectory;
import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link NotificationService}. Minimal body for the skeleton.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    /** Matches relative due rules such as {@code "before_due:3d"} or {@code "in:12h"}. */
    private static final Pattern RELATIVE_RULE = Pattern.compile("(?:.*:)?(\\d+)\\s*([dhm])", Pattern.CASE_INSENSITIVE);

    private static final Logger LOG = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;
    private final NotificationGateway gateway;
    private final RecipientResolver recipients;
    private final UserDirectory userDirectory;

    public NotificationServiceImpl(NotificationRepository repository,
                                   NotificationGateway gateway,
                                   RecipientResolver recipients,
                                   UserDirectory userDirectory) {
        this.repository = repository;
        this.gateway = gateway;
        this.recipients = recipients;
        this.userDirectory = userDirectory;
    }

    @Override
    public Notification send(UUID userId, NotificationType type, NotificationChannel channel, String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        NotificationStatus status = deliver(channel, userId, title, body);
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, type, channel,
                status, title, body, Instant.now());
        return repository.save(notification);
    }

    /**
     * Attempts external delivery via the configured gateway and returns the
     * resulting status. In-app notifications are the persisted record itself, so
     * they are always SENT. External channels are SENT on success and FAILED if
     * the recipient can't be resolved or the gateway throws.
     */
    private NotificationStatus deliver(NotificationChannel channel, UUID userId, String title, String body) {
        if (channel == NotificationChannel.IN_APP) {
            return NotificationStatus.SENT;
        }
        String recipient = recipients.emailFor(userId).orElse(null);
        if (recipient == null) {
            LOG.warn("No recipient address for user {} on channel {}; marking FAILED", userId, channel);
            return NotificationStatus.FAILED;
        }
        try {
            gateway.deliver(channel.name(), recipient, title, body);
            return NotificationStatus.SENT;
        } catch (RuntimeException e) {
            LOG.error("Notification delivery to {} on {} failed: {}", recipient, channel, e.getMessage());
            return NotificationStatus.FAILED;
        }
    }

    @Override
    public Notification scheduleReminder(UUID userId, String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, NotificationType.REMINDER, NotificationChannel.IN_APP,
                NotificationStatus.SCHEDULED, title, body, Instant.now());
        return repository.save(notification);
    }

    @Override
    public List<Notification> broadcastAlert(String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        List<UUID> userIds = userDirectory.allUserIds();
        Instant now = Instant.now();
        List<Notification> created = new ArrayList<>(userIds.size());
        for (UUID userId : userIds) {
            created.add(repository.save(new Notification(
                    UUID.randomUUID(), tenantId, userId, NotificationType.ALERT, NotificationChannel.IN_APP,
                    NotificationStatus.SENT, title, body, now)));
        }
        LOG.info("Broadcast alert to {} users in tenant {}", created.size(), tenantId);
        return created;
    }

    @Override
    public Notification create(UUID userId, NotificationType type, NotificationChannel channel,
                               NotificationStatus status, String title, String body, Instant scheduledAt) {
        UUID tenantId = TenantContext.requireUuid();
        Notification notification = new Notification(
                UUID.randomUUID(),
                tenantId,
                userId,
                type != null ? type : NotificationType.SYSTEM,
                channel != null ? channel : NotificationChannel.IN_APP,
                status != null ? status : NotificationStatus.SENT,
                title,
                body,
                scheduledAt != null ? scheduledAt : Instant.now());
        return repository.save(notification);
    }

    @Override
    public List<Notification> scheduleReminders(Map<String, Object> targetFilter, String dueRule,
                                                NotificationChannel channel) {
        UUID tenantId = TenantContext.requireUuid();
        NotificationChannel resolvedChannel = channel != null ? channel : NotificationChannel.IN_APP;
        Instant dueAt = resolveDueAt(dueRule);
        Set<UUID> recipients = resolveRecipients(tenantId, targetFilter);

        String title = "Nhắc hoàn thành đào tạo bắt buộc";
        String body = "Bạn có khoá đào tạo bắt buộc cần hoàn thành (quy tắc: "
                + (dueRule != null && !dueRule.isBlank() ? dueRule : "before_due") + ").";

        List<Notification> scheduled = new ArrayList<>(recipients.size());
        for (UUID userId : recipients) {
            Notification notification = new Notification(
                    UUID.randomUUID(), tenantId, userId, NotificationType.REMINDER, resolvedChannel,
                    NotificationStatus.SCHEDULED, title, body, dueAt);
            scheduled.add(repository.save(notification));
        }
        return scheduled;
    }

    /**
     * Resolves the set of recipient user ids. Honours an explicit {@code user_ids} list in the
     * filter; otherwise falls back to the distinct users that already have notifications in the
     * tenant (we cannot enumerate the directory across module boundaries).
     */
    private Set<UUID> resolveRecipients(UUID tenantId, Map<String, Object> targetFilter) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (targetFilter != null && targetFilter.get("user_ids") instanceof Iterable<?> ids) {
            for (Object id : ids) {
                UUID parsed = toUuid(id);
                if (parsed != null) {
                    recipients.add(parsed);
                }
            }
        }
        if (recipients.isEmpty()) {
            for (Notification existing : repository.findByTenantId(tenantId)) {
                if (existing.getUserId() != null) {
                    recipients.add(existing.getUserId());
                }
            }
        }
        return recipients;
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Translates a due rule into an absolute timestamp. Supports relative forms such as
     * {@code "before_due:3d"}, {@code "in:12h"} or a bare {@code "30m"}; anything else
     * (e.g. {@code "annual"}) defaults to one day from now.
     */
    private static Instant resolveDueAt(String dueRule) {
        Instant now = Instant.now();
        if (dueRule == null || dueRule.isBlank()) {
            return now.plus(3, ChronoUnit.DAYS);
        }
        Matcher matcher = RELATIVE_RULE.matcher(dueRule.trim());
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2).toLowerCase()) {
                case "d" -> now.plus(Duration.ofDays(amount));
                case "h" -> now.plus(Duration.ofHours(amount));
                default -> now.plus(Duration.ofMinutes(amount));
            };
        }
        return now.plus(1, ChronoUnit.DAYS);
    }

    /**
     * Creates a reminder without going through TenantContext (used by event listeners that already have the tenant).
     */
    public Notification createReminderForTenant(UUID tenantId, UUID userId, String title, String body) {
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, NotificationType.REMINDER, NotificationChannel.IN_APP,
                NotificationStatus.SCHEDULED, title, body, Instant.now());
        return repository.save(notification);
    }
}
