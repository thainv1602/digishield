package com.digishield.tenancy.web;

import com.digishield.tenancy.api.MailDomainCheckDto;
import com.digishield.tenancy.application.MailDomainService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings endpoint to verify the org's email sending domain via live DNS
 * (MX / SPF / DMARC). Org-admin only; read-only (no mail is sent).
 */
@RestController
public class MailDomainController {

    private final MailDomainService mailDomainService;

    public MailDomainController(MailDomainService mailDomainService) {
        this.mailDomainService = mailDomainService;
    }

    @PreAuthorize("hasRole('ORG_ADMIN')")
    @GetMapping("/api/v1/settings/mail-domain/verify")
    public ResponseEntity<MailDomainCheckDto> verify(@RequestParam("domain") String domain) {
        return ResponseEntity.ok(mailDomainService.check(domain));
    }
}
