package com.digishield.tenancy.api;

import java.util.List;

/**
 * Result of a live DNS check of an organisation's email sending domain: whether
 * it has MX (can receive mail), an SPF record, and a DMARC policy. No mail is
 * sent — only DNS is queried — so it is safe to run on demand.
 *
 * @param domain the domain that was checked (normalised)
 * @param mx     MX record check
 * @param spf    SPF (TXT {@code v=spf1}) check
 * @param dmarc  DMARC (TXT at {@code _dmarc.<domain>}) check
 */
public record MailDomainCheckDto(
        String domain,
        RecordCheck mx,
        RecordCheck spf,
        RecordCheck dmarc) {

    /**
     * One record-type result.
     *
     * @param ok      whether a usable record was found
     * @param records the raw records found (may be empty)
     * @param note    a short human-readable note (e.g. an error or hint)
     */
    public record RecordCheck(boolean ok, List<String> records, String note) {
    }
}
