package com.digishield.analytics.api;

import java.util.List;
import java.util.UUID;

/**
 * SPI for reading the current tenant's people and the department each belongs
 * to, so risk can be rolled up per department.
 * <p>
 * Analytics owns risk but not the workforce; the boot application bridges this
 * to the auth module, the same way {@link RecentReportsProvider} is bridged to
 * reporting. Without it the department panel has no denominator: a department's
 * phish-prone rate is the share of <em>its people</em> who clicked, so the
 * people who did not click have to be counted too, and risk signals alone only
 * ever name the ones who did.
 */
public interface WorkforceDirectory {

    /**
     * Every member of the current tenant. Never {@code null}; empty when the
     * tenant has no users yet.
     */
    List<Member> members();

    /**
     * One person, reduced to what a rollup needs.
     *
     * @param userId     the user
     * @param department department name, or {@code null} when unassigned
     */
    record Member(UUID userId, String department) {
    }
}
