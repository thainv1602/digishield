package com.digishield.learning.application;

import com.digishield.learning.api.BehaviourHistory;
import com.digishield.learning.domain.Badge;
import com.digishield.learning.domain.BadgeCatalog;
import com.digishield.learning.domain.BadgeCriteriaType;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.infrastructure.BadgeCatalogRepository;
import com.digishield.learning.infrastructure.BadgeRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BadgeAwarder}.
 * <p>
 * Nothing awarded a badge before this class existed — the catalogue stated its
 * conditions in prose that nothing could read — so every case covers behaviour
 * with no prior implementation.
 */
@ExtendWith(MockitoExtension.class)
class BadgeAwarderTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private BadgeCatalogRepository catalogRepository;
    @Mock
    private BadgeRepository badgeRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private GamificationProfileRepository profileRepository;
    @Mock
    private BehaviourHistory behaviourHistory;

    private BadgeAwarder awarder() {
        return new BadgeAwarder(catalogRepository, badgeRepository, enrollmentRepository,
                profileRepository, behaviourHistory);
    }

    @Test
    void awardsWhenTheMeasureReachesTheThreshold() {
        when(catalogRepository.findByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(
                badge("Người canh gác", BadgeCriteriaType.COURSES_COMPLETED, 3)));
        when(badgeRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(enrollmentRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of(
                enrollment(EnrollmentStatus.COMPLETED),
                enrollment(EnrollmentStatus.COMPLETED),
                enrollment(EnrollmentStatus.COMPLETED)));

        awarder().evaluate(TENANT_ID, USER_ID);

        ArgumentCaptor<Badge> saved = ArgumentCaptor.forClass(Badge.class);
        verify(badgeRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Người canh gác");
        assertThat(saved.getValue().isEarned()).isTrue();
    }

    @Test
    void countsOnlyCompletedCourses() {
        when(catalogRepository.findByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(
                badge("Người canh gác", BadgeCriteriaType.COURSES_COMPLETED, 3)));
        when(badgeRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(enrollmentRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of(
                enrollment(EnrollmentStatus.COMPLETED),
                enrollment(EnrollmentStatus.IN_PROGRESS),
                enrollment(EnrollmentStatus.ASSIGNED)));

        awarder().evaluate(TENANT_ID, USER_ID);

        // Being assigned three courses is not finishing three.
        verify(badgeRepository, never()).save(any());
    }

    @Test
    void awardsAConfirmedReporterFromWhatTriageConfirmed() {
        when(catalogRepository.findByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(
                badge("Thợ săn phishing", BadgeCriteriaType.REPORTS_CONFIRMED, 5)));
        when(badgeRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(behaviourHistory.confirmedReports(TENANT_ID, USER_ID)).thenReturn(5);

        awarder().evaluate(TENANT_ID, USER_ID);

        verify(badgeRepository).save(any());
    }

    @Test
    void neverAwardsTheSameBadgeTwice() {
        when(catalogRepository.findByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(
                badge("Người canh gác", BadgeCriteriaType.POINTS, 10)));
        when(badgeRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(List.of(
                new Badge(UUID.randomUUID(), TENANT_ID, USER_ID, "Người canh gác",
                        "đã có", "shield", true, Instant.now())));

        awarder().evaluate(TENANT_ID, USER_ID);

        // Points keep rising, and this runs on every scoring event; without the
        // check the holder would collect a duplicate each time they earned one.
        verify(badgeRepository, never()).save(any());
    }

    @Test
    void skipsCatalogueEntriesThatDefineNoCriteria() {
        when(catalogRepository.findByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(
                new BadgeCatalog(UUID.randomUUID(), TENANT_ID, "Phản ứng nhanh",
                        "Báo cáo trong 60 giây", "zap")));

        awarder().evaluate(TENANT_ID, USER_ID);

        // A badge nobody wrote a rule for is one nothing awards — which is what
        // every row looked like before criteria existed. Guessing a rule would
        // put a claim on screen the catalogue never made.
        verify(badgeRepository, never()).save(any());
    }

    private static BadgeCatalog badge(String name, BadgeCriteriaType type, int threshold) {
        return new BadgeCatalog(UUID.randomUUID(), TENANT_ID, name, "mô tả", "shield",
                type, threshold);
    }

    private static Enrollment enrollment(EnrollmentStatus status) {
        return new Enrollment(UUID.randomUUID(), TENANT_ID, USER_ID, UUID.randomUUID(), status, null);
    }
}
