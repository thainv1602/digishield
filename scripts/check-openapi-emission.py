#!/usr/bin/env python3
"""
Compare the *case* an enum reaches the wire in against what the spec promises.

The three existing checks answer three questions: is the route declared
(routes), are the field names right (schemas), are the values the right set
(enums). None of them looks at the code that produces a value.

That gap is not hypothetical. `TenantView.tier` was declared in
check-openapi-enums.py as `TenantTier:exact:lower`, the spec listed
[pool, bridge, silo], and the enum check passed - while `toView` returned
`getTier().name()`, so the API answered `POOL` for months. The super-admin
console counts organisations with `status === 'active'` and therefore reported
zero live tenants however many were running. Both sides of that check agreed
with each other; neither had read the mapper.

So this check reads the mapper. For every enum position the enum check declares
with a spelling, it pairs the schema to the Java record that serves it, finds
the component behind the field, and works out what the code actually emits:

    component typed as the enum   Jackson writes the constant name -> UPPER
    ... .name().toLowerCase(...)  -> lower
    ... .name().toUpperCase(...)  -> upper
    ... .name()                   -> UPPER
    a string literal              -> whatever the literal is

Anything it cannot read is reported as unverifiable and carried in a baseline,
never silently counted as agreement. Two construction sites that disagree are a
finding in their own right: the same field cannot reach the wire two ways.

Inbound-only schemas are out of scope: a request body's question is what the
server accepts, and both `requiredTier` and `parseStatus` upper case before
`valueOf`, so either spelling is valid there.

Usage:
    python3 scripts/check-openapi-emission.py
    python3 scripts/check-openapi-emission.py --verbose
    python3 scripts/check-openapi-emission.py --update-baseline
"""
import importlib.util
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
HERE = Path(__file__).resolve().parent
SPEC = ROOT / "docs" / "DigiShield_openapi.yaml"
BASELINE = HERE / "openapi-emission-baseline.txt"


def _load(filename: str, name: str):
    """Import a sibling check. The parsing lives there; duplicating it here is
    how two checks drift into disagreeing about the same source file."""
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


schemas_check = _load("check-openapi-schemas.py", "digishield_check_schemas")
enums_check = _load("check-openapi-enums.py", "digishield_check_enums")

UPPER, LOWER = "upper", "lower"


# ------------------------------------------------------------------ sources
def sources() -> list[tuple[Path, str]]:
    """Main sources with comments removed: a `// Lower case, like ...` note
    inside a constructor call otherwise splits the argument list in the wrong
    places and every field after it is read as the previous one."""
    return [(java, enums_check.strip_comments(text))
            for java, text in schemas_check.iter_sources()]


def record_components(src) -> dict[str, list[tuple[str, str]]]:
    """Record name -> [(java type, wire name)], declaration order.

    The wire name is what the spec calls the field, so @JsonProperty wins:
    `@JsonProperty("risk_level") String riskLevel` is `risk_level` in the
    contract, and matching on the Java name alone loses every snake_case
    field - which is most of the ones that carry an enum.
    """
    out: dict[str, list[tuple[str, str]]] = {}
    for _java, text in src:
        for m in schemas_check.RECORD.finditer(text):
            if m.group(1) == "private":
                continue
            name = m.group(2)
            body = _balanced(text, m.end() - 1)
            if body is None:
                continue
            comps = []
            for part in schemas_check.split_top_level(body):
                tokens = re.sub(r"@\w+(\([^)]*\))?", " ", part).split()
                if len(tokens) < 2:
                    continue
                json_property = re.search(
                    r'@(?:com\.fasterxml\.jackson\.annotation\.)?JsonProperty'
                    r'\(\s*"([^"]+)"', part)
                comps.append(
                    (tokens[-2], json_property.group(1) if json_property else tokens[-1]))
            out.setdefault(name, comps)
    return out


def _balanced(text: str, open_paren: int) -> str | None:
    """The text between `open_paren` and its matching close."""
    depth = 0
    for k in range(open_paren, len(text)):
        if text[k] == "(":
            depth += 1
        elif text[k] == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren + 1:k]
    return None


def constructions(src, record: str) -> list[tuple[Path, list[str], int]]:
    """Every `new Record(...)` in main sources: (file, arguments, offset).

    The offset matters: resolving a bare argument means finding the method it
    sits in, and searching the file for the identifier instead lands on the
    record's own component list every time.
    """
    out = []
    pattern = re.compile(r"\bnew\s+" + re.escape(record) + r"\s*\(")
    for java, text in src:
        for m in pattern.finditer(text):
            body = _balanced(text, m.end() - 1)
            if body is not None:
                out.append((java, schemas_check.split_top_level(body), m.start()))
    return out


# --------------------------------------------------------------- emission
def emitted_case(expression: str) -> str | None:
    """The spelling this argument reaches the wire in, or None if unreadable."""
    e = " ".join(expression.split())
    if ".toLowerCase(" in e:
        return None if ".toUpperCase(" in e else LOWER
    if ".toUpperCase(" in e:
        return UPPER
    if ".name()" in e:
        return UPPER
    literals = re.findall(r'"([^"]*)"', e)
    if literals and not re.search(r"\w\s*\(", re.sub(r'"[^"]*"', "", e)):
        letters = [c for lit in literals for c in lit if c.isalpha()]
        if letters and all(c.islower() for c in letters):
            return LOWER
        if letters and all(c.isupper() for c in letters):
            return UPPER
    return None


METHOD = re.compile(
    r"(?:public|protected|private)\s+(?:static\s+)?[\w.<>,\[\]?\s]+?\b(\w+)\s*\(")


def method_at(text: str, name: str) -> tuple[list[str], str] | None:
    """(parameter names, body) of the first declaration of `name` in `text`."""
    for m in METHOD.finditer(text):
        if m.group(1) != name:
            continue
        params = _balanced(text, m.end() - 1)
        if params is None:
            continue
        names = []
        for p in schemas_check.split_top_level(params):
            tokens = re.sub(r"@\w+(\([^)]*\))?", " ", p).split()
            if tokens:
                names.append(tokens[-1])
        brace = text.find("{", m.end())
        if brace == -1:
            continue
        depth, end = 0, len(text)
        for k in range(brace, len(text)):
            if text[k] == "{":
                depth += 1
            elif text[k] == "}":
                depth -= 1
                if depth == 0:
                    end = k
                    break
        return names, text[brace + 1:end]
    return None


def enclosing_method(text: str, position: int) -> tuple[str, list[str]] | None:
    """The method a source offset sits inside: (name, parameter names)."""
    last = None
    for m in METHOD.finditer(text[:position]):
        last = m
    if last is None:
        return None
    found = method_at(text, last.group(1))
    return (last.group(1), found[0]) if found else None


def resolve_case(expression: str, java: Path, text: str, src,
                 offset: int = 0, depth: int = 0):
    """The spelling an argument ends up as, following at most two hops.

    A value assembled behind a helper is not a value nobody can check: the
    enrollment status is built by `statusOf(enrollment)`, and stopping at the
    call site would have filed a real mismatch away as "unreadable" - which is
    the failure mode this whole check exists to avoid.
    """
    direct = emitted_case(expression)
    if direct is not None:
        return direct, f"{java.relative_to(ROOT)}: {' '.join(expression.split())[:70]}"
    if depth >= 2:
        return None, None
    e = " ".join(expression.split())

    # A call to a helper declared in the same file: read what it returns.
    call = re.fullmatch(r"(?:this\.)?(\w+)\s*\(.*\)", e, re.S)
    if call:
        found = method_at(text, call.group(1))
        if found:
            cases = set()
            for ret in re.findall(r"\breturn\s+([^;]+);", found[1]):
                if ret.strip() == "null":
                    continue
                case, _where = resolve_case(ret, java, text, src, offset, depth + 1)
                if case is None:
                    return None, None
                cases.add(case)
            if len(cases) == 1:
                return cases.pop(), f"{java.relative_to(ROOT)}: {call.group(1)}() returns it"

    # A bare parameter of the enclosing method: read what the callers pass.
    if re.fullmatch(r"\w+", e):
        position = offset
        enclosing = enclosing_method(text, position)
        if enclosing and e in enclosing[1]:
            index = enclosing[1].index(e)
            cases, where = set(), None
            # Qualified by the declaring type outside its own file. A bare
            # `of\s*\(` matches List.of, Map.of and Optional.of in every file
            # in the repo, and one nonsensical argument later the whole
            # position reads as unresolvable.
            owner = re.escape(java.stem)
            method = re.escape(enclosing[0])
            qualified = re.compile(r"\b" + owner + r"\s*\.\s*" + method + r"\s*\(")
            local = re.compile(r"(?:\bthis\s*\.\s*|\b)" + method + r"\s*\(")
            for other, other_text in src:
                pattern = local if other == java else qualified
                for m in pattern.finditer(other_text):
                    if other == java and m.start() <= position:
                        continue  # the declaration and the site being resolved
                    args = _balanced(other_text, m.end() - 1)
                    if args is None:
                        continue
                    parts = schemas_check.split_top_level(args)
                    if len(parts) <= index:
                        continue
                    case, w = resolve_case(parts[index], other, other_text, src,
                                           m.start(), depth + 1)
                    if case is None:
                        return None, None
                    cases.add(case)
                    where = w
            if len(cases) == 1:
                return cases.pop(), where
    return None, None


def enum_names(src) -> set[str]:
    return {m.group(1)
            for _java, text in src
            for m in re.finditer(r"\benum\s+([A-Z]\w*)\s*[\{<]", text)}


def json_value_enums(src) -> set[str]:
    """Enums whose serialised form is decided by @JsonValue rather than by the
    constant name. Reading one would mean following a method body, so they are
    reported as unverifiable instead of guessed at."""
    out = set()
    for _java, text in src:
        for m in re.finditer(r"\benum\s+([A-Z]\w*)\s*[\{<]", text):
            body = text[m.end():m.end() + 4000]
            if "@JsonValue" in body:
                out.add(m.group(1))
    return out


# ---------------------------------------------------------------- pairing
def response_records(spec) -> tuple[dict[str, str], set[str]]:
    """(schema name -> the Java record a handler returns, inbound-only schemas).

    Only responses carry an emitted spelling. A schema that appears solely as a
    request body is a different question - what the server accepts - and both
    `requiredTier` and `parseStatus` upper case before `valueOf`, so either
    spelling is valid there. Those are named rather than counted as unread.
    """
    ops = schemas_check.spec_operations(spec)
    out: dict[str, set[str]] = {}
    responses, bodies = set(), set()
    for key, op in ops.items():
        if op["resp"][0]:
            responses.add(op["resp"][0])
        if op["body"][0]:
            bodies.add(op["body"][0])
    for key, handler in schemas_check.handlers().items():
        op = ops.get(key)
        if not op:
            continue
        schema_name, _props = op["resp"]
        if schema_name and handler["returns"]:
            out.setdefault(schema_name, set()).add(handler["returns"])
    paired = {name: sorted(v)[0] for name, v in out.items() if len(v) == 1}
    return paired, bodies - responses


BASELINE_HEADER = """\
# Known disagreements between the spec and what the server writes, and enum
# positions whose emitted case this check cannot read. One per line, prefixed
# by which it is.
#
# A mismatch here is debt, not permission: the spec promises one spelling and
# the server writes another. Shrinking this list is the point; growing it
# needs a reason.
#
#     python3 scripts/check-openapi-emission.py --update-baseline
#
# Entries: {count}
"""


def read_baseline() -> set[str]:
    if not BASELINE.exists():
        return set()
    return {line.strip() for line in BASELINE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def main() -> int:
    verbose = "--verbose" in sys.argv

    problems = schemas_check.guard_assumptions()
    if problems:
        print("[FAIL] The DTO parsing assumptions no longer hold:\n")
        for p in problems:
            print("  " + p)
        return 1

    spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))
    src = sources()
    comps = record_components(src)
    enums = enum_names(src)
    custom = json_value_enums(src)
    paired, inbound_only = response_records(spec)

    checked = 0
    inbound = 0
    mismatches: list[str] = []
    mismatch_at: list[str] = []
    unverifiable: dict[str, str] = {}

    for location, declaration in sorted(enums_check.DECLARED.items()):
        parts = declaration.split(":")
        if len(parts) < 3 or parts[0] == "-":
            continue
        want = parts[2]
        if want not in (UPPER, LOWER):
            continue
        m = re.fullmatch(r"schemas\.(\w+)\.properties\.([\w.]+)", location)
        if not m:
            continue
        schema, field = m.group(1), m.group(2)

        if schema in inbound_only:
            inbound += 1
            continue

        record = paired.get(schema)
        if record is None:
            unverifiable[location] = "no response handler returns this schema"
            continue
        components = comps.get(record)
        if not components:
            unverifiable[location] = f"record {record} not parsed"
            continue

        index = next((i for i, (_t, n) in enumerate(components) if n == field), None)
        if index is None:
            unverifiable[location] = f"{record} has no component named {field}"
            continue

        java_type = components[index][0]
        if java_type in enums:
            if java_type in custom:
                unverifiable[location] = f"{java_type} serialises through @JsonValue"
                continue
            # Jackson writes the constant name for an enum-typed component.
            found = {UPPER}
            where = [f"{record}.{field} is typed {java_type}"]
        else:
            sites = [(java, args, at) for java, args, at in constructions(src, record)
                     if len(args) > index]
            if not sites:
                unverifiable[location] = f"no `new {record}(...)` in main sources"
                continue
            found, where = set(), []
            for java, args, at in sites:
                text = next(t for f, t in src if f == java)
                case, site = resolve_case(args[index], java, text, src, at)
                rel = java.relative_to(ROOT)
                if case is None:
                    unverifiable[location] = (
                        f"{rel}: cannot read `{' '.join(args[index].split())[:60]}`")
                    found = None
                    break
                found.add(case)
                where.append(site or f"{rel}: {' '.join(args[index].split())[:70]}")
            if found is None:
                continue

        checked += 1
        if len(found) > 1:
            mismatch_at.append(location)
            mismatches.append(
                f"{location}\n    the same field is built two ways: {sorted(found)}\n"
                + "\n".join(f"      {w}" for w in where))
        elif found != {want}:
            emitted = sorted(found)[0]
            mismatch_at.append(location)
            mismatches.append(
                f"{location}\n    spec promises {want} case, the server writes "
                f"{emitted} case\n" + "\n".join(f"      {w}" for w in where))
        elif verbose:
            print(f"  ok  {location:52} {want:5} {where[0][:70]}")

    if "--update-baseline" in sys.argv:
        entries = sorted({f"mismatch   {location}" for location in mismatch_at}
                         | {f"unreadable {location}" for location in unverifiable})
        BASELINE.write_text(
            "\n".join([BASELINE_HEADER.format(count=len(entries))] + entries) + "\n",
            encoding="utf-8")
        print(f"Baseline rewritten: {len(entries)} recorded position(s).")
        return 0

    baseline = read_baseline()
    entries = {f"mismatch   {location}" for location in mismatch_at}
    entries |= {f"unreadable {location}" for location in unverifiable}
    new_debt = sorted(entries - baseline)
    stale = sorted(baseline - entries)

    print(f"declared positions with a spelling : "
          f"{checked + len(unverifiable) + inbound}")
    print(f"inbound only (accepted, not written): {inbound}")
    print(f"emission read from the code        : {checked}")
    print(f"disagreements                      : {len(mismatch_at)} "
          f"(baseline allows {len([b for b in baseline if b.startswith('mismatch')])})")
    print(f"unreadable                         : {len(unverifiable)} "
          f"(baseline allows {len([b for b in baseline if b.startswith('unreadable')])})")

    if mismatches:
        print(f"\nThe spec and the server disagree on spelling ({len(mismatches)}):\n")
        for entry in mismatches:
            print("  " + entry.replace("\n", "\n  "))
        print("\nThe spec is what the frontend compares against. Fix the mapper,\n"
              "or the spec, but they cannot both be right.")

    if unverifiable:
        print(f"\nPositions this check cannot read ({len(unverifiable)}):\n")
        for location, why in sorted(unverifiable.items()):
            print(f"  {location}\n    {why}")
        print("\nAn unread position is an unchecked promise.")

    if new_debt:
        print(f"\n[FAIL] Not in the baseline ({len(new_debt)}):\n")
        for entry in new_debt:
            print("  " + entry)
        print("\nEither fix it, or record it deliberately:\n"
              "  python3 scripts/check-openapi-emission.py --update-baseline")

    if stale:
        print(f"\n[FAIL] Baseline entries that no longer apply ({len(stale)}):\n")
        for entry in stale:
            print("  " + entry)
        print("\nProgress that is not recorded gets undone. Run --update-baseline.")

    if new_debt or stale:
        return 1
    if mismatches or unverifiable:
        print("\nOK - nothing new; the entries above are recorded in the baseline.")
        return 0
    print("\nOK - every declared spelling matches what the server writes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
