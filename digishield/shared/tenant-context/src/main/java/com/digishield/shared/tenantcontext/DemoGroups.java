package com.digishield.shared.tenantcontext;

import java.util.UUID;

/**
 * Well-known target-group identifiers used by the {@code dev} profile.
 * <p>
 * Fixed rather than random so a seeder in one module can point at a group
 * another module seeds: the simulation campaigns need an audience, and the
 * audience lives in tenancy. Without a stable id the campaign would have to
 * carry {@code null}, which is what left the Send button reporting "sent to 0
 * recipients".
 */
public final class DemoGroups {

    /** Static group: the leadership team. */
    public static final UUID LEADERSHIP_GROUP_ID =
            UUID.fromString("33333333-0000-0000-0000-00000000001a");

    /** Smart group: everyone whose risk score is 70 or higher. */
    public static final UUID HIGH_RISK_GROUP_ID =
            UUID.fromString("33333333-0000-0000-0000-00000000002b");

    /** Smart group: the finance department above a risk floor. */
    public static final UUID FINANCE_GROUP_ID =
            UUID.fromString("33333333-0000-0000-0000-00000000003c");

    private DemoGroups() {
    }
}
