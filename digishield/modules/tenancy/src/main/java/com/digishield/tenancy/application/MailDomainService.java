package com.digishield.tenancy.application;

import com.digishield.tenancy.api.MailDomainCheckDto;
import com.digishield.tenancy.api.MailDomainCheckDto.RecordCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Verifies an organisation's email sending domain by querying live DNS (MX, SPF,
 * DMARC) via JNDI. Read-only: it never sends mail, so it can be run on demand
 * from the settings screen. Provider-agnostic (works regardless of SES/SNS).
 */
@Service
public class MailDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(MailDomainService.class);
    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9.-]{1,253}$");

    /** Runs the DNS checks for {@code rawDomain}. */
    public MailDomainCheckDto check(String rawDomain) {
        String domain = normalise(rawDomain);
        if (domain == null) {
            RecordCheck bad = new RecordCheck(false, List.of(), "Tên miền không hợp lệ");
            return new MailDomainCheckDto(String.valueOf(rawDomain), bad, bad, bad);
        }
        DirContext ctx = null;
        try {
            ctx = dnsContext();
            RecordCheck mx = mxCheck(ctx, domain);
            RecordCheck spf = txtCheck(ctx, domain, "v=spf1", "Không tìm thấy bản ghi SPF (v=spf1)");
            RecordCheck dmarc = txtCheck(ctx, "_dmarc." + domain, "v=DMARC1",
                    "Không tìm thấy bản ghi DMARC tại _dmarc." + domain);
            return new MailDomainCheckDto(domain, mx, spf, dmarc);
        } catch (Exception e) {
            LOG.warn("DNS check failed for {}: {}", clean(domain), clean(e.toString()));
            RecordCheck err = new RecordCheck(false, List.of(), "Không truy vấn được DNS: " + e.getMessage());
            return new MailDomainCheckDto(domain, err, err, err);
        } finally {
            close(ctx);
        }
    }

    private RecordCheck mxCheck(DirContext ctx, String domain) {
        List<String> records = lookup(ctx, domain, "MX");
        boolean ok = !records.isEmpty();
        return new RecordCheck(ok, records, ok ? "Tên miền có thể nhận email" : "Không tìm thấy bản ghi MX");
    }

    private RecordCheck txtCheck(DirContext ctx, String name, String prefix, String missingNote) {
        List<String> all = lookup(ctx, name, "TXT");
        List<String> matched = all.stream()
                .map(r -> r.replace("\"", "").trim())
                .filter(r -> r.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
        boolean ok = !matched.isEmpty();
        return new RecordCheck(ok, ok ? matched : all, ok ? "Hợp lệ" : missingNote);
    }

    /** Returns the string values of a DNS attribute, or an empty list if absent. */
    private List<String> lookup(DirContext ctx, String name, String type) {
        List<String> out = new ArrayList<>();
        try {
            Attributes attrs = ctx.getAttributes(name, new String[]{type});
            Attribute attr = attrs.get(type);
            if (attr != null) {
                NamingEnumeration<?> values = attr.getAll();
                while (values.hasMore()) {
                    out.add(String.valueOf(values.next()));
                }
            }
        } catch (javax.naming.NameNotFoundException e) {
            // domain/subdomain doesn't exist — treated as "no records"
        } catch (Exception e) {
            LOG.debug("DNS {} lookup for {} failed: {}", type, clean(name), clean(e.toString()));
        }
        return out;
    }

    private DirContext dnsContext() throws javax.naming.NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2500");
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        return new InitialDirContext(env);
    }

    /** Lower-cases, trims, and strips a scheme/path if a URL was pasted. */
    private String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.trim().toLowerCase();
        d = d.replaceFirst("^https?://", "");
        int slash = d.indexOf('/');
        if (slash >= 0) {
            d = d.substring(0, slash);
        }
        int at = d.indexOf('@');
        if (at >= 0) {
            d = d.substring(at + 1);
        }
        d = d.replaceFirst("\\.$", "");
        return DOMAIN.matcher(d).matches() && d.contains(".") ? d : null;
    }

    /** Strips CR/LF and control chars so user-derived values can't forge log lines. */
    private static String clean(String s) {
        return s == null ? null : s.replaceAll("[\\p{Cntrl}]", "_");
    }

    private void close(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
