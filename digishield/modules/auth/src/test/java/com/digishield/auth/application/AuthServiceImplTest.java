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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    /** Puts a JWT in the security context the way the resource server would. */
    private static void authenticateAs(UUID subject, String email) {
        var builder = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject.toString());
        if (email != null) {
            builder = builder.claim("email", email);
        }
        var jwt = builder.build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication
                        .JwtAuthenticationToken(jwt, List.of()));
    }

    @Mock
    private AppUserRepository userRepository;

    @Mock

    private ObjectProvider<AuditRecorder> auditRecorderProvider;


    @Mock

    private AuditRecorder auditRecorder;

    @Mock

    private com.digishield.auth.api.UserDirectory userDirectory;


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
    void currentUser_whenNoToken_fallsBackToFirstUserOfTenant() {
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
    void currentUser_whenTokenPresent_resolvesTheCallerNotWhoeverIsFirst() {
        UUID callerId = UUID.randomUUID();
        AppUser caller = new AppUser(
                callerId, TENANT_ID, "learner@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, callerId)).thenReturn(Optional.of(caller));
        authenticateAs(callerId, null);

        Optional<CurrentUser> result = authService.currentUser();

        // /me used to return the tenant's first row regardless of who asked, so
        // an admin listed ahead of the caller would be reported as the caller.
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(callerId);
        assertThat(result.get().role()).isEqualTo("LEARNER");
        verify(userRepository, never()).findAll();
    }

    @Test
    void currentUser_whenSubjectIsNotADirectoryId_fallsBackToTheEmailClaim() {
        UUID subject = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        AppUser caller = new AppUser(
                rowId, TENANT_ID, "boss@x.com", Role.SUPER_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, subject)).thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "boss@x.com"))
                .thenReturn(Optional.of(caller));
        authenticateAs(subject, "boss@x.com");

        Optional<CurrentUser> result = authService.currentUser();

        // Rows created when a tenant is bootstrapped get a generated id rather
        // than the token's subject, so email is the only thing linking them.
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(rowId);
    }

    @Test
    void currentUser_whenTokenIdentifiesNobody_returnsEmpty() {
        UUID subject = UUID.randomUUID();
        when(userRepository.findByTenantIdAndId(TENANT_ID, subject)).thenReturn(Optional.empty());
        authenticateAs(subject, "stranger@x.com");
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "stranger@x.com"))
                .thenReturn(Optional.empty());

        Optional<CurrentUser> result = authService.currentUser();

        // Better to say nothing than to hand back an arbitrary tenant member.
        assertThat(result).isEmpty();
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

    // ---- changing a role moves the account's group -------------------------

    @Test
    void changingARoleMovesTheAccountToTheNewGroupAndRevokesTheRest() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.ORG_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert(null, "learner", null, null));

        // Authorisation reads the token's groups. Writing the column and leaving
        // org_admin in place is a demotion that only looks like it happened.
        verify(userDirectory).setRole(eq("u@x.com"), eq("learner"),
                argThat(others -> others.contains("org_admin") && !others.contains("learner")));
    }

    @Test
    void anEditThatLeavesTheRoleAloneDoesNotTouchTheDirectory() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert(null, null, null, "en"));

        verify(userDirectory, never()).setRole(any(), any(), any());
    }

    @Test
    void theLegacyAdminSpellingIsNotTreatedAsARoleChange() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.TENANT_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert(null, "org_admin", null, null));

        // TENANT_ADMIN and ORG_ADMIN are the same group; moving the account would
        // mean revoking org_admin on the way to granting org_admin.
        verify(userDirectory, never()).setRole(any(), any(), any());
    }

    @Test
    void whenTheDirectoryCannotMoveTheAccountTheRoleIsNotWritten() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("identity provider is down"))
                .when(userDirectory).setRole(any(), any(), any());

        assertThatThrownBy(() -> authService.updateUser(userId, new UserUpsert(null, "analyst", null, null)))
                .isInstanceOf(IllegalStateException.class);

        // Otherwise the Users screen shows a role the tokens do not carry.
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void theDirectoryIsAddressedByTheEmailTheAccountWasCreatedWith() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, TENANT_ID, "old@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateUser(userId, new UserUpsert("new@x.com", "analyst", null, null));

        // Email is the account's username at the provider and editing the row does
        // not rename it, so the new address names nothing.
        verify(userDirectory).setRole(eq("old@x.com"), eq("analyst"), any());
    }

    // ---- creating a user provisions the sign-in account --------------------

    @Test
    void creatingAUserProvisionsTheSignInAccountInTheRolesGroup() {
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@x.com"))
                .thenReturn(Optional.empty());
        when(userDirectory.createUser("new@x.com", "analyst")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.createUser(new UserUpsert("new@x.com", "analyst", null, null));

        // The group is what the token carries, and the token is what authorises:
        // a row without one is a user who can sign in and reach nothing.
        verify(userDirectory).createUser("new@x.com", "analyst");
    }

    @Test
    void aUserWithNoRoleAskedForLandsInTheLearnerGroup() {
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@x.com"))
                .thenReturn(Optional.empty());
        when(userDirectory.createUser("new@x.com", "learner")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.createUser(new UserUpsert("new@x.com", null, null, null));

        // The row defaults to LEARNER; the directory has to be told the same thing
        // rather than left with no group at all.
        verify(userDirectory).createUser("new@x.com", "learner");
    }

    @Test
    void theRowIsKeyedByTheDirectorysSubjectWhenItKnowsOne() {
        UUID subject = UUID.randomUUID();
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@x.com"))
                .thenReturn(Optional.empty());
        when(userDirectory.createUser("new@x.com", "learner")).thenReturn(Optional.of(subject));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = authService.createUser(new UserUpsert("new@x.com", "learner", null, null));

        // currentUser() resolves the caller by the token's subject first. Matching
        // ids means the row is found straight away instead of via the email claim.
        assertThat(created.id()).isEqualTo(subject);
    }

    @Test
    void whenTheDirectoryRefusesNoRowIsWritten() {
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@x.com"))
                .thenReturn(Optional.empty());
        when(userDirectory.createUser("new@x.com", "learner"))
                .thenThrow(new IllegalStateException("identity provider is down"));

        assertThatThrownBy(() -> authService.createUser(new UserUpsert("new@x.com", "learner", null, null)))
                .isInstanceOf(IllegalStateException.class);

        // A row whose account was never created is a user the Users screen lists
        // and nobody can log in as — the failure has to reach the admin instead.
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void anOrgAdminCannotCreateASuperAdmin() {
        actingAs("ROLE_ORG_ADMIN");
        lenient().when(userRepository.findByTenantIdAndEmail(TENANT_ID, "boss@x.com"))
                .thenReturn(Optional.empty());

        // Creating a user now hands out real access, so the ceiling that guards an
        // edit has to guard a create — otherwise this is self-promotion in one call.
        assertThatThrownBy(() -> authService.createUser(
                new UserUpsert("boss@x.com", "super_admin", null, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("above your own");
        verify(userDirectory, never()).createUser(any(), any());
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void postingAnExistingEmailCannotPromoteThemPastTheCaller() {
        actingAs("ROLE_ORG_ADMIN");
        AppUser existing = new AppUser(
                UUID.randomUUID(), TENANT_ID, "u@x.com", Role.LEARNER, UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "u@x.com"))
                .thenReturn(Optional.of(existing));

        // POST on an address that already exists updates it. Without the same guard
        // PATCH has, it is the way around the check on the line above.
        assertThatThrownBy(() -> authService.createUser(
                new UserUpsert("u@x.com", "super_admin", null, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("above your own");
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void creatingAUserIsAuditedAsCritical() {
        when(auditRecorderProvider.getIfAvailable()).thenReturn(auditRecorder);
        when(userRepository.findByTenantIdAndEmail(TENANT_ID, "new@x.com"))
                .thenReturn(Optional.empty());
        when(userDirectory.createUser("new@x.com", "org_admin")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.createUser(new UserUpsert("new@x.com", "org_admin", null, null));

        verify(auditRecorder).record(eq("user.create"), eq("user:new@x.com"),
                eq(AuditRecorder.Severity.CRITICAL));
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
