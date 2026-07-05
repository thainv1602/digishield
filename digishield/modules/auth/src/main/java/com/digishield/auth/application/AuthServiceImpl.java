package com.digishield.auth.application;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.CurrentUser;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
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

    /** Static dev tokens (no real credentials are validated in the dev profile). */
    private static final String DEV_ACCESS_TOKEN = "dev-access-token";
    private static final String DEV_REFRESH_TOKEN = "dev-refresh-token";
    private static final long DEV_EXPIRES_IN_SECONDS = 3600L;

    /** RFC 4648 base32 alphabet used for the (dev) TOTP secret. */
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int TOTP_SECRET_LENGTH = 32;
    private static final int RECOVERY_CODE_COUNT = 8;

    private final SecureRandom random = new SecureRandom();

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
        // Dev: no credential validation; return static demo tokens.
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
    }

    @Override
    public TokenPair ssoCallback(String org, String assertion) {
        // Dev: accept any org/assertion and return demo tokens (no SAML/OAuth verification).
        return new TokenPair(DEV_ACCESS_TOKEN, DEV_REFRESH_TOKEN, DEV_EXPIRES_IN_SECONDS);
    }

    @Override
    public void forgotPassword(String email) {
        // Dev: no-op (no email sent); never reveal whether the account exists.
        log.info("[auth] Password reset requested (dev no-op)");
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        // Dev: accept the token and new password without verification.
        log.info("[auth] Password reset completed (dev no-op)");
    }

    @Override
    public MfaSetupView mfaSetup() {
        String secret = generateBase32Secret();
        String account = currentUser().map(CurrentUser::email).orElse("user@digishield.vn");
        String label = URLEncoder.encode("DigiShield:" + account, StandardCharsets.UTF_8);
        String otpauthUrl = "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=DigiShield&algorithm=SHA1&digits=6&period=30";
        return new MfaSetupView(secret, otpauthUrl, sampleQrSvg(otpauthUrl));
    }

    @Override
    public List<String> mfaVerify(String code) {
        // Dev: accept any code and return a fresh set of one-time recovery codes.
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomRecoveryCode());
        }
        return codes;
    }

    @Override
    public TokenPair mfaChallenge(String mfaToken, String code, boolean trustDevice) {
        // Dev: accept any code for the supplied temporary token and return demo tokens.
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

    private String generateBase32Secret() {
        StringBuilder sb = new StringBuilder(TOTP_SECRET_LENGTH);
        for (int i = 0; i < TOTP_SECRET_LENGTH; i++) {
            sb.append(BASE32[random.nextInt(BASE32.length)]);
        }
        return sb.toString();
    }

    private String randomRecoveryCode() {
        // Format: XXXX-XXXX using the base32 alphabet.
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(BASE32[random.nextInt(BASE32.length)]);
        }
        return sb.toString();
    }

    private static String sampleQrSvg(String otpauthUrl) {
        // Dev placeholder: a tiny SVG that embeds the otpauth URL as a title; the
        // frontend can render its own QR code from the otpauth_url field.
        String safe = otpauthUrl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"160\" height=\"160\" "
                + "viewBox=\"0 0 160 160\"><title>" + safe + "</title>"
                + "<rect width=\"160\" height=\"160\" fill=\"#ffffff\"/>"
                + "<rect x=\"16\" y=\"16\" width=\"128\" height=\"128\" fill=\"none\" "
                + "stroke=\"#000000\" stroke-width=\"8\"/>"
                + "<text x=\"80\" y=\"86\" text-anchor=\"middle\" font-family=\"monospace\" "
                + "font-size=\"12\" fill=\"#000000\">QR</text></svg>";
    }
}
