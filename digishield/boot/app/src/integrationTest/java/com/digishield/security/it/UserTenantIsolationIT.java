package com.digishield.security.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digishield.auth.domain.AppUser;
import com.digishield.auth.domain.Role;
import com.digishield.auth.domain.UserStatus;
import com.digishield.auth.infrastructure.AppUserRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves one organisation cannot reach another's people.
 *
 * <p>{@code TenantIsolationIT} proves the RLS mechanism on reports. This proves
 * the thing a customer is buying: that their staff list, and everything keyed to
 * it, is invisible to every other customer on the same database. Selling to a
 * second organisation rests on this being true, so it is asserted rather than
 * assumed from reading the policy.
 *
 * <p>Requires Docker: a real PostgreSQL, because RLS does not exist on H2.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
class UserTenantIsolationIT {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Connect as a role that cannot bypass RLS. Testcontainers' default user
        // is a superuser, and PostgreSQL lets superusers past row-level security
        // even with FORCE enabled -- measured, not assumed: as that user, both
        // tenants' rows come back with app.tenant_id set to one of them. A test
        // written on the default connection proves nothing.
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE ROLE rls_user WITH LOGIN PASSWORD 'rls_pass' NOSUPERUSER NOBYPASSRLS");
            stmt.execute("GRANT ALL PRIVILEGES ON DATABASE " + POSTGRES.getDatabaseName() + " TO rls_user");
            stmt.execute("GRANT ALL ON SCHEMA public TO rls_user");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Could not create the non-superuser role", e);
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "rls_user");
        registry.add("spring.datasource.password", () -> "rls_pass");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("digishield.rls.app-role", () -> "");
        registry.add("spring.cache.type", () -> "none");
        registry.add("management.health.redis.enabled", () -> false);
    }

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        }
    }

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUpSchema() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.support.EncodedResource(
                            new ClassPathResource("it/rls-setup.sql"), StandardCharsets.UTF_8));
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private AppUser user(UUID tenantId, String email) {
        return new AppUser(UUID.randomUUID(), tenantId, email, Role.LEARNER, UserStatus.ACTIVE);
    }

    private <T> T asTenant(UUID tenantId, Supplier<T> work) {
        TenantContext.set(tenantId.toString());
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void oneOrganisationCannotSeeAnothersPeople() {
        UUID inA = asTenant(TENANT_A, () -> userRepository.save(user(TENANT_A, "a@acme.vn")).getId());
        asTenant(TENANT_B, () -> userRepository.save(user(TENANT_B, "b@globex.vn")));

        List<AppUser> seenByA = asTenant(TENANT_A, () -> userRepository.findAll());

        // findAll() is the shape that leaks: any listing screen, export or report
        // that forgets a tenant filter still returns only this tenant's rows,
        // because the database refuses to hand over the others.
        assertThat(seenByA).extracting(AppUser::getId).containsExactly(inA);
        assertThat(seenByA).extracting(AppUser::getTenantId).containsOnly(TENANT_A);
    }

    @Test
    void anIdFromAnotherOrganisationResolvesToNothing() {
        UUID inB = asTenant(TENANT_B, () -> userRepository.save(user(TENANT_B, "b@globex.vn")).getId());

        // Guessing or leaking an id must not be enough. This is the attack that
        // survives a careful UI: the caller supplies the id directly.
        assertThat(asTenant(TENANT_A, () -> userRepository.findById(inB))).isEmpty();
    }

    @Test
    void writingIntoAnotherOrganisationIsRefused() {
        // The WITH CHECK half of the policy. Without it, a tenant could create
        // members inside somebody else's organisation.
        assertThatThrownBy(() -> asTenant(TENANT_A, () ->
                userRepository.save(user(TENANT_B, "smuggled@acme.vn"))))
                .hasMessageContaining("row-level security");
    }

    @Test
    void deletingAcrossOrganisationsSilentlyDeletesNothing() {
        UUID inB = asTenant(TENANT_B, () -> userRepository.save(user(TENANT_B, "b@globex.vn")).getId());

        asTenant(TENANT_A, () -> {
            userRepository.findById(inB).ifPresent(userRepository::delete);
            return null;
        });

        // A DELETE filtered by RLS removes zero rows rather than erroring, so the
        // check that matters is that the row is still there afterwards.
        assertThat(asTenant(TENANT_B, () -> userRepository.findById(inB))).isPresent();
    }

    @Test
    void withoutATenantNothingIsReadableAtAll() {
        asTenant(TENANT_A, () -> userRepository.save(user(TENANT_A, "a@acme.vn")));
        TenantContext.clear();

        // Fail closed, and loudly: the aspect refuses to open a transaction with
        // no tenant rather than quietly running one that would see nothing. A
        // background job that forgets to set a tenant stops instead of reading.
        assertThatThrownBy(() -> transactionTemplate.execute(status -> userRepository.findAll()))
                .isInstanceOf(IllegalStateException.class);
    }
}
