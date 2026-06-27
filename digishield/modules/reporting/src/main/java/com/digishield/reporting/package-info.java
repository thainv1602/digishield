/**
 * Phishing report intake and classification module.
 * <p>
 * Users submit reports of suspicious emails/messages; the triage team
 * confirms the severity. When a report is confirmed as a threat, the module
 * emits {@code PhishingReportConfirmedEvent} for other modules (e.g. analytics).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reporting",
        allowedDependencies = {
                "contracts :: events",
                "contracts :: dto",
                "shared :: tenant-context",
                "shared :: messaging"
        }
)
package com.digishield.reporting;
