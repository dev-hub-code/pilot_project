#!/usr/bin/env python3
"""
Seeds LOCAL development data through the public admin API: company bank accounts and published
investment plans.

All names are fictional. Uses only the Python standard library.

    python3 scripts/seed-demo-data.py            # reads BOOTSTRAP_ADMIN_* from .env
    API_URL=http://localhost:8080 python3 scripts/seed-demo-data.py

Re-running is safe: accounts and plans that already exist are skipped.
"""
import json
import os
import sys
from pathlib import Path
from urllib import error, parse, request

ROOT = Path(__file__).resolve().parent.parent
API = os.environ.get("API_URL", "http://localhost:8080").rstrip("/")

if parse.urlparse(API).hostname not in ("localhost", "127.0.0.1"):
    sys.exit(f"Refusing to seed demo data into {API}: local development only.")


def env(name):
    if name in os.environ:
        return os.environ[name]
    for line in (ROOT / ".env").read_text().splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1]
    sys.exit(f"{name} is not set (environment or .env)")


# --------------------------------------------------------------------------- HTTP helpers

def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = request.Request(API + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with request.urlopen(req) as resp:
            text = resp.read().decode()
            return resp.status, json.loads(text) if text else None
    except error.HTTPError as exc:
        text = exc.read().decode()
        return exc.code, json.loads(text) if text.startswith("{") else text


# ------------------------------------------------------------------------------- data

# Investment plans. Prices in rupees.
PLANS = [
    dict(months=16, price="250000", rent="2", title="Starter plan",
         summary="The entry point: a 16-month term."),
    dict(months=18, price="400000", rent="2.25", title="Growth plan",
         summary="A higher return over an 18-month term."),
    dict(months=16, price="450000", rent="2.25", title="Premium plan",
         summary="A larger unit at a higher return."),
    dict(months=12, price="1200000", rent="2.5", title="Short-term plan",
         summary="The highest return over a shorter 12-month term."),
]


# Demo collection accounts investors can choose when paying by bank. Not real accounts.
COMPANY_BANK_ACCOUNTS = [
    {"accountName": "SeaLease Investments Pvt Ltd - Client Collections", "bankName": "HDFC Bank",
     "branch": "Fort, Mumbai", "accountNumber": "50200012345678", "ifscCode": "HDFC0000060",
     "upiId": "sealease@hdfcbank"},
    {"accountName": "SeaLease Investments Pvt Ltd - Client Collections", "bankName": "ICICI Bank",
     "branch": "Bandra Kurla Complex, Mumbai", "accountNumber": "000405123456", "ifscCode": "ICIC0000004",
     "upiId": None},
]


def main():
    status, login = call("POST", "/api/v1/auth/login",
                         body={"email": env("BOOTSTRAP_ADMIN_EMAIL"), "password": env("BOOTSTRAP_ADMIN_PASSWORD")})
    if status != 200:
        sys.exit(f"Admin login failed ({status}): {login}")
    token = login["accessToken"]
    created = 0

    for account in COMPANY_BANK_ACCOUNTS:
        status, result = call("POST", "/api/v1/admin/company-bank-accounts", token, account)
        if status == 409:
            print(f"skip  {account['bankName']} {account['accountNumber']} (already seeded)")
        elif status != 201:
            sys.exit(f"Company bank account {account['bankName']} failed ({status}): {result}")
        else:
            print(f"ok    {account['bankName']} {account['accountNumber']}  (company bank account)")

    status, existing = call("GET", "/api/v1/admin/investment-products?size=100", token)
    if status != 200:
        sys.exit(f"Listing plans failed ({status}): {existing}")
    titles = {p["title"] for p in existing["content"] if p["status"] in ("DRAFT", "OPEN")}

    for spec in PLANS:
        if spec["title"] in titles:
            print(f"skip  plan '{spec['title']}' (already exists)")
            continue
        status, product = call("POST", "/api/v1/admin/investment-products", token, {
            "audience": "ONLINE", "title": spec["title"], "summary": spec["summary"], "currency": "INR",
            "tenureYears": max(1, spec["months"] // 12), "annualReturnPercent": str(float(spec["rent"]) * 12), "minUnits": 1})
        if status != 201:
            sys.exit(f"Plan {spec['title']} failed ({status}): {product}")
        status, published = call("POST", f"/api/v1/admin/investment-products/{product['id']}/publish", token)
        if status != 200:
            sys.exit(f"Publishing {product['code']} failed ({status}): {published}")
        created += 1
        print(f"ok    {product['code']}  {spec['title']}")

    print(f"\nSeeded {created} plan(s). Open http://localhost:3000/invest")


if __name__ == "__main__":
    main()
