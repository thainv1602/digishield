#!/usr/bin/env python3
"""
Compare the routes Spring actually serves against the routes declared in
DigiShield_openapi.yaml.

Why this exists: in August 2026 five endpoints were running in controllers with
no entry in the spec, GET /auth/me among them. The backend worked, every test
was green, and the generated frontend client still could not call them. Nothing
in the pipeline could see it, because nothing compared the two.

The spec is authoritative for the frontend -- its client is generated from the
file -- so a route that exists only in Java is unreachable from the UI, and a
path that exists only in the spec generates a client method that 404s.

The first run found 40 undeclared routes, far more than the five we knew about.
Failing on all of them at once would only teach people to skip the check, so the
known 40 live in a baseline file and the check is a ratchet:

  * a served route that is neither declared nor in the baseline  -> fail (new debt)
  * a baseline entry that is now declared, or whose controller is
    gone                                                        -> fail (stale entry)

The second rule is what makes the list shrink: you cannot declare a route and
leave its baseline line behind, so the debt is always counted honestly.

Usage:
    python3 scripts/check-openapi-routes.py                    # CI mode
    python3 scripts/check-openapi-routes.py --update-baseline  # after declaring routes
    python3 scripts/check-openapi-routes.py --strict           # also fail on spec-only paths
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "DigiShield_openapi.yaml"
BASELINE = Path(__file__).resolve().parent / "openapi-route-baseline.txt"
SOURCES = [ROOT / "digishield" / "modules", ROOT / "digishield" / "boot"]

# Server URLs end in /api/v1, so spec paths are relative to it.
BASE = "/api/v1"

VERB_OF = {
    "GetMapping": "get",
    "PostMapping": "post",
    "PutMapping": "put",
    "PatchMapping": "patch",
    "DeleteMapping": "delete",
}

CLASS_MAPPING = re.compile(r'@RequestMapping\(\s*"([^"]+)"')
METHOD_MAPPING = re.compile(
    r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\s*\(([^)]*)\)'
)
# A mapping annotation with no argument at all: @GetMapping followed by a newline.
BARE_MAPPING = re.compile(
    r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\s*(?=[\r\n])'
)


def paths_in_annotation(args: str) -> list[str]:
    """Every string literal that is a path in a mapping annotation's arguments.

    Handles @GetMapping("/x"), @GetMapping(value = "/x", produces = ...) and
    @GetMapping({"/a", "/b"}). Ignores non-path literals such as media types by
    keeping only literals that start with a slash.
    """
    if "=" in args:
        m = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', args)
        args = m.group(1) if m else ""
    return [s for s in re.findall(r'"([^"]*)"', args) if s.startswith("/")]


def join(prefix: str, suffix: str) -> str:
    p = (prefix or "").rstrip("/")
    s = suffix or ""
    if s and not s.startswith("/"):
        s = "/" + s
    return (p + s) or "/"


def normalise(path: str) -> str:
    """Strip the /api/v1 base and collapse path-variable names to a placeholder."""
    if path.startswith(BASE):
        path = path[len(BASE):] or "/"
    # {id}, {token}, {groupId} ... all become {} so naming differences do not matter.
    return re.sub(r"\{[^}]*\}", "{}", path)


def routes_from_code() -> dict[tuple[str, str], str]:
    """(verb, normalised path) -> the file it came from."""
    found: dict[tuple[str, str], str] = {}
    for root in SOURCES:
        for java in sorted(root.rglob("*.java")):
            if "/src/test/" in str(java) or "/src/integrationTest/" in str(java):
                continue
            text = java.read_text(encoding="utf-8")
            if "Mapping" not in text:
                continue
            # Profile-gated dev helpers are not part of the product API and must
            # not be declared in the spec: /dev/token exists only under
            # dev-secure, to mint a token for local authorization testing.
            # Declaring it would put a credential-issuing endpoint in the
            # published contract; leaving it undeclared without this skip would
            # fail the check for a route production never serves.
            if '@Profile("dev' in text:
                continue
            cm = CLASS_MAPPING.search(text)
            prefix = cm.group(1) if cm else ""
            rel = str(java.relative_to(ROOT))

            for ann, args in METHOD_MAPPING.findall(text):
                verb = VERB_OF[ann]
                paths = paths_in_annotation(args) or [""]
                for p in paths:
                    found[(verb, normalise(join(prefix, p)))] = rel
            for ann in BARE_MAPPING.findall(text):
                found[(VERB_OF[ann], normalise(prefix or "/"))] = rel
    return found


def routes_from_spec() -> set[tuple[str, str]]:
    text = SPEC.read_text(encoding="utf-8")
    out: set[tuple[str, str]] = set()
    path = None
    for line in text.splitlines():
        m = re.match(r"^  (/\S*):", line)
        if m:
            path = normalise(m.group(1))
            continue
        m = re.match(r"^    (get|post|put|patch|delete):", line)
        if m and path:
            out.add((m.group(1), path))
    return out


def read_baseline() -> set[tuple[str, str]]:
    if not BASELINE.exists():
        return set()
    out: set[tuple[str, str]] = set()
    for line in BASELINE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        verb, _, path = line.partition(" ")
        out.add((verb.strip().lower(), path.strip()))
    return out


BASELINE_HEADER = """\
# Routes served by a controller but not declared in docs/DigiShield_openapi.yaml.
#
# This is accepted debt, not a permission slip. scripts/check-openapi-routes.py
# fails on any undeclared route that is NOT listed here, and equally fails on a
# line here that has since been declared or deleted -- so the file can only
# shrink. Never add a line by hand to silence the check; declare the route in
# the spec instead, then run:
#
#     python3 scripts/check-openapi-routes.py --update-baseline
#
# Entries: {count}
"""


def write_baseline(entries: set[tuple[str, str]]) -> None:
    lines = [BASELINE_HEADER.format(count=len(entries))]
    for verb, path in sorted(entries, key=lambda k: (k[1], k[0])):
        lines.append(f"{verb.upper()} {path}")
    BASELINE.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    strict = "--strict" in sys.argv
    code = routes_from_code()
    spec = routes_from_spec()

    undeclared = {k for k in code if k not in spec}
    baseline = read_baseline()

    if "--update-baseline" in sys.argv:
        write_baseline(undeclared)
        print(f"Baseline rewritten: {len(undeclared)} undeclared route(s).")
        return 0

    new_debt = sorted(undeclared - baseline)
    stale = sorted(baseline - undeclared)
    orphan = sorted(k for k in spec if k not in code)

    print(f"controller routes : {len(code)}")
    print(f"spec operations   : {len(spec)}")
    print(f"undeclared        : {len(undeclared)} (baseline allows {len(baseline)})")

    if new_debt:
        print(f"\n[FAIL] Served but NOT declared in the spec ({len(new_debt)} new):")
        for verb, path in new_debt:
            print(f"  {verb.upper():6} {path:44} {code[(verb, path)]}")
        print(
            "\nThe frontend client is generated from the spec, so these are\n"
            "unreachable from it. Add them to docs/DigiShield_openapi.yaml."
        )

    if stale:
        print(f"\n[FAIL] Baseline entries that no longer apply ({len(stale)}):")
        for verb, path in stale:
            why = "now declared" if (verb, path) in spec else "controller gone"
            print(f"  {verb.upper():6} {path:44} {why}")
        print(
            "\nProgress that is not recorded gets undone. Run:\n"
            "  python3 scripts/check-openapi-routes.py --update-baseline"
        )

    if orphan:
        label = "FAIL" if strict else "note"
        print(f"\n[{label}] Declared in the spec with no controller ({len(orphan)}):")
        for verb, path in orphan:
            print(f"  {verb.upper():6} {path}")
        if not strict:
            print("  (not a failure without --strict: may be planned or served elsewhere)")

    if new_debt or stale or (strict and orphan):
        return 1
    print("\nOK - no new undeclared routes, baseline is current.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
