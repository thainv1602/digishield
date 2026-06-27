package com.digishield.auth.application;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.CurrentUser;
import com.digishield.auth.api.TokenPair;
import com.digishield.auth.api.UserView;
import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
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

    /** Static dev tokens (no real credentials are validated in the dev profile). */
    private static final String DEV_ACCESS_TOKEN = "dev-access-token";
    private static final String DEV_REFRESH_TOKEN = "dev-refresh-token";
    private static final long DEV_EXPIRES_IN_SECONDS = 3600L;

    private final AppUserRepository userRepository;

    public AuthServiceImpl(AppUserRepository userRepository) {
        this.userRepository = userRepository;
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
                .map(u -> UserView.of(
                        u.getId(),
                        u.getTenantId(),
                        u.getEmail(),
                        u.getName(),
                        u.getRole() != null ? u.getRole().wireName() : null,
                        u.getStatus() != null ? u.getStatus().name().toLowerCase() : null,
                        u.getDepartment(),
                        u.getRiskScore()))
                .toList();
    }

    @Override
    public TokenPair login(String email, String password, String roleHint) {
        // Dev: no credential validation; return static demo tokens.
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
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
}
