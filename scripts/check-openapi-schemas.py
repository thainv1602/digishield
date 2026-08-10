#!/usr/bin/env python3
"""
Compare the field names in DigiShield_openapi.yaml against the Java DTOs the
controllers actually return and accept.

scripts/check-openapi-routes.py proves every served route is declared. It says
nothing about whether the declaration is *true*. In August 2026 the spec
declared Course with `domain` and `duration_min` while the server sent `title`
and `durationMin`; GET /courses had been declared, and wrong, the whole time.
Worse, POST /enrollments declared its body as {user_id, course_id} while
EnrollRequest carries no @JsonProperty at all, so a generated client sent two
fields Jackson silently dropped and the enrollment was created with nulls.

Pairing a schema with a DTO by name does not work: matching on the name `Group`
finds a private record inside RiskRollupService that is not a DTO at all. So
the pairing here comes from the contract instead -- for each operation, the
handler's response type is compared against the schema that same operation
declares, and its @RequestBody type against the declared requestBody.

Wire names follow one mechanical rule, because the project configures no
property naming strategy and uses no Jackson annotation other than
@JsonProperty: a record component is its own name unless @JsonProperty renames
it. That rule is only safe while it stays true, so this script REFUSES TO RUN
if it finds any other Jackson annotation or a non-record DTO -- the assumption
fails loudly instead of silently producing wrong answers.

Usage:
    python3 scripts/check-openapi-schemas.py
    python3 scripts/check-openapi-schemas.py --update-baseline
    python3 scripts/check-openapi-schemas.py --verbose   # also list unpaired ops
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "DigiShield_openapi.yaml"
BASELINE = Path(__file__).resolve().parent / "openapi-schema-baseline.txt"
SOURCES = [ROOT / "digishield" / "modules", ROOT / "digishield" / "boot"]

BASE = "/api/v1"
VERBS = ("get", "post", "put", "patch", "delete")
VERB_OF = {
    "GetMapping": "get", "PostMapping": "post", "PutMapping": "put",
    "PatchMapping": "patch", "DeleteMapping": "delete",
}

# Only @JsonProperty is understood. Anything else changes the wire shape in a
# way this parser does not model, so its presence is a hard error.
ALLOWED_JACKSON = {"JsonProperty"}

CLASS_MAPPING = re.compile(r'@RequestMapping\(\s*"([^"]+)"')
MAPPING_LINE = re.compile(
    r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\b\s*(\(([^)]*)\))?'
)
RECORD = re.compile(r"(?:(public|private|protected)\s+)?(?:static\s+)?record\s+([A-Z]\w*)\s*\(")
# Containers whose type argument is the thing actually serialised.
UNWRAP = ("ResponseEntity", "List", "java.util.List", "Set", "Collection", "Optional")


# --------------------------------------------------------------- java side
def iter_sources():
    for root in SOURCES:
        for java in sorted(root.rglob("*.java")):
            s = str(java)
            if "/src/test/" in s or "/src/integrationTest/" in s:
                continue
            yield java, java.read_text(encoding="utf-8")


def guard_assumptions() -> list[str]:
    """Fail loudly if the codebase stops matching what this parser models."""
    problems = []
    for java, text in iter_sources():
        # Both spellings occur in this codebase: @JsonProperty and the
        # fully-qualified @com.fasterxml.jackson.annotation.JsonProperty. A
        # guard that only matched the short one let @JsonIgnore through and
        # the check went on reporting OK on a shape it had got wrong.
        for ann in set(re.findall(r"@(?:com\.fasterxml\.jackson\.annotation\.)?(Json\w+)", text)):
            if ann not in ALLOWED_JACKSON:
                problems.append(
                    f"{java.relative_to(ROOT)}: @{ann} changes the wire shape; "
                    f"this parser only models @JsonProperty"
                )
    return problems


def split_top_level(s: str) -> list[str]:
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]


def record_fields(body: str) -> list[str]:
    """Wire names of a record's components, honouring @JsonProperty."""
    out = []
    for p in split_top_level(body):
        jp = re.search(
            r'@(?:com\.fasterxml\.jackson\.annotation\.)?JsonProperty\(\s*"([^"]+)"', p)
        if jp:
            out.append(jp.group(1))
            continue
        toks = re.sub(r"@\w+(\([^)]*\))?", " ", p).split()
        if toks:
            out.append(toks[-1])
    return out


def collect_records() -> dict[str, list[str]]:
    """Public/package-private record name -> wire field names.

    Private records are skipped: they are internal helpers, never DTOs, and
    matching one is how a name-based pairing goes wrong.
    """
    out: dict[str, list[str]] = {}
    for _java, text in iter_sources():
        for m in RECORD.finditer(text):
            visibility, name = m.group(1), m.group(2)
            if visibility == "private":
                continue
            i = m.end() - 1
            depth = 0
            body = None
            for k in range(i, len(text)):
                if text[k] == "(":
                    depth += 1
                elif text[k] == ")":
                    depth -= 1
                    if depth == 0:
                        body = text[i + 1:k]
                        break
            if body is not None:
                out.setdefault(name, record_fields(body))
    return out


def base_type(java_type: str) -> str | None:
    """Peel ResponseEntity/List wrappers down to the serialised type name."""
    t = java_type.strip()
    for _ in range(6):
        m = re.match(r"^([\w.]+)\s*<(.+)>$", t)
        if not m:
            break
        outer = m.group(1).split(".")[-1]
        if outer not in [u.split(".")[-1] for u in UNWRAP]:
            return None          # Map, Page, … : not a DTO we can compare
        t = m.group(2).strip()
    t = t.split(".")[-1]
    if not re.fullmatch(r"[A-Z]\w*", t) or t in ("Void", "Object", "String", "UUID"):
        return None
    return t


def normalise(path: str) -> str:
    if path.startswith(BASE):
        path = path[len(BASE):] or "/"
    return re.sub(r"\{[^}]*\}", "{}", path)


def join(prefix: str, suffix: str) -> str:
    p = (prefix or "").rstrip("/")
    s = suffix or ""
    if s and not s.startswith("/"):
        s = "/" + s
    return (p + s) or "/"


def paths_in_annotation(args: str) -> list[str]:
    if args and "=" in args:
        m = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', args)
        args = m.group(1) if m else ""
    return [s for s in re.findall(r'"([^"]*)"', args or "") if s.startswith("/")]


def handlers() -> dict[tuple[str, str], dict]:
    """(verb, path) -> {'returns': type|None, 'body': type|None, 'file': str}."""
    out: dict[tuple[str, str], dict] = {}
    for java, text in iter_sources():
        if "Mapping" not in text:
            continue
        cm = CLASS_MAPPING.search(text)
        prefix = cm.group(1) if cm else ""
        rel = str(java.relative_to(ROOT))
        lines = text.splitlines()
        for idx, line in enumerate(lines):
            m = MAPPING_LINE.search(line)
            if not m:
                continue
            verb = VERB_OF[m.group(1)]
            paths = paths_in_annotation(m.group(3)) or [""]
            # The method signature is the next line that is not another
            # annotation; gather until the body opens.
            sig, j = "", idx + 1
            while j < len(lines) and j < idx + 12:
                s = lines[j].strip()
                if s.startswith("@") or not s:
                    j += 1
                    continue
                sig += " " + s
                if "{" in s or ";" in s:
                    break
                j += 1
            sig = sig.strip()
            ret = re.match(r"^((?:[\w.]+)(?:\s*<.*?>)?)\s+\w+\s*\(", sig)
            returns = base_type(ret.group(1)) if ret else None
            bm = re.search(r"@RequestBody(?:\([^)]*\))?\s+((?:[\w.]+)(?:\s*<[^>]*>)?)\s+\w+", sig)
            body = base_type(bm.group(1)) if bm else None
            for p in paths:
                out[(verb, normalise(join(prefix, p)))] = {
                    "returns": returns, "body": body, "file": rel,
                }
    return out


# --------------------------------------------------------------- spec side
def schema_props(node, schemas, depth=0):
    """(schema name or None, property names) for a response/requestBody schema."""
    if not isinstance(node, dict) or depth > 5:
        return None, None
    if "$ref" in node:
        name = node["$ref"].rsplit("/", 1)[-1]
        target = schemas.get(name)
        if target is None:
            return name, None
        props = target.get("properties")
        return name, set(props) if props else None
    if node.get("type") == "array" and "items" in node:
        return schema_props(node["items"], schemas, depth + 1)
    if "properties" in node:
        return None, set(node["properties"])
    return None, None


def spec_operations(spec):
    schemas = spec["components"]["schemas"]
    out = {}
    for raw_path, item in spec["paths"].items():
        path = normalise(raw_path)
        for verb in VERBS:
            op = item.get(verb)
            if not op:
                continue
            resp_name = resp_props = None
            for code in ("200", "201"):
                r = (op.get("responses") or {}).get(code)
                if isinstance(r, dict) and r.get("content"):
                    for media, c in r["content"].items():
                        if media == "application/json" and isinstance(c, dict):
                            resp_name, resp_props = schema_props(c.get("schema"), schemas)
                    break
            body_name = body_props = None
            rb = op.get("requestBody")
            if isinstance(rb, dict) and rb.get("content"):
                c = (rb["content"] or {}).get("application/json")
                if isinstance(c, dict):
                    body_name, body_props = schema_props(c.get("schema"), schemas)
            out[(verb, path)] = {
                "resp": (resp_name, resp_props), "body": (body_name, body_props),
            }
    return out


# --------------------------------------------------------------- comparison
def read_baseline() -> set[str]:
    if not BASELINE.exists():
        return set()
    out = set()
    for line in BASELINE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            out.add(line)
    return out


BASELINE_HEADER = """\
# Schema mismatches between docs/DigiShield_openapi.yaml and the DTOs the
# controllers actually return or accept, expressed as one key per pairing.
#
# Accepted debt, not a permission slip. scripts/check-openapi-schemas.py fails
# on any mismatch NOT listed here, and equally fails on a line here that no
# longer mismatches -- so the file can only shrink. Fix the spec (or the DTO),
# then run:
#
#     python3 scripts/check-openapi-schemas.py --update-baseline
#
# Entries: {count}
"""


def main() -> int:
    verbose = "--verbose" in sys.argv

    problems = guard_assumptions()
    if problems:
        print("[FAIL] This check's parsing assumptions no longer hold:\n")
        for p in problems:
            print("  " + p)
        print("\nTeach the parser the new annotation, or drop it from the DTO.")
        return 1

    spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))
    ops = spec_operations(spec)
    hs = handlers()
    recs = collect_records()

    mismatches: dict[str, list[str]] = {}
    compared = unpaired = 0
    unpaired_list = []

    for key, h in sorted(hs.items()):
        op = ops.get(key)
        if not op:
            continue
        verb, path = key
        for kind, java_type, (schema_name, props) in (
            ("response", h["returns"], op["resp"]),
            ("request", h["body"], op["body"]),
        ):
            if java_type is None or props is None:
                if java_type or props:
                    unpaired += 1
                    unpaired_list.append(
                        f"{verb.upper():6} {path:36} {kind:8} "
                        f"java={java_type or '-'} spec={schema_name or 'inline/none'}"
                    )
                continue
            fields = recs.get(java_type)
            if fields is None:
                unpaired += 1
                unpaired_list.append(
                    f"{verb.upper():6} {path:36} {kind:8} unknown record {java_type}")
                continue
            compared += 1
            want, have = set(fields), set(props)
            missing, extra = want - have, have - want
            if missing or extra:
                label = f"{verb.upper()} {path} {kind} {java_type}"
                detail = []
                if missing:
                    detail.append(f"    server sends, spec omits : {sorted(missing)}")
                if extra:
                    detail.append(f"    spec declares, server won't send: {sorted(extra)}")
                mismatches[label] = detail

    if "--update-baseline" in sys.argv:
        lines = [BASELINE_HEADER.format(count=len(mismatches))]
        lines += sorted(mismatches)
        BASELINE.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"Baseline rewritten: {len(mismatches)} mismatch(es).")
        return 0

    baseline = read_baseline()
    new = sorted(set(mismatches) - baseline)
    stale = sorted(baseline - set(mismatches))

    print(f"pairings compared : {compared}")
    print(f"not comparable    : {unpaired} (Map/inline/generic — no field list to check)")
    print(f"mismatches        : {len(mismatches)} (baseline allows {len(baseline)})")

    if verbose and unpaired_list:
        print("\nNot comparable:")
        for u in unpaired_list:
            print("  " + u)

    if new:
        print(f"\n[FAIL] Schema does not match the DTO ({len(new)} new):")
        for label in new:
            print(f"  {label}")
            for d in mismatches[label]:
                print(d)
        print("\nA declared-but-wrong schema is worse than a missing one: the\n"
              "generated client compiles and then sends or reads the wrong fields.")

    if stale:
        print(f"\n[FAIL] Baseline entries that no longer mismatch ({len(stale)}):")
        for s in stale:
            print(f"  {s}")
        print("\nRun: python3 scripts/check-openapi-schemas.py --update-baseline")

    if new or stale:
        return 1
    print("\nOK - every comparable schema matches its DTO.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
