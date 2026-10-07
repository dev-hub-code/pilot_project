#!/usr/bin/env python3
"""
Seeds LOCAL development data through the public admin API: containers with generated photos and
a survey report, plus published investment offerings for the marketplace.

All names (lessees, etc.) are fictional. Uses only the Python standard library.

    python3 scripts/seed-demo-data.py            # reads BOOTSTRAP_ADMIN_* from .env
    API_URL=http://localhost:8080 python3 scripts/seed-demo-data.py

Re-running is safe: containers that already exist are skipped.
"""
import json
import os
import struct
import sys
import uuid
import zlib
from datetime import datetime, timedelta, timezone
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

CLOSES = (datetime.now(timezone.utc) + timedelta(days=90)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
RISK = ("Rental income depends on the lessee continuing to pay. Containers can be damaged, lost or off-hire between "
        "leases, and resale values vary with the shipping market. You may receive less than expected.")
TERMS = ("1. Your ownership is proportional to the amount you invest.\n2. Rental income is distributed after costs.\n"
         "3. Investments are held until the end of the term.\n(Demo terms for local development.)")

OFFERINGS = [
    dict(serial="SLSU100101", type="HIGH_CUBE_40FT", condition="NEW", cbm=76.3, gross=30480, tare=3940, year=2025,
         location="Port of Rotterdam, Maasvlakte", country="NL", colour=(22, 22, 24), investment="RETAIL",
         title="40ft High Cube on a 3-year lease", lessee="Northsea Container Lines (fictional)", currency="USD",
         price="50000", minimum="1000", increment="500", maximum=None, rental="520", frequency="MONTHLY", months=36,
         risk="MEDIUM", summary="A new 40ft high cube on long-term lease with a European carrier."),
    dict(serial="SLSU100202", type="REEFER_20FT", condition="CARGO_WORTHY", cbm=28.3, gross=30480, tare=3080, year=2022,
         location="Port of Singapore, Pasir Panjang", country="SG", colour=(206, 206, 200), investment="RETAIL",
         title="20ft refrigerated container, Asia trade lanes", lessee="Straits Cold Chain (fictional)", currency="USD",
         price="38000", minimum="500", increment="250", maximum=None, rental="410", frequency="MONTHLY", months=24,
         risk="MEDIUM", summary="A reefer serving perishable cargo on intra-Asia routes."),
    dict(serial="SLSU100303", type="DRY_40FT", condition="CARGO_WORTHY", cbm=67.7, gross=30480, tare=3750, year=2023,
         location="Jebel Ali Port", country="AE", colour=(112, 50, 34), investment="HNI",
         title="Standalone 40ft dry container, Gulf routes", lessee="Gulf Freight Partners (fictional)", currency="USD",
         price="46000", minimum=None, increment=None, maximum=None, rental="1350", frequency="QUARTERLY", months=48,
         risk="LOW", summary="Own a whole container outright on a 4-year quarterly-paid lease."),
    dict(serial="SLSU100404", type="DRY_40FT", condition="CARGO_WORTHY", cbm=67.7, gross=30480, tare=3750, year=2021,
         location="Nhava Sheva (JNPT)", country="IN", colour=(28, 58, 108), investment="RETAIL",
         title="40ft dry container, India export lanes", lessee="Western Ghats Logistics (fictional)", currency="USD",
         price="30000", minimum="1000", increment="1000", maximum="10000", rental="330", frequency="MONTHLY", months=36,
         risk="HIGH", summary="Higher yield on an older container serving fast-growing export routes."),
    dict(serial="SLSU100505", type="DRY_20FT", condition="NEW", cbm=33.2, gross=30480, tare=2200, year=2025,
         location="Port of Hamburg, Altenwerder", country="DE", colour=(46, 90, 60), investment="RETAIL",
         title="20ft dry container, European short-sea", lessee="Elbe Shortsea (fictional)", currency="EUR",
         price="18000", minimum="500", increment="100", maximum=None, rental="175", frequency="MONTHLY", months=24,
         risk="LOW", summary="An accessible entry point: a new 20ft container from 500 EUR."),
    dict(serial="SLSU100606", type="HIGH_CUBE_45FT", condition="NEW", cbm=86.0, gross=32500, tare=4800, year=2025,
         location="Port of Los Angeles", country="US", colour=(30, 30, 33), investment="HNI",
         title="Standalone 45ft High Cube, transpacific", lessee="Pacific Rim Carriers (fictional)", currency="USD",
         price="62000", minimum=None, increment=None, maximum=None, rental="640", frequency="MONTHLY", months=60,
         risk="MEDIUM", summary="A large-volume 45ft container on a 5-year transpacific lease."),
]


def main():
    status, login = call("POST", "/api/v1/auth/login",
                         body={"email": env("BOOTSTRAP_ADMIN_EMAIL"), "password": env("BOOTSTRAP_ADMIN_PASSWORD")})
    if status != 200:
        sys.exit(f"Admin login failed ({status}): {login}")
    token = login["accessToken"]
    created = 0

    for spec in OFFERINGS:
        number = container_number(spec["serial"])
        status, container = call("POST", "/api/v1/admin/containers", token, {
            "containerNumber": number, "containerType": spec["type"], "condition": spec["condition"],
            "capacityCbm": spec["cbm"], "maxGrossKg": spec["gross"], "tareKg": spec["tare"],
            "manufactureYear": spec["year"], "manufacturer": "CIMC", "currentLocation": spec["location"],
            "locationCountry": spec["country"], "notes": "Seeded demo data"})
        if status == 409:
            print(f"skip  {number} (already seeded)")
            continue
        if status != 201:
            sys.exit(f"Container {number} failed ({status}): {container}")
        container_id = container["container"]["id"]

        uploads = [("CONTAINER_PHOTO", "Yard view", "photo-1.png", "image/png", container_photo(spec["colour"], 0)),
                   ("CONTAINER_PHOTO", "Side view", "photo-2.png", "image/png", container_photo(spec["colour"], 1)),
                   ("CONTAINER_SURVEY_REPORT", "Pre-lease survey", "survey.pdf", "application/pdf", SURVEY_PDF)]
        for purpose, title, filename, ctype, data in uploads:
            body, ctype_header = multipart({}, {"file": (filename, ctype, data)})
            query = parse.urlencode({"purpose": purpose, "title": title, "visibleToInvestors": "true"})
            status, result = call("POST", f"/api/v1/admin/containers/{container_id}/documents?{query}", token,
                                  raw=body, content_type=ctype_header)
            if status != 201:
                sys.exit(f"Upload {title} for {number} failed ({status}): {result}")

        status, product = call("POST", "/api/v1/admin/investment-products", token, {
            "containerId": container_id, "investmentType": spec["investment"], "title": spec["title"],
            "summary": spec["summary"],
            "description": f"{spec['summary']}\n\nThe container is located at {spec['location']} and leased to "
                           f"{spec['lessee']}. Rental is paid {spec['frequency'].lower()} and distributed to "
                           f"investors in proportion to their ownership.",
            "currency": spec["currency"], "totalAmount": spec["price"], "minimumInvestment": spec["minimum"],
            "investmentIncrement": spec["increment"], "maximumPerInvestor": spec["maximum"],
            "expectedRentalAmount": spec["rental"], "rentalFrequency": spec["frequency"],
            "durationMonths": spec["months"], "lesseeName": spec["lessee"], "riskLevel": spec["risk"],
            "riskDisclosure": RISK, "termsAndConditions": TERMS, "termsVersion": "2026.1", "offerClosesAt": CLOSES})
        if status != 201:
            sys.exit(f"Offering for {number} failed ({status}): {product}")
        status, published = call("POST", f"/api/v1/admin/investment-products/{product['id']}/publish", token)
        if status != 200:
            sys.exit(f"Publishing {product['code']} failed ({status}): {published}")
        created += 1
        print(f"ok    {product['code']}  {number}  {spec['investment']:<6} {spec['title']}")

    print(f"\nSeeded {created} offering(s). Open http://localhost:3000/marketplace")


if __name__ == "__main__":
    main()
