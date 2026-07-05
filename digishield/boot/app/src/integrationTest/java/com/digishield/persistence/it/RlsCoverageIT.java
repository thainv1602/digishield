package com.digishield.persistence.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail against a whole class of tenant-isolation bugs: every tenant-scoped
 * table — one that carries a {@code tenant_id} column — must have PostgreSQL
 * Row-Level Security <em>enabled</em> and a <em>policy</em>, so isolation is
 * enforced by the database and not only by application-level {@code tenant_id}
 * filtering (which a raw query or a forgetful repository method could bypass).
 *
 * <p>The real Flyway migrations are applied to a throwaway PostgreSQL and the
 * catalog is inspected. If a future migration introduces a tenant table without
 * RLS, this test fails and names it. Requires Docker.
 */
@Testcontainers
class RlsCoverageIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void everyTenantScopedTableHasRlsEnabledAndAPolicy() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            Set<String> tenantTables = query(c,
                    "SELECT table_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND column_name = 'tenant_id'");
            Set<String> rlsEnabled = query(c,
                    "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = 'public' AND c.relrowsecurity");
            Set<String> withPolicy = query(c,
                    "SELECT tablename FROM pg_policies WHERE schemaname = 'public'");

            List<String> missing = new ArrayList<>();
            for (String t : tenantTables) {
                if (!rlsEnabled.contains(t) || !withPolicy.contains(t)) {
                    missing.add(t);
                }
            }

            assertThat(missing)
                    .as("tenant-scoped tables (with a tenant_id column) missing RLS enable + policy")
                    .isEmpty();
            // Sanity: the migrations really did create tenant-scoped tables to check.
            assertThat(tenantTables).as("tables carrying a tenant_id column").isNotEmpty();
        }
    }

    private static Set<String> query(Connection c, String sql) throws Exception {
        Set<String> out = new TreeSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
