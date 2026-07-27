package com.digishield.learning.application;

import com.digishield.learning.api.LearnerDirectory;
import com.digishield.learning.domain.GamificationProfile;
import com.digishield.learning.domain.PointAction;
import com.digishield.learning.domain.PointRule;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import com.digishield.learning.infrastructure.PointRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PointsAwarder}.
 * <p>
 * Nothing awarded points before this class existed, so every case here covers
 * behaviour that had no implementation at all rather than a change to one.
 */
@ExtendWith(MockitoExtension.class)
class PointsAwarderTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private GamificationProfileRepository profileRepository;
    @Mock
    private PointRuleRepository pointRuleRepository;
    @Mock
    private ObjectProvider<LearnerDirectory> directory;
    @Mock
    private LearnerDirectory learnerDirectory;

    private PointsAwarder awarder() {
        return new PointsAwarder(profileRepository, pointRuleRepository, directory);
    }

    @Test
    void appliesTheBuiltInDefaultWhenTheTenantHasNoRule() {
        when(pointRuleRepository.findByTenantIdAndAction(TENANT_ID, "report_confirmed"))
                .thenReturn(Optional.empty());
        when(profileRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(profile(10)));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int total = awarder().award(TENANT_ID, USER_ID, PointAction.REPORT_CONFIRMED);

        // The seeded rules were never applied, so a tenant with an empty
        // point_rule table must still score — otherwise this stays at zero
        // exactly as it did before.
        assertThat(total).isEqualTo(60);
    }

    @Test
    void aTenantsOwnRuleOverridesTheDefault() {
        when(pointRuleRepository.findByTenantIdAndAction(TENANT_ID, "report_confirmed"))
                .thenReturn(Optional.of(new PointRule(
                        UUID.randomUUID(), TENANT_ID, "report_confirmed", "Báo cáo đúng", 5)));
        when(profileRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(profile(0)));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(awarder().award(TENANT_ID, USER_ID, PointAction.REPORT_CONFIRMED)).isEqualTo(5);
    }

    @Test
    void clickingCostsPointsAndCanGoNegative() {
        when(pointRuleRepository.findByTenantIdAndAction(TENANT_ID, "simulation_clicked"))
                .thenReturn(Optional.empty());
        when(profileRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(profile(0)));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Not clamped at zero: a score that stops falling stops distinguishing
        // one click from five.
        assertThat(awarder().award(TENANT_ID, USER_ID, PointAction.SIMULATION_CLICKED)).isEqualTo(-5);
    }

    @Test
    void createsAProfileNamedFromTheDirectoryOnFirstScore() {
        when(pointRuleRepository.findByTenantIdAndAction(any(), any())).thenReturn(Optional.empty());
        when(profileRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(directory.getIfAvailable()).thenReturn(learnerDirectory);
        when(learnerDirectory.find(USER_ID))
                .thenReturn(Optional.of(new LearnerDirectory.Learner("Nguyễn Văn A", "Kế toán")));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        awarder().award(TENANT_ID, USER_ID, PointAction.LESSON_COMPLETED);

        ArgumentCaptor<GamificationProfile> saved = ArgumentCaptor.forClass(GamificationProfile.class);
        verify(profileRepository).save(saved.capture());
        // A leaderboard has to show a person, not an id.
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getValue().getDepartment()).isEqualTo("Kế toán");
        assertThat(saved.getValue().getPoints()).isEqualTo(10);
    }

    @Test
    void stillScoresWhenNoDirectoryIsWired() {
        when(pointRuleRepository.findByTenantIdAndAction(any(), any())).thenReturn(Optional.empty());
        when(profileRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(directory.getIfAvailable()).thenReturn(null);
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // A missing name is worth less than a missing score.
        assertThat(awarder().award(TENANT_ID, USER_ID, PointAction.QUIZ_PASSED)).isEqualTo(24);
    }

    private static GamificationProfile profile(int points) {
        return new GamificationProfile(UUID.randomUUID(), TENANT_ID, USER_ID, "A", "IT", points);
    }
}
