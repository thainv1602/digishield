package com.digishield.auth.application;

import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import com.digishield.shared.tenantcontext.DemoUsers;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only seeder: creates one demo {@link AppUser} per role under the fixed
 * {@link DemoTenants#DEMO_TENANT_ID} so the frontend can exercise login,
 * {@code /auth/me} and the Users screen.
 *
 * <p>Runs early ({@link Order}) so other modules' seeders can rely on the demo
 * users existing. Only active under the {@code dev} profile.
 */
@Component
@Profile("dev")
@Order(0)
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AppUserRepository userRepository;

    public DevDataSeeder(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        UUID tenant = DemoTenants.DEMO_TENANT_ID;
        List<AppUser> demoUsers = List.of(
                user(DemoUsers.SUPER_ADMIN, tenant,
                        "superadmin@digishield.vn", "Super Admin",
                        Role.SUPER_ADMIN, "Platform", 12),
                user(DemoUsers.ORG_ADMIN, tenant,
                        "admin@coquan.gov.vn", "Nguyễn Tuấn",
                        Role.ORG_ADMIN, "Ban Giám đốc", 18),
                user(DemoUsers.MANAGER, tenant,
                        "manager@coquan.gov.vn", "Trần Thị Bình",
                        Role.MANAGER, "Kinh doanh", 64),
                user(DemoUsers.CONTENT_EDITOR, tenant,
                        "editor@coquan.gov.vn", "Lê Văn Cường",
                        Role.CONTENT_EDITOR, "Đào tạo", 27),
                user(DemoUsers.ANALYST, tenant,
                        "analyst@coquan.gov.vn", "Phạm Thu Hà",
                        Role.ANALYST, "An ninh mạng", 22),
                user(DemoUsers.LEARNER, tenant,
                        "learner@coquan.gov.vn", "Nguyễn Minh An",
                        Role.LEARNER, "Kế toán", 78));

        // Demo phones (E.164), unique per user, so SMS delivery is exercisable
        // in dev/prod-like.
        for (int i = 0; i < demoUsers.size(); i++) {
            demoUsers.get(i).setPhone("+8490123456" + (i + 1));
        }
        userRepository.saveAll(demoUsers);
        LOG.info("[dev] Seeded {} demo users for tenant {}", demoUsers.size(), tenant);
    }

    private static AppUser user(UUID id, UUID tenant, String email, String name,
                                Role role, String department, int riskScore) {
        AppUser u = new AppUser(
                id, tenant, email, role, UserStatus.ACTIVE,
                name, department, riskScore);
        u.setLocale("vi");
        return u;
    }
}
