#!/usr/bin/env python3
"""
Compare the enum values in DigiShield_openapi.yaml against the Java enums
behind them.

The route check proves a path is declared. The schema check proves the field
names are right. Neither looks inside a field: BlacklistEntry.type declared
[url, phone, account] while BlacklistType holds domain/url/email/ip/phone/hash,
and Report.status declared [new, ai_classified, in_review, closed] -- four
values the server has never produced. Both were "declared" the whole time.

Pairing cannot be inferred. A DTO field carrying an enum is usually typed
String, with the conversion (`getStatus().name().toLowerCase()`) buried in a
mapper, so the record says nothing about which enum backs it. Matching by value
overlap guesses wrong: [email, sms] fits Channel, TemplateChannel and
NotificationChannel equally. So every enum position is declared here by hand,
and an undeclared one fails the check -- a new enum cannot slip in unnoticed.

Each entry is "Enum:mode:case", so that every relaxation is a recorded
decision rather than a blanket rule:

    exact     spec values == the enum's constants
    subset    spec values are a subset -- for inputs deliberately narrower than
              the enum (template generation offers only email and sms)
    lower/upper
              which spelling reaches the wire. Case is part of the contract:
              the watchlist check emits WATCH while the spec promised watch,
              and folding both sides would have hidden exactly that.
    -         no Java enum backs this: a free-form String column or a product
              vocabulary. Never checked, always explained.

`except` drops constants that never reach the wire, such as Role.TENANT_ADMIN,
a legacy alias the web layer maps to org_admin before serialising.

Usage:
    python3 scripts/check-openapi-enums.py
    python3 scripts/check-openapi-enums.py --update-baseline
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "DigiShield_openapi.yaml"
BASELINE = Path(__file__).resolve().parent / "openapi-enum-baseline.txt"
SOURCES = [ROOT / "digishield"]

# location -> "Enum:mode:case [except=A,B]" | "-  reason it is unbacked"
DECLARED = {
    # ---- schemas -----------------------------------------------------------
    "schemas.AccountWatchEntry.properties.risk_level": "RiskLevel:exact:lower",
    "schemas.AccountWatchEntry.properties.type": "WatchType:exact:lower",
    "schemas.Assessment.properties.type": "AssessmentType:exact:lower",
    "schemas.BlacklistEntry.properties.type": "BlacklistType:exact:lower",
    "schemas.BlacklistEntryInput.properties.type": "BlacklistType:exact:lower",
    "schemas.Course.properties.level": "CourseLevel:exact:lower",
    "schemas.Enrollment.properties.status": "EnrollmentStatus:exact:lower",
    "schemas.InterventionDecision.properties.decision": "Decision:exact:lower",
    "schemas.InterventionEvent.properties.decision": "Decision:exact:lower",
    "schemas.Me.properties.role": "Role:exact:lower except=TENANT_ADMIN",
    "schemas.Notification.properties.channel": "NotificationChannel:exact:lower",
    "schemas.Notification.properties.status": "NotificationStatus:exact:lower",
    "schemas.Notification.properties.type": "NotificationType:exact:lower",
    "schemas.NotificationInput.properties.channel": "NotificationChannel:exact:lower",
    "schemas.NotificationInput.properties.status": "NotificationStatus:exact:lower",
    "schemas.NotificationInput.properties.type": "NotificationType:exact:lower",
    "schemas.Plan.properties.name": "-  plan names are commercial, not a Java enum",
    "schemas.Report.properties.aiLabel": "AiLabel:exact:lower",
    "schemas.Report.properties.channel": "-  free String column, set from the request",
    "schemas.Report.properties.status": "ReportStatus:exact:lower",
    "schemas.RiskScore.properties.scope": "RiskScope:exact:lower",
    "schemas.SimCampaign.allOf.properties.status": "CampaignStatus:exact:lower",
    "schemas.SimCampaignInput.properties.channel": "TemplateChannel:exact:lower",
    "schemas.SimEvent.properties.action": "SimAction:exact:upper",
    "schemas.SimTemplate.properties.body_format": "BodyFormat:exact:lower",
    "schemas.SimTemplate.properties.channel": "TemplateChannel:exact:lower",
    "schemas.SimTemplate.properties.difficulty": "Difficulty:exact:lower",
    "schemas.SimTemplate.properties.status": "TemplateStatus:exact:lower",
    "schemas.SimTemplateUpsert.properties.body_format": "BodyFormat:exact:lower",
    "schemas.SimTemplateUpsert.properties.channel": "TemplateChannel:exact:lower",
    "schemas.SimTemplateUpsert.properties.difficulty": "Difficulty:exact:lower",
    "schemas.Subscription.properties.status": "-  billing states, not modelled in Java",
    "schemas.Tenant.properties.status": "TenantStatus:exact:lower",
    "schemas.Tenant.properties.tier": "TenantTier:exact:lower",
    "schemas.TenantUpdate.properties.status": "TenantStatus:exact:lower",
    "schemas.TenantUpdate.properties.tier": "TenantTier:exact:lower",
    "schemas.UsageMetering.properties.metric": "-  metering keys, not modelled in Java",
    "schemas.User.properties.role": "Role:exact:lower except=TENANT_ADMIN",
    "schemas.User.properties.status": "UserStatus:exact:lower",
    # ---- request bodies and query parameters -------------------------------
    "paths./account-watchlist/check.get.parameters.schema": "WatchType:exact:lower",
    "paths./account-watchlist/check.get.responses.200.content.application/json"
    ".schema.properties.riskLevel": "RiskLevel:exact:upper",
    "paths./ai/classify.post.responses.200.content.application/json.schema"
    ".properties.label": "AiLabel:exact:lower",
    "paths./ai/moderate.post.responses.200.content.application/json.schema"
    ".properties.verdict": "-  the AI client's verdicts, not a domain enum",
    "paths./ai/orchestration/run.post.requestBody.content.application/json.schema"
    ".properties.scope": "-  orchestration scope words, not RiskScope",
    "paths./ai/templates/generate.post.requestBody.content.application/json.schema"
    ".properties.channel": "TemplateChannel:subset:lower",
    "paths./alerts/broadcast.post.requestBody.content.application/json.schema"
    ".properties.severity": "-  broadcast severity, unrelated to the audit Severity",
    "paths./analytics/risk.get.parameters.schema": "RiskScope:exact:lower",
    "paths./assessments.get.parameters.schema": "AssessmentType:exact:lower",
    "paths./assessments/placement.post.responses.200.content.application/json"
    ".schema.properties.level": "CourseLevel:exact:lower",
    "paths./compliance/status.get.parameters.schema": "-  reporting scope words, not RiskScope",
    "paths./enrollments.get.parameters.schema": "EnrollmentStatus:exact:lower",
    "paths./gamification/leaderboard.get.parameters.schema":
        "-  leaderboard scope words, not RiskScope",
    "paths./notifications/reminders.post.requestBody.content.application/json.schema"
    ".properties.channel": "NotificationChannel:exact:lower",
    "paths./reports/phishing.get.parameters.schema": "ReportStatus:exact:lower",
    "paths./reports/phishing.post.requestBody.content.application/json.schema"
    ".properties.channel": "-  free String column, set from the request",
    "paths./reports/phishing/{id}/triage.post.requestBody.content.application/json"
    ".schema.properties.decision": "TriageDecision:exact:lower",
    "paths./tenants.post.requestBody.content.application/json.schema"
    ".properties.tier": "TenantTier:exact:lower",
}


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def java_enums() -> dict[str, list[str]]:
    """Enum name -> constants. Comments are removed first, because a javadoc
    containing {@code org_admin} otherwise ends the enum body early and the
    parser silently reports one constant where there are seven."""
    out: dict[str, list[str]] = {}
    for root in SOURCES:
        for java in sorted(root.rglob("*.java")):
            s = str(java)
            if "/build/" in s or "/src/test/" in s or "/src/integrationTest/" in s:
                continue
            text = strip_comments(java.read_text(encoding="utf-8", errors="replace"))
            for m in re.finditer(r"\benum\s+([A-Z]\w*)\s*\{", text):
                depth, body = 1, ""
                for k in range(m.end(), len(text)):
                    ch = text[k]
                    if ch == "{":
                        depth += 1
                    elif ch == "}":
                        depth -= 1
                        if depth == 0:
                            break
                    if ch == ";" and depth == 1:
                        break
                    body += ch
                consts = re.findall(r"\b([A-Z][A-Z0-9_]*)\s*(?:\([^)]*\))?\s*(?=,|$)", body)
                if consts:
                    out.setdefault(m.group(1), consts)
    return out


def spec_enums(spec) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}

    def walk(node, path):
        if isinstance(node, dict):
            if node.get("type") == "string" and "enum" in node:
                found[path] = list(node["enum"])
            for k, v in node.items():
                walk(v, f"{path}.{k}" if path else k)
        elif isinstance(node, list):
            for v in node:
                walk(v, path)

    walk(spec["components"]["schemas"], "schemas")
    walk(spec["paths"], "paths")
    return found


def read_baseline() -> set[str]:
    if not BASELINE.exists():
        return set()
    return {line.split("#", 1)[0].strip()
            for line in BASELINE.read_text(encoding="utf-8").splitlines()
            if line.split("#", 1)[0].strip()}


HEADER = """\
# Enum positions whose declared values do not match the Java enum behind them.
#
# Accepted debt. scripts/check-openapi-enums.py fails on any mismatch not listed
# here, and on a line here that no longer mismatches, so the file only shrinks.
# Fix the spec (or the enum), then run:
#
#     python3 scripts/check-openapi-enums.py --update-baseline
#
# Entries: {count}
"""


def main() -> int:
    spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))
    positions = spec_enums(spec)
    enums = java_enums()

    undeclared = sorted(set(positions) - set(DECLARED))
    orphan_decls = sorted(set(DECLARED) - set(positions))

    mismatches: dict[str, str] = {}
    checked = unbacked = 0

    for path, values in sorted(positions.items()):
        decl = DECLARED.get(path)
        if decl is None:
            continue
        if decl.startswith("-"):
            unbacked += 1
            continue
        head = decl.split()[0]
        name, mode, case = head.split(":")
        excepted = set()
        m = re.search(r"except=([\w,]+)", decl)
        if m:
            excepted = set(m.group(1).split(","))
        constants = enums.get(name)
        if constants is None:
            mismatches[path] = f"    no Java enum named {name}"
            continue
        checked += 1
        # Case is part of the contract: the watchlist check emits WATCH while
        # the spec promised watch, and lower-casing both sides would have hidden
        # exactly that.
        fold = str.upper if case == "upper" else str.lower
        java = {fold(c) for c in constants if c not in excepted}
        declared = set(values)
        if mode == "exact" and declared != java:
            extra, missing = sorted(declared - java), sorted(java - declared)
            detail = []
            if extra:
                detail.append(f"    spec declares, {name} has no such constant: {extra}")
            if missing:
                detail.append(f"    {name} has, spec omits: {missing}")
            mismatches[path] = "\n".join(detail)
        elif mode == "subset" and not declared <= java:
            mismatches[path] = (
                f"    spec declares, {name} has no such constant: "
                f"{sorted(declared - java)}")

    if "--update-baseline" in sys.argv:
        lines = [HEADER.format(count=len(mismatches))] + sorted(mismatches)
        BASELINE.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"Baseline rewritten: {len(mismatches)} mismatch(es).")
        return 0

    baseline = read_baseline()
    new = sorted(set(mismatches) - baseline)
    stale = sorted(baseline - set(mismatches))

    print(f"enum positions    : {len(positions)}")
    print(f"checked against Java: {checked}   unbacked (declared): {unbacked}")
    print(f"mismatches        : {len(mismatches)} (baseline allows {len(baseline)})")

    if undeclared:
        print(f"\n[FAIL] Enum positions not declared in this check ({len(undeclared)}):")
        for p in undeclared:
            print(f"  {p}\n    spec values: {positions[p]}")
        print("\nAdd each to DECLARED, naming the Java enum behind it or '-' with a\n"
              "reason. An undeclared enum is one nothing is comparing.")

    if orphan_decls:
        print(f"\n[FAIL] Declared positions that no longer exist in the spec "
              f"({len(orphan_decls)}):")
        for p in orphan_decls:
            print(f"  {p}")
        print("\nRemove them from DECLARED.")

    if new:
        print(f"\n[FAIL] Declared values do not match the Java enum ({len(new)}):")
        for p in new:
            print(f"  {p}")
            print(mismatches[p])

    if stale:
        print(f"\n[FAIL] Baseline entries that no longer mismatch ({len(stale)}):")
        for p in stale:
            print(f"  {p}")
        print("\nRun: python3 scripts/check-openapi-enums.py --update-baseline")

    if undeclared or orphan_decls or new or stale:
        return 1
    print("\nOK - every declared enum matches the Java enum behind it.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
