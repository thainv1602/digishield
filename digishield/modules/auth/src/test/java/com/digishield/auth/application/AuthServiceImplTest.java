package com.digishield.auth.application;

import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void currentUser_whenTenantHasUser_returnsFirstUserOfTenant() {
        // Arrange
        AppUser otherTenantUser = new AppUser(
                UUID.randomUUID(), OTHER_TENANT_ID, "other@x.com", Role.LEARNER, UserStatus.ACTIVE);
        UUID expectedId = UUID.randomUUID();
        AppUser ourUser = new AppUser(
                expectedId, TENANT_ID, "admin@x.com", Role.TENANT_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(otherTenantUser, ourUser));

        // Act
        Optional<CurrentUser> result = authService.currentUser();

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(expectedId);
        assertThat(result.get().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.get().email()).isEqualTo("admin@x.com");
        assertThat(result.get().role()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    void currentUser_whenTenantNotSet_returnsEmpty() {
        // Arrange
        TenantContext.clear();

        // Act
        Optional<CurrentUser> result = authService.currentUser();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void currentUser_whenNoUserForTenant_returnsEmpty() {
        // Arrange
        AppUser otherTenantUser = new AppUser(
                UUID.randomUUID(), OTHER_TENANT_ID, "other@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(otherTenantUser));

        // Act
        Optional<CurrentUser> result = authService.currentUser();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenUserExists_returnsViewScopedToTenant() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.MANAGER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));

        // Act
        Optional<CurrentUser> result = authService.findById(userId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().role()).isEqualTo("MANAGER");
    }

    @Test
    void hasRole_whenCurrentUserHasMatchingRole_returnsTrueCaseInsensitive() {
        // Arrange
        AppUser admin = new AppUser(
                UUID.randomUUID(), TENANT_ID, "admin@x.com", Role.TENANT_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(admin));

        // Act + Assert (case-insensitive comparison in service)
        assertThat(authService.hasRole("tenant_admin")).isTrue();
    }

    @Test
    void hasRole_whenCurrentUserHasDifferentRole_returnsFalse() {
        // Arrange
        AppUser learner = new AppUser(
                UUID.randomUUID(), TENANT_ID, "learner@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(learner));

        // Act + Assert
        assertThat(authService.hasRole("TENANT_ADMIN")).isFalse();
    }

    @Test
    void hasRole_whenNoCurrentUser_returnsFalse() {
        // Arrange: no tenant -> currentUser() short-circuits to empty, repo never queried
        lenient().when(userRepository.findAll()).thenReturn(List.of());
        TenantContext.clear();

        // Act + Assert
        assertThat(authService.hasRole("ANY")).isFalse();
    }
}
