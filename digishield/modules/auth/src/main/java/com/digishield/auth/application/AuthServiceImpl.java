package com.digishield.auth.application;

import com.digishield.auth.api.AuthProvider;
import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.api.ProfileView;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import com.digishield.auth.api.ImportResult;
import com.digishield.auth.api.MfaSetupView;
import com.digishield.auth.api.TokenPair;
import com.digishield.auth.api.UserDirectory;
import com.digishield.auth.api.UserUpsert;
import com.digishield.auth.api.UserView;
import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.shared.tenantcontext.AuditRecorder;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link AuthService}.
 * <p>
 * Skeleton/dev: the "current user" is inferred from the current tenant (and,
 * optionally, a requested demo role). When integrating the resource-server,
 * this will be replaced by reading the subject/claim from the JWT.
 */
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AppUserRepository userRepository;
    /** Optional: absent in slices that do not wire the application shell. */
    private final ObjectProvider<AuditRecorder> auditRecorder;
    private final AuthProvider authProvider;
    private final UserDirectory userDirectory;

    public AuthServiceImpl(AppUserRepository userRepository, AuthProvider authProvider,
                           UserDirectory userDirectory,
                           ObjectProvider<AuditRecorder> auditRecorder) {
        this.userRepository = userRepository;
        this.authProvider = authProvider;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    /** Records an auditable action, if an audit sink is wired (absent in slices). */
    private void audit(String action, String target, AuditRecorder.Severity severity) {
        AuditRecorder recorder = auditRecorder.getIfAvailable();
        if (recorder != null) {
            recorder.record(action, target, severity);
        }
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        String rawTenantId = TenantContext.get();
        if (rawTenantId == null || rawTenantId.isBlank()) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.requireUuid();

        Optional<Jwt> jwt = currentJwt();
        if (jwt.isEmpty()) {
            // No token at all: the dev stub provider issues static tokens and
            // carries no identity, so there is nobody to resolve. The first
            // tenant user stands in, which is only meaningful because a dev
            // database has one.
            return userRepository.findAll().stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .findFirst()
                    .map(this::toView);
        }

        // Identify the caller, rather than returning whoever happens to be
        // first. Two identifiers are tried because directory rows are not all
        // created the same way: SCIM and self-service rows carry the token's
        // subject as their id, while rows inserted when a tenant is bootstrapped
        // get a generated id and are only recognisable by email.
        UUID sub = subjectUuid(jwt.get());
        if (sub != null) {
            Optional<CurrentUser> bySubject =
                    userRepository.findByTenantIdAndId(tenantId, sub).map(this::toView);
            if (bySubject.isPresent()) {
                return bySubject;
            }
        }
        String email = jwt.get().getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return userRepository.findByTenantIdAndEmail(tenantId, email).map(this::toView);
        }
        return Optional.empty();
    }

    @Override
    public ProfileView getMyProfile() {
        UUID tenantId = TenantContext.requireUuid();
        Optional<Jwt> jwt = currentJwt();
        if (jwt.isEmpty()) {
            // Non-JWT (dev): fall back to the demo current user.
            return currentUser()
                    .map(u -> new ProfileView(u.id(), u.tenantId(), u.email(), u.role(), u.name(), null))
                    .orElse(null);
        }
        UUID sub = subjectUuid(jwt.get());
        Role role = roleFromJwt(jwt.get());
        if (sub == null) {
            return new ProfileView(null, tenantId, null, role.wireName(), null, null);
        }
        return userRepository.findByTenantIdAndId(tenantId, sub)
                .map(this::toProfileView)
                .orElseGet(() -> new ProfileView(sub, tenantId, null, role.wireName(), null, null));
    }

    @Override
    @Transactional
    public ProfileView updateMyProfile(String name, String locale, String email) {
        UUID tenantId = TenantContext.requireUuid();
        Jwt jwt = currentJwt().orElseThrow(() -> new IllegalStateException("No authenticated JWT user"));
        UUID sub = subjectUuid(jwt);
        if (sub == null) {
            throw new IllegalStateException("JWT subject is not a UUID");
        }
        AppUser user = userRepository.findByTenantIdAndId(tenantId, sub).orElse(null);
        if (user == null) {
            // JIT-provision on first save; the frontend supplies the ID-token email.
            String em = (email != null && !email.isBlank()) ? email.trim() : (sub + "@cognito.local");
            user = new AppUser(sub, tenantId, em, roleFromJwt(jwt), UserStatus.ACTIVE);
        } else if (email != null && !email.isBlank()) {
            user.setEmail(email.trim());
        }
        if (name != null) {
            String n = name.trim();
            user.setName(n.isEmpty() ? null : n);
        }
        if (locale != null && !locale.isBlank()) {
            user.setLocale(locale.trim());
        }
        return toProfileView(userRepository.save(user));
    }

    private Optional<Jwt> currentJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    private static UUID subjectUuid(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static Role roleFromJwt(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            for (Role r : List.of(Role.SUPER_ADMIN, Role.ORG_ADMIN, Role.MANAGER,
                    Role.CONTENT_EDITOR, Role.ANALYST, Role.LEARNER)) {
                if (groups.contains(r.wireName())) {
                    return r;
                }
            }
        }
        return Role.LEARNER;
    }

    private ProfileView toProfileView(AppUser u) {
        return new ProfileView(u.getId(), u.getTenantId(), u.getEmail(),
                u.getRole() != null ? u.getRole().wireName() : null, u.getName(), u.getLocale());
    }

    @Override
    public Optional<CurrentUser> findById(UUID userId) {
        UUID tenantId = TenantContext.requireUuid();
        return userRepository.findByTenantIdAndId(tenantId, userId).map(this::toView);
    }

    @Override
    public List<UserView> listUsers() {
        UUID tenantId = TenantContext.requireUuid();
        return userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .sorted(Comparator.comparing(AppUser::getEmail))
                .map(this::toUserView)
                .toList();
    }

    @Override
    public UserView getUser(UUID userId) {
        UUID tenantId = TenantContext.requireUuid();
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .map(this::toUserView)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    @Override
    @Transactional
    public UserView createUser(UserUpsert input) {
        UUID tenantId = TenantContext.requireUuid();
        String email = input.email();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        AppUser existing = userRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);
        if (existing != null) {
            // Idempotent in dev: update the existing user rather than failing.
            assertMayAdminister(existing, input);
            applyChanges(existing, input);
            return toUserView(userRepository.save(existing));
        }
        Role role = input.role() != null ? Role.fromWireName(input.role()) : Role.LEARNER;
        // Creating a user now hands out a real sign-in account in that role's group,
        // so the same ceiling that guards an edit has to guard a create: without it
        // an org admin could mint themselves a super admin by adding one.
        assertMayGrant(role);
        // Provision the sign-in account before the row exists. The other order leaves
        // a user who shows up on the Users screen and can never log in; this one, at
        // worst, leaves an account nobody is pointing at, and the directory adopts it
        // on the retry.
        UUID subject = userDirectory.createUser(email, role.wireName()).orElse(null);
        AppUser user = new AppUser(
                // The directory's subject is what the token will carry, so the row is
                // keyed by it when we know it — that is the id currentUser() looks up
                // first, before falling back to matching on email.
                subject != null ? subject : UUID.randomUUID(),
                tenantId,
                email,
                role,
                UserStatus.PENDING,
                deriveName(email),
                null,
                0);
        user.setDepartmentId(input.departmentId());
        user.setLocale(input.locale() != null ? input.locale() : "vi");
        UserView saved = toUserView(userRepository.save(user));
        audit("user.create", "user:" + email, AuditRecorder.Severity.CRITICAL);
        return saved;
    }

    @Override
    @Transactional
    public UserView updateUser(UUID userId, UserUpsert changes) {
        UUID tenantId = TenantContext.requireUuid();
        AppUser user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        assertMayAdminister(user, changes);
        String before = groupOf(user.getRole());
        // The account was created under the address the row held at the time, and
        // that address is its username at the provider. Editing the row's email
        // does not rename it, so the lookup has to use the old one.
        String username = user.getEmail();
        applyChanges(user, changes);
        String after = groupOf(user.getRole());
        boolean roleChanged = !Objects.equals(before, after);
        if (roleChanged) {
            // Before the row is written, for the same reason a create provisions
            // first: authority lives in the token's groups, so a row saved without
            // the group move is a role change that only looks like it happened.
            userDirectory.setRole(username, after, otherGroups(user.getRole()));
        }
        UserView saved = toUserView(userRepository.save(user));
        // A role change decides what someone may do; it is the entry an
        // investigation looks for, so it is called out from a plain edit.
        if (roleChanged) {
            audit("user.role_change", "user:" + userId, AuditRecorder.Severity.CRITICAL);
        } else {
            audit("user.update", "user:" + userId, AuditRecorder.Severity.SENSITIVE);
        }
        return saved;
    }

    /** The provider's group name for a role, or {@code null} for no role at all. */
    private static String groupOf(Role role) {
        return role != null ? role.wireName() : null;
    }

    /**
     * Every group that is a role other than this one — what a move has to revoke.
     *
     * <p>By group name, not by enum constant: {@code TENANT_ADMIN} is the legacy
     * spelling of {@code ORG_ADMIN} and shares its group, so revoking "the others"
     * by constant would revoke the very group being granted.
     */
    private static Set<String> otherGroups(Role role) {
        String keep = groupOf(role);
        Set<String> others = new LinkedHashSet<>();
        for (Role candidate : Role.values()) {
            String group = candidate.wireName();
            if (!group.equals(keep)) {
                others.add(group);
            }
        }
        return others;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every accepted row goes through {@link #createUser}, so a bulk import
     * provisions that many sign-in accounts and sends that many invitations —
     * enough to reach an identity provider's rate limit or daily mail cap. When
     * one row fails the transaction rolls back and no application rows are
     * written, but the accounts created before it stay; re-running the import
     * adopts them rather than duplicating them.
     */
    @Override
    @Transactional
    public ImportResult importUsers(List<UserUpsert> users) {
        int accepted = 0;
        if (users != null) {
            for (UserUpsert input : users) {
                if (input == null || input.email() == null || input.email().isBlank()) {
                    continue;
                }
                createUser(input);
                accepted++;
            }
        }
        String jobId = "import-" + UUID.randomUUID();
        log.info("[auth] Bulk import accepted {} users (job {})", accepted, jobId);
        return new ImportResult(jobId, accepted);
    }

    @Override
    public TokenPair login(String email, String password) {
        try {
            TokenPair tokens = authProvider.login(email, password);
            audit("user.login", "user:" + email, AuditRecorder.Severity.STANDARD);
            return tokens;
        } catch (RuntimeException e) {
            // No tenant is knowable here: the credential check happens in the IdP,
            // which tells us nothing on failure, and resolving email -> tenant would
            // mean querying app_user across tenants for unauthenticated input. The
            // attempt is recorded in the application log instead — structured, with
            // the request id — rather than not at all.
            LOG.warn("Failed login for {}: {}", email, e.toString());
            throw e;
        }
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        return authProvider.refresh(refreshToken);
    }

    @Override
    public TokenPair ssoCallback(String org, String assertion) {
        return authProvider.ssoCallback(org, assertion);
    }

    @Override
    public void forgotPassword(String email) {
        authProvider.forgotPassword(email);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        authProvider.resetPassword(token, newPassword);
    }

    @Override
    public MfaSetupView mfaSetup() {
        String account = currentUser().map(CurrentUser::email).orElse(null);
        return authProvider.mfaSetup(account);
    }

    @Override
    public List<String> mfaVerify(String code) {
        return authProvider.mfaVerify(code);
    }

    @Override
    public TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice) {
        return authProvider.mfaChallenge(mfaToken, code, trustDevice);
    }

    @Override
    public boolean hasRole(String role) {
        // Deliberately the token, not app_user.role. Reading the database made this
        // a privilege-escalation path waiting for its first caller: an org admin can
        // edit that column, so anyone writing hasRole("super_admin") would have been
        // trusting a value the attacker controls. Authorisation lives in the signed
        // token, the same source @PreAuthorize uses.
        if (role == null || role.isBlank()) {
            return false;
        }
        String wanted = "ROLE_" + role.trim().toUpperCase(Locale.ROOT);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> wanted.equals(a.getAuthority()));
    }

    /**
     * The caller's authority level, taken from the signed token.
     *
     * <p>Empty when there is no authenticated role at all — the {@code dev} profile
     * permits everything and sets no authentication, and the guards below stay out
     * of the way there rather than breaking local work.
     */
    private Optional<Role> callerRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> {
                    try {
                        return Role.valueOf(a.substring("ROLE_".length()));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(r -> r != null)
                .max(Comparator.comparingInt(Role::rank));
    }

    /**
     * Refuses edits that reach above the caller.
     *
     * <p>Two separate things: you may not administer an account that outranks you,
     * and you may not hand out authority you do not hold. Without the first, an org
     * admin could rewrite the platform operator's record — including the email that
     * simulation mail is delivered to. Without the second, the Users screen could be
     * made to disagree with the tokens that actually grant access.
     */
    private void assertMayAdminister(AppUser target, UserUpsert changes) {
        Optional<Role> caller = callerRole();
        if (caller.isEmpty()) {
            return;
        }
        Role actor = caller.get();
        if (!actor.outranksOrEquals(target.getRole())) {
            throw new AccessDeniedException(
                    "Cannot modify a user whose role outranks yours");
        }
        if (changes != null && changes.role() != null && !changes.role().isBlank()) {
            assertMayGrant(Role.fromWireName(changes.role()));
        }
    }

    /**
     * Refuses handing out authority the caller does not hold.
     *
     * <p>Empty caller role means no authenticated role at all — the {@code dev}
     * profile permits everything and sets no authentication.
     */
    private void assertMayGrant(Role wanted) {
        Optional<Role> caller = callerRole();
        if (caller.isPresent() && !caller.get().outranksOrEquals(wanted)) {
            throw new AccessDeniedException("Cannot grant a role above your own");
        }
    }

    private CurrentUser toView(AppUser user) {
        return new CurrentUser(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getName()
        );
    }

    private UserView toUserView(AppUser u) {
        return UserView.of(
                u.getId(),
                u.getTenantId(),
                u.getDepartmentId(),
                u.getEmail(),
                u.getName(),
                u.getRole() != null ? u.getRole().wireName() : null,
                u.getStatus() != null ? u.getStatus().name().toLowerCase() : null,
                u.getDepartment(),
                u.getLocale(),
                u.getPhone(),
                u.getRiskScore());
    }

    private void applyChanges(AppUser user, UserUpsert changes) {
        if (changes == null) {
            return;
        }
        if (changes.email() != null && !changes.email().isBlank()) {
            user.setEmail(changes.email());
        }
        if (changes.role() != null && !changes.role().isBlank()) {
            user.setRole(Role.fromWireName(changes.role()));
        }
        if (changes.departmentId() != null) {
            user.setDepartmentId(changes.departmentId());
        }
        if (changes.locale() != null && !changes.locale().isBlank()) {
            user.setLocale(changes.locale());
        }
    }

    private static String deriveName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

}
