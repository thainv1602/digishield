package com.digishield;

import com.digishield.analytics.api.WorkforceDirectory;
import com.digishield.auth.api.AuthService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Wires the analytics module's {@link WorkforceDirectory} SPI to the auth
 * module, so the risk rollup can group people by department. Lives in the boot
 * app to keep analytics decoupled from auth (mirrors {@link AuthUserDirectory}).
 */
@Component
class AuthWorkforce implements WorkforceDirectory {

    private final AuthService authService;

    AuthWorkforce(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public List<Member> members() {
        // listUsers() is already scoped to the tenant in context, which the
        // rollup sets one tenant at a time.
        return authService.listUsers().stream()
                .map(u -> new Member(u.id(), u.department()))
                .toList();
    }
}
