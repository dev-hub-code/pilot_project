#!/usr/bin/env python3
"""
Generates the country, state and city data the backend loads into its database (migration V51): three gzipped,
tab-separated files in backend/src/main/resources/db/locations/. The data comes from the open countries-states-cities
database (https://github.com/dr5hn/countries-states-cities-database, ODbL-1.0).

India's state names must match the backend's GstStates (place of supply on invoices); the script stops if one does not.

    python3 scripts/generate-locations.py
    python3 scripts/generate-locations.py path/to/countries+states+cities.json   # an already downloaded copy

Changing the files after V51 has run changes nothing in existing databases: add a new migration to load updates.
Uses only the Python standard library.
"""
import gzip
import json
import pathlib
import re
import sys
import urllib.request

SOURCE = ("https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/"
          "master/json/countries%2Bstates%2Bcities.json")
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "backend/src/main/resources/db/locations"
GST_STATES = ROOT / "backend/src/main/java/com/sealease/backend/common/tax/GstStates.java"


def load(argv):
    if len(argv) > 1:
        with open(argv[1], encoding="utf-8") as f:
            return json.load(f)
    print(f"Downloading {SOURCE} ...", file=sys.stderr)
    with urllib.request.urlopen(SOURCE, timeout=300) as response:
        return json.load(response)


def clean(text):
    """One line, no tabs: the files are tab-separated."""
    return re.sub(r"\s+", " ", (text or "")).strip()


def write(name, rows):
    with gzip.open(OUTPUT / name, "wt", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write("\t".join(str(v) for v in row) + "\n")


def main(argv):
    data = load(argv)
    countries, states, cities = [], [], []
    for country in sorted(data, key=lambda c: c["iso2"]):
        code = country["iso2"].upper()
        countries.append((code, clean(country["name"]), clean(country.get("iso3")), clean(country.get("phonecode"))))
        for state in country.get("states", []):
            states.append((state["id"], code, clean(state["name"]), clean(state.get("iso2") or state.get("state_code"))))
            seen = set()
            for city in state.get("cities", []):
                name = clean(city["name"])
                # One entry per name in a state: the dropdown shows names only.
                if name and name.casefold() not in seen:
                    seen.add(name.casefold())
                    cities.append((city["id"], state["id"], name))

    gst = set(re.findall(r'add\("\d{2}", "([^"]+)"', GST_STATES.read_text(encoding="utf-8")))
    india = {s[2] for s in states if s[1] == "IN"}
    if india - gst:
        sys.exit(f"Indian states not in GstStates (fix the names or GstStates first): {sorted(india - gst)}")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    write("countries.tsv.gz", countries)
    write("states.tsv.gz", states)
    write("cities.tsv.gz", cities)
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {len(countries)} countries, {len(states)} states, {len(cities)} cities",
          file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv)
