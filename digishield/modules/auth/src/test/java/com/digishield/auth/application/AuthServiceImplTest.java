package com.digishield.auth.application;

import com.digishield.auth.api.UserUpsert;
import com.digishield.shared.tenantcontext.AuditRecorder;
import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock

    private ObjectProvider<AuditRecorder> auditRecorderProvider;


    @Mock

    private AuditRecorder auditRecorder;


    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Authorities are thread-local; a leaked one would silently authorise the
        // next test in the same worker.
        SecurityContextHolder.clearContext();
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

    // hasRole() used to read app_user.role. A tenant admin can edit that column,
    // so the answer was only as trustworthy as the least-privileged person able to
    // change it. These now pin the token as the source.

    @Test
    void hasRole_matchesAGrantedAuthorityCaseInsensitively() {
        actingAs("ROLE_ORG_ADMIN");

        assertThat(authService.hasRole("org_admin")).isTrue();
        assertThat(authService.hasRole("ORG_ADMIN")).isTrue();
    }

    @Test
    void hasRole_isFalseForARoleTheTokenDoesNotCarry() {
        actingAs("ROLE_LEARNER");

        assertThat(authService.hasRole("ORG_ADMIN")).isFalse();
    }

    @Test
    void hasRole_ignoresTheDatabaseRowEntirely() {
        // The row says super admin; the token says learner. The token wins.
        lenient().when(userRepository.findAll()).thenReturn(List.of(
                new AppUser(UUID.randomUUID(), TENANT_ID, "x@x.com", Role.SUPER_ADMIN, UserStatus.ACTIVE)));
        actingAs("ROLE_LEARNER");

        assertThat(authService.hasRole("super_admin")).isFalse();
    }

    @Test
    void hasRole_whenUnauthenticated_returnsFalse() {
        SecurityContextHolder.clearContext();

        assertThat(authService.hasRole("ANY")).isFalse();
    }

    // ---- audit trail -------------------------------------------------------

    @Test
    void changingSomeonesRoleIsAuditedAsCritical() {
        when(auditRecorderProvider.getIfAvailable()).thenReturn(auditRecorder);
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert(null, "analyst", null, null));

        // Who can do what changed — this is the entry an investigation looks for.
        verify(auditRecorder).record(eq("user.role_change"), eq("user:" + userId),
                eq(AuditRecorder.Severity.CRITICAL));
    }

    @Test
    void anEditThatLeavesTheRoleAloneIsAuditedAsSensitive() {
        when(auditRecorderProvider.getIfAvailable()).thenReturn(auditRecorder);
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert(null, "learner", null, null));

        verify(auditRecorder).record(eq("user.update"), eq("user:" + userId),
                eq(AuditRecorder.Severity.SENSITIVE));
    }

    @Test
    void auditingIsSkippedWhenNoSinkIsWired() {
        when(auditRecorderProvider.getIfAvailable()).thenReturn(null);
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // A slice without the application shell must still be able to edit a user.
        authService.updateUser(userId, new UserUpsert(null, "analyst", null, null));
    }

    // ---- privilege guards on user administration -------------------------

    private void actingAs(String... authorities) {
        var token = new TestingAuthenticationToken("actor", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void existingUser(UUID id, Role role) {
        AppUser user = new AppUser(id, TENANT_ID, "u@x.com", role, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(java.util.Optional.of(user));
        lenient().when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void anOrgAdminCannotEditASuperAdminsAccount() {
        actingAs("ROLE_ORG_ADMIN");
        UUID victim = UUID.randomUUID();
        existingUser(victim, Role.SUPER_ADMIN);

        // Without this the platform operator's record — including the email that
        // simulation mail is delivered to — is editable by any tenant admin.
        assertThatThrownBy(() -> authService.updateUser(victim, new UserUpsert(null, "learner", null, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("outranks");
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void anOrgAdminCannotPromoteAnyoneToSuperAdmin() {
        actingAs("ROLE_ORG_ADMIN");
        UUID target = UUID.randomUUID();
        existingUser(target, Role.LEARNER);

        assertThatThrownBy(() -> authService.updateUser(target, new UserUpsert(null, "super_admin", null, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("above your own");
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void anOrgAdminMayStillAdministerOrdinaryUsers() {
        actingAs("ROLE_ORG_ADMIN");
        UUID target = UUID.randomUUID();
        existingUser(target, Role.LEARNER);

        authService.updateUser(target, new UserUpsert(null, "analyst", null, null));

        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void aSuperAdminMayAdministerAnyone() {
        actingAs("ROLE_SUPER_ADMIN");
        UUID target = UUID.randomUUID();
        existingUser(target, Role.SUPER_ADMIN);

        authService.updateUser(target, new UserUpsert(null, "org_admin", null, null));

        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void hasRoleReadsTheTokenNotTheDatabaseRow() {
        // The database column is editable by a tenant admin; the token is not.
        // Anyone reaching for hasRole() must get the trustworthy answer.
        actingAs("ROLE_ANALYST");

        assertThat(authService.hasRole("analyst")).isTrue();
        assertThat(authService.hasRole("super_admin")).isFalse();
    }
}
