#!/usr/bin/env python3
"""
Seeds LOCAL development data through the public admin API: company bank accounts, container
stock with generated photos and survey reports, and published investment plans for the marketplace.

All names are fictional. Uses only the Python standard library.

    python3 scripts/seed-demo-data.py            # reads BOOTSTRAP_ADMIN_* from .env
    API_URL=http://localhost:8080 python3 scripts/seed-demo-data.py

Re-running is safe: accounts, containers and plans that already exist are skipped.
"""
import json
import os
import struct
import sys
import uuid
import zlib
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

def call(method, path, token=None, body=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = request.Request(API + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with request.urlopen(req) as resp:
            text = resp.read().decode()
            return resp.status, json.loads(text) if text else None
    except error.HTTPError as exc:
        text = exc.read().decode()
        return exc.code, json.loads(text) if text.startswith("{") else text


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for name, (filename, ctype, data) in files.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                     f"Content-Type: {ctype}\r\n\r\n".encode() + data + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# --------------------------------------------------------------------- ISO 6346 numbers

LETTERS = dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                   [10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 34, 35, 36, 37, 38]))


def container_number(prefix10):
    total = sum((LETTERS[c] if c.isalpha() else int(c)) << i for i, c in enumerate(prefix10))
    return prefix10 + str(total % 11 % 10)


# ------------------------------------------------------------------ generated imagery

def png(width, height, pixel):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(pixel(x, y))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 6)) + chunk(b"IEND", b""))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def container_photo(body_colour, variant):
    """A container in a yard at dusk; `variant` shifts the framing between photos."""
    w, h = 960, 600
    horizon = int(h * (0.6 if variant == 0 else 0.66))
    left, right = (int(w * 0.14), int(w * 0.86)) if variant == 0 else (int(w * 0.04), int(w * 0.72))
    top, bottom = int(h * 0.3), int(h * 0.82)
    sky_top, sky_low, ground_top, ground_low = (26, 33, 46), (238, 176, 104), (52, 44, 38), (14, 12, 11)
    light = tuple(min(255, c + 22) for c in body_colour)
    dark = tuple(max(0, c - 18) for c in body_colour)

    def pixel(x, y):
        if left <= x < right and top <= y < bottom:
            if y < top + 8:
                return bytes(lerp(light, (210, 150, 80), 0.5))  # sunlit top rail
            if left + int((right - left) * 0.08) < x < left + int((right - left) * 0.2) and \
                    top + int((bottom - top) * 0.35) < y < top + int((bottom - top) * 0.55):
                return bytes((201, 162, 76))  # gold brand plate
            return bytes(light if ((x - left) // 9) % 2 == 0 else dark)  # corrugation
        if y < horizon:
            return bytes(lerp(sky_top, sky_low, (y / horizon) ** 1.6))
        return bytes(lerp(ground_top, ground_low, (y - horizon) / (h - horizon)))

    return png(w, h, pixel)


SURVEY_PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
              b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
              b"4 0 obj<</Length 66>>stream\nBT /F1 18 Tf 60 760 Td (Container survey report - demo data) Tj ET\nendstream endobj\n"
              b"5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")

# ------------------------------------------------------------------------------- data

RISK = ("Payouts depend on the platform leasing out its containers. Containers can be damaged, lost or off-hire, "
        "and the value of a container at the end of its lease varies with the shipping market.")
TERMS = ("1. You buy whole containers at the plan's price per container.\n"
         "2. Each container is assigned to you by number once your payment is confirmed and leased for the plan's tenure.\n"
         "3. Every month you receive the plan's rent plus 100 / tenure % of the price back, into your SeaLease wallet,\n"
         "   so the whole price is returned by the end of the lease.\n"
         "(Demo terms for local development.)")


def rupees(amount):
    """Indian digit grouping: 250000 -> 2,50,000."""
    digits = str(int(amount))
    head, tail = digits[:-3], digits[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    return "₹" + ",".join(([head] if head else []) + groups + [tail])


def box(serial, condition, year, location, country, colour, cbm, gross, tare):
    return dict(serial=serial, condition=condition, year=year, location=location, country=country, colour=colour,
                cbm=cbm, gross=gross, tare=tare)


# Investment plans and the containers in stock for each (by type). Prices in rupees.
PLANS = [
    dict(type="DRY_20FT", months=16, price="250000", rent="2", title="20ft dry container plan",
         summary="The entry point: a 20ft dry container leased for 16 months.",
         stock=[box(f"SLSU1005{n:02d}", "NEW", 2025, "Nhava Sheva (JNPT), Mumbai", "IN", (46, 90, 60), 33.2, 30480, 2200)
                for n in range(1, 6)]),
    dict(type="DRY_40FT", months=18, price="400000", rent="2.25", title="40ft dry container plan",
         summary="A cargo-worthy 40ft dry container serving India's export lanes, on an 18-month lease.",
         stock=[box(f"SLSU1004{n:02d}", "CARGO_WORTHY", 2022, "Mundra Port, Gujarat", "IN", (28, 58, 108), 67.7, 30480, 3750)
                for n in range(1, 6)]),
    dict(type="HIGH_CUBE_40FT", months=16, price="450000", rent="2.25", title="40ft high cube plan",
         summary="A new 40ft high cube for high-volume cargo.",
         stock=[box(f"SLSU1001{n:02d}", "NEW", 2025, "Chennai Port", "IN", (22, 22, 24), 76.3, 30480, 3940)
                for n in range(1, 5)]),
    dict(type="REEFER_20FT", months=12, price="1200000", rent="2.5", title="20ft refrigerated container plan",
         summary="A reefer for perishable cargo: a higher rent over a shorter 12-month lease.",
         stock=[box(f"SLSU1002{n:02d}", "CARGO_WORTHY", 2023, "Visakhapatnam Port", "IN", (206, 206, 200), 28.3, 30480, 3080)
                for n in range(1, 4)]),
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
        for index, c in enumerate(spec["stock"]):
            number = container_number(c["serial"])
            status, container = call("POST", "/api/v1/admin/containers", token, {
                "containerNumber": number, "containerType": spec["type"], "condition": c["condition"],
                "capacityCbm": c["cbm"], "maxGrossKg": c["gross"], "tareKg": c["tare"],
                "manufactureYear": c["year"], "manufacturer": "CIMC", "currentLocation": c["location"],
                "locationCountry": c["country"], "notes": "Seeded demo data"})
            if status == 409:
                print(f"skip  {number} (already registered)")
                continue
            if status != 201:
                sys.exit(f"Container {number} failed ({status}): {container}")
            container_id = container["container"]["id"]
            uploads = [("CONTAINER_SURVEY_REPORT", "Pre-lease survey", "survey.pdf", "application/pdf", SURVEY_PDF, "false")]
            if index < 2:
                uploads.append(("CONTAINER_PHOTO", "Yard view" if index == 0 else "Side view", f"photo-{index}.png",
                                "image/png", container_photo(c["colour"], index), "true"))
            for purpose, title, filename, ctype, data, visible in uploads:
                body, ctype_header = multipart({}, {"file": (filename, ctype, data)})
                query = parse.urlencode({"purpose": purpose, "title": title, "visibleToInvestors": visible})
                status, result = call("POST", f"/api/v1/admin/containers/{container_id}/documents?{query}", token,
                                      raw=body, content_type=ctype_header)
                if status != 201:
                    sys.exit(f"Upload {title} for {number} failed ({status}): {result}")
            print(f"ok    {number}  {spec['type']}")

        if spec["title"] in titles:
            print(f"skip  plan '{spec['title']}' (already exists)")
            continue
        status, product = call("POST", "/api/v1/admin/investment-products", token, {
            "containerType": spec["type"], "title": spec["title"], "summary": spec["summary"],
            "description": f"{spec['summary']}\n\nBuy one or more containers at {rupees(spec['price'])} each. Each container "
                           f"is assigned to you by its container number once your payment is confirmed and leased "
                           f"for {spec['months']} months. Every month you receive {spec['rent']}% of the price as rent "
                           f"plus {100 / spec['months']:.2f}% of it back, so the whole price is returned by the end.",
            "currency": "INR", "price": spec["price"], "monthlyRentPercent": spec["rent"],
            "tenureMonths": spec["months"],
            "riskDisclosure": RISK, "termsAndConditions": TERMS})
        if status != 201:
            sys.exit(f"Plan {spec['title']} failed ({status}): {product}")
        status, published = call("POST", f"/api/v1/admin/investment-products/{product['id']}/publish", token)
        if status != 200:
            sys.exit(f"Publishing {product['code']} failed ({status}): {published}")
        created += 1
        print(f"ok    {product['code']}  {spec['type']:<15} {spec['title']}")

    print(f"\nSeeded {created} plan(s). Open http://localhost:3000/marketplace")


if __name__ == "__main__":
    main()
