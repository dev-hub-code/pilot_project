#!/usr/bin/env python3
"""Reads every admin endpoint and its @PreAuthorize rule from the controllers, and every admin page from the menu
registry, into catalog.json. The scenarios come from here, so a new endpoint or page is covered automatically."""
import json, re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / "backend/src/main/java/com/sealease/backend"
U = "00000000-0000-4000-8000-000000000001"  # exists nowhere, so a permitted call changes nothing

ADJ = {"userId": U, "currency": "INR", "direction": "CREDIT", "amount": "1", "reason": "e2e"}
REFUND = {"reference": "E2E", "reason": "e2e"}
REASON = {"reason": "e2e"}
LEAD = {"firstName": "E2E", "email": "lead@e2e.test", "interest": "@LEAD_INTEREST"}
PRODUCT = {"audience": "ONLINE", "title": "E2E", "summary": "e2e", "currency": "INR", "tenureYears": 1,
           "annualReturnPercent": "15", "minUnits": 1}
BANK = {"accountName": "E2E", "bankName": "E2E", "accountNumber": "123456789", "ifscCode": "HDFC0001234"}
# A valid body for each write. Spring validates a body before it checks permission, so without one a refused role
# gets 400, not 403, and the check would prove nothing. "@UNIQUE" is replaced on every call.
BODIES = {
    "agreement-template": {"title": "E2E", "body": "e2e"},
    "agreement-template/preview": {"title": "E2E", "body": "e2e"},
    "support/tickets/{ticketId}/status": {"status": "RESOLVED"},
    "support/tickets/{ticketId}/priority": {"priority": "@TICKET_PRIORITY"},
    "support/tickets/{ticketId}/assign": {"assigneeId": U},
    "support/tickets/{ticketId}/team": {"team": "@SUPPORT_TEAM"},
    "ledger/adjustments": ADJ,
    "staff": {"email": "@UNIQUE@e2e.test", "firstName": "E2E", "lastName": "Probe", "roles": ["SUPPORT_EXECUTIVE"]},
    "roles": {"name": "E2E_@UNIQUEUPPER", "description": "e2e", "permissions": ["REPORT_VIEW"]},
    "roles/{id}": {"description": "e2e", "permissions": ["REPORT_VIEW"], "version": 0},
    "users/{userId}/roles": {"roles": ["SUPPORT_EXECUTIVE"]},
    "referral-rates": {"percents": ["1", "1", "1", "1"], "oneTimePercent": "1", "reason": "e2e"},
    "users/{userId}/referrer": {"referrerId": U, "reason": "e2e"},
    "bank-accounts/{accountId}/reject": REASON,
    "payments/{paymentId}/confirm": {"externalReference": "E2E", "amountReceived": "1"},
    "payments/{paymentId}/reject": REASON,
    "payments/{paymentId}/refund": REFUND,
    "company-bank-accounts": BANK,
    "company-bank-accounts/{accountId}": BANK,
    "settings/rent-credit": {"intervalMinutes": 60},
    "users/{userId}/suspend": REASON, "users/{userId}/reactivate": REASON,
    "users/{userId}/disable": REASON, "users/{userId}/enable": REASON,
    "withdrawals/{withdrawalId}/resolve": {"outcome": "@RESOLVE_OUTCOME"},
    "users/{userId}/referral-mode": {"mode": "BANK_TRANSFER"},
    "settings/withdrawals": {"minimumAmount": "1000"},
    "finance-tasks/{withdrawalId}": {"outcome": "@TASK_OUTCOME"},
    "investors": {"email": "@UNIQUE@e2e.test", "firstName": "E2E", "lastName": "Probe", "phone": "98@DIGITS",
                  "phoneCountry": "IN", "signupsJoinNetwork": False},
    "departments": {"code": "E2E_@UNIQUEUPPER", "name": "E2E"},
    "departments/{id}": {"name": "E2E", "version": 0},
    "departments/{id}/head": {"userId": U},
    "departments/{id}/employees": {"userId": U, "primary": False},
    "approvals": {"type": "LEDGER_ADJUSTMENT", "reason": "e2e", "adjustment": ADJ},
    "approvals/{id}/reject": REASON,
    "terms": {"title": "E2E", "body": "e2e"},
    "investment-products": PRODUCT,
    "investment-products/{productId}": PRODUCT,
    "investment-products/{productId}/cancel": REASON,
    "demo-accounts": {"userId": U, "startsOn": "2026-01-01", "endsOn": "2026-12-31"},
    "demo-accounts/{userId}": {"startsOn": "2026-01-01", "endsOn": "2026-12-31"},
    "demo-accounts/{userId}/orders": {"productId": U, "amount": "1", "startsOn": "2026-01-01"},
    "demo-accounts/{userId}/members": {"name": "E2E", "joinedOn": "2026-01-01"},
    "kyc/{submissionId}/reject": REASON,
    "leads": LEAD, "leads/{leadId}": LEAD,
    "leads/{leadId}/stage": {"stage": "@LEAD_STAGE"},
    "leads/{leadId}/activities": {"type": "@ACTIVITY_TYPE", "body": "e2e"},
    "leads/{leadId}/assign": {"ownerId": U},
}
ENUMS = {"LEAD_INTEREST": "LeadInterest", "TICKET_PRIORITY": "TicketPriority", "SUPPORT_TEAM": "SupportTeam",
         "RESOLVE_OUTCOME": ("ResolveFailedRequest", "Outcome"), "TASK_OUTCOME": ("FinanceTaskUpdateRequest", "Outcome"),
         "LEAD_STAGE": "LeadStage", "ACTIVITY_TYPE": "ActivityType"}


# Required query parameters: like bodies, these are checked before permission.
DATES = "from=2026-01-01&to=2026-01-31"
QUERIES = {("GET", "reports/financial-summary"): DATES, ("GET", "reports/statement"): DATES + "&userId=" + U,
           ("POST", "withdrawal-runs"): "kind=RENT", ("GET", "agreements"): "orderId=" + U}


def enum_first(spec):
    owner, name = (spec, spec) if isinstance(spec, str) else spec
    for f in JAVA.rglob(owner + ".java"):
        m = re.search(r"enum " + name + r"\s*\{\s*(?:/\*\*.*?\*/\s*)?(\w+)", f.read_text(), re.S)
        if m:
            return m.group(1)
    raise SystemExit("no enum " + name)


FIRST = {k: enum_first(v) for k, v in ENUMS.items()}


def fill(v):
    if isinstance(v, dict):
        return {k: fill(x) for k, x in v.items()}
    if isinstance(v, list):
        return [fill(x) for x in v]
    if isinstance(v, str) and v.startswith("@") and v[1:] in FIRST:
        return FIRST[v[1:]]
    return v


def endpoints():
    found = []
    for f in JAVA.rglob("*Controller.java"):
        t = f.read_text()
        base = re.search(r'@RequestMapping\("([^"]+)"\)', t)
        base = base.group(1) if base else ""
        head = t[: t.index("public class")]
        class_rule = re.search(r"@PreAuthorize\(([^\n]+)\)\s*\n", head)
        consts = dict(re.findall(r'String (\w+) = "([^"]+)"', t))
        pattern = r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^)]*)\))?([\s\S]*?)public [^\n(]*?(\w+)\(([\s\S]*?)\) (?:throws[^{]*)?\{"
        for m in re.finditer(pattern, t):
            verb, arg, mid, name, params = m.groups()
            arg = (arg or "").strip()
            lit = re.search(r'"([^"]*)"', arg)
            sub = lit.group(1) if lit else ""
            for k, v in consts.items():
                if arg.startswith(k):
                    sub = v + sub
            path = sub if sub.startswith("/api") else base + sub
            if not path.startswith("/api/v1/admin"):
                continue
            rule = re.search(r"@PreAuthorize\((.+)\)\s*$", mid, re.M)
            rule = rule.group(1) if rule else (class_rule.group(1) if class_rule else None)
            rule = consts.get(rule, rule).strip('"')
            short = path[len("/api/v1/admin/"):] if len(path) > len("/api/v1/admin") else ""
            e = {"method": verb.upper(), "path": path, "rule": rule, "operation": f.stem + "." + name}
            if "MultipartFile" in params or "@RequestPart" in params:
                e["multipart"] = True
            elif "@RequestBody" in params:
                if short not in BODIES:
                    raise SystemExit("no sample body for " + short)
                e["body"] = fill(BODIES[short])
            query = QUERIES.get((verb.upper(), short))
            if query:
                e["query"] = query
            found.append(e)
    return sorted(found, key=lambda e: (e["path"], e["method"]))


def pages():
    t = (ROOT / "frontend/src/components/layout/admin-nav.ts").read_text()
    items = []
    for m in re.finditer(r'\{ id: "[^"]+", label: "([^"]+)", href: "([^"]+)", icon: "[^"]+", category: "([^"]+)",\s*requires: \[([^\]]*)\]', t):
        label, href, category, req = m.groups()
        items.append({"label": label, "href": href, "category": category, "menu": True,
                      "requires": re.findall(r"P\.(\w+)", req)})
    for href, req in [("/admin/roles/new", ["ROLE_MANAGE"]), ("/admin/staff/new", ["USER_ROLE_ASSIGN+ROLE_VIEW"]),
                      ("/admin/users/new-investor", ["INVESTOR_ACCOUNT_CREATE"]), ("/admin/leads/new", ["LEAD_CREATE"]),
                      ("/admin/products/new", ["INVESTMENT_CREATE"]), ("/admin/withdrawals/batches", ["WITHDRAWAL_VIEW"])]:
        items.append({"label": href, "href": href, "category": "Not in the menu", "menu": False, "requires": req})
    return items


catalog = {"endpoints": endpoints(), "pages": pages()}
(ROOT / "e2e/catalog.json").write_text(json.dumps(catalog, indent=1) + "\n")
print(len(catalog["endpoints"]), "endpoints,", len(catalog["pages"]), "pages")
