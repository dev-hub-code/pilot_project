#!/usr/bin/env python3
"""Turns results.json into one scenario file per role, plus RESULTS.md."""
import json, pathlib, collections, re

HERE = pathlib.Path(__file__).resolve().parent
results = json.loads((HERE / "results.json").read_text())
out = HERE / "scenarios"
for old in out.glob("*.md"):
    old.unlink()


def title(name):
    return name.replace("_", " ").title()


def rule(text):
    return ", ".join(re.findall(r"'(\w+)'", text)) or ("Any signed-in user" if "isAuthenticated" in text else text)


def short(path):
    return path.replace("/api/v1/admin", "") or "/"


def area(path):
    parts = short(path).strip("/").split("/")
    return parts[0] or "dashboard"


def word(allowed):
    return "Allowed" if allowed else "Refused"


rows, problems = [], []
for r in results:
    name = r["role"]
    bad_api = [a for a in r["api"] if not a["verdict"].startswith("PASS")]
    bad_pages = [p for p in r["pages"] if not p["verdict"].startswith("PASS")]
    for a in bad_api:
        problems.append((name, f'{a["method"]} {short(a["path"])}', a["verdict"], f'HTTP {a["status"]}'))
    for p in bad_pages:
        problems.append((name, p["href"], p["verdict"], f'{p["outcome"]}, ended at {p["final"]}'))
    if name == "ANONYMOUS":
        rows.append((name, 0, len(r["api"]), "-", "-", len(bad_api)))
        continue
    allowed = [a for a in r["api"] if a["expected"]]
    opened = [p for p in r["pages"] if p["outcome"] == "open"]
    rows.append((name, len(allowed), len(r["api"]) - len(allowed), len(opened), len(r["menu"]), len(bad_api) + len(bad_pages)))

    L = [f"# Scenario: {title(name)}", "", f'`{name}`' + (f' — {r["description"]}' if r.get("description") else ""), "",
         "**Given** an employee who holds only this role, signed in with their own account.",
         "**When** they use every admin page and call every admin API endpoint.",
         "**Then** they get exactly what the role's permissions allow, and are refused everything else.", "",
         f"**Result: {'PASS' if not bad_api and not bad_pages else 'FAIL'}** — "
         f"{len(allowed)} of {len(r['api'])} endpoints allowed, {len(opened)} of {len(r['pages'])} pages open, "
         f"{len(bad_api) + len(bad_pages)} problem(s).", "",
         f"## Permissions ({len(r['permissions'])})", "",
         ", ".join(f"`{p}`" for p in sorted(r["permissions"])) or "None", "",
         "## Pages", "", "| Page | Should open | In menu | What happened | Result |", "|---|---|---|---|---|"]
    for p in r["pages"]:
        menu = ("Yes" if p["inMenu"] else "No") if p["menu"] else "n/a"
        L.append(f'| {p["label"]} (`{p["href"]}`) | {"Yes" if p["expected"] else "No"} | {menu} | {p["outcome"]} | {p["verdict"]} |')
    L += ["", "## What the role can do through the API", ""]
    groups = collections.OrderedDict()
    for a in r["api"]:
        groups.setdefault(area(a["path"]), []).append(a)
    for group, items in groups.items():
        ok = sum(a["expected"] for a in items)
        L += [f"### {group} ({ok} of {len(items)} allowed)", "",
              "| Call | Needs | Should be | HTTP | Result |", "|---|---|---|---|---|"]
        for a in items:
            L.append(f'| `{a["method"]} {short(a["path"])}` | {rule(a["rule"])} | {word(a["expected"])} | {a["status"]} | {a["verdict"]} |')
        L.append("")
    (out / f"{name}.md").write_text("\n".join(L))

total = sum(len(r["api"]) + len(r["pages"]) for r in results)
verdicts = collections.Counter(v["verdict"] for r in results for v in r["api"] + r["pages"])
S = ["# Role access results", "", f"{len(results) - 1} roles and one anonymous caller, {total} checks.", "",
     "| Verdict | Checks |", "|---|---|"] + [f"| {k} | {v} |" for k, v in verdicts.most_common()]
S += ["", "## Roles", "", "| Role | Endpoints allowed | Endpoints refused | Pages open | Menu links | Problems |", "|---|---:|---:|---:|---:|---:|"]
for n, a, d, o, m, b in rows:
    link = n if n == "ANONYMOUS" else f"[{n}](scenarios/{n}.md)"
    S.append(f"| {link} | {a} | {d} | {o} | {m} | {b} |")
S += ["", f"## Problems ({len(problems)})", ""]
if problems:
    S += ["| Role | Where | Verdict | Detail |", "|---|---|---|---|"] + [f"| {a} | `{b}` | {c} | {d} |" for a, b, c, d in problems]
else:
    S.append("None.")
(HERE / "RESULTS.md").write_text("\n".join(S) + "\n")
print(verdicts.most_common(), "scenarios:", len(list(out.glob("*.md"))))
