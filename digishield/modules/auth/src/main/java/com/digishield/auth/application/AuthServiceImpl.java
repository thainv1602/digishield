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
import com.digishield.auth.api.UserUpsert;
import com.digishield.auth.api.UserView;
import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AppUserRepository userRepository;
    private final AuthProvider authProvider;

    public AuthServiceImpl(AppUserRepository userRepository, AuthProvider authProvider) {
        this.userRepository = userRepository;
        this.authProvider = authProvider;
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        return currentUser(null);
    }

    @Override
    public Optional<CurrentUser> currentUser(String roleHint) {
        String rawTenantId = TenantContext.get();
        if (rawTenantId == null || rawTenantId.isBlank()) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.requireUuid();
        List<AppUser> tenantUsers = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .toList();

        // If a demo role is requested (dev: X-Demo-Role), prefer that persona.
        if (roleHint != null && !roleHint.isBlank()) {
            Role wanted = Role.fromWireName(roleHint);
            Optional<AppUser> match = tenantUsers.stream()
                    .filter(u -> u.getRole() == wanted)
                    .findFirst();
            if (match.isPresent()) {
                return match.map(this::toView);
            }
        }
        return tenantUsers.stream().findFirst().map(this::toView);
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
            applyChanges(existing, input);
            return toUserView(userRepository.save(existing));
        }
        AppUser user = new AppUser(
                UUID.randomUUID(),
                tenantId,
                email,
                input.role() != null ? Role.fromWireName(input.role()) : Role.LEARNER,
                UserStatus.PENDING,
                deriveName(email),
                null,
                0);
        user.setDepartmentId(input.departmentId());
        user.setLocale(input.locale() != null ? input.locale() : "vi");
        return toUserView(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserView updateUser(UUID userId, UserUpsert changes) {
        UUID tenantId = TenantContext.requireUuid();
        AppUser user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        applyChanges(user, changes);
        return toUserView(userRepository.save(user));
    }

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
    public TokenPair login(String email, String password, String roleHint) {
        return authProvider.login(email, password);
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
        return currentUser()
                .map(u -> u.role() != null && u.role().equalsIgnoreCase(role))
                .orElse(false);
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
