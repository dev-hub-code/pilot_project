import { NextResponse } from "next/server";
import { OPEN_API, OPEN_HEADERS, openPreflight } from "@/lib/server/routes/open-api";

/** What the open API offers: the marketplace, read-only, without signing in. */
export function GET() {
  return NextResponse.json({
    name: "SeaLease open API",
    description: "The SeaLease container marketplace, read-only. No sign-in or key; nothing can be bought or changed. "
      + "At most 120 requests a minute from one address. Amounts are strings in the currency given.",
    endpoints: [
      { method: "GET", path: `${OPEN_API}/marketplace`, description: "Listed containers, a page at a time.",
        query: {
          status: "OPEN (default: on sale) or CLOSED (no longer sold)",
          containerType: "e.g. DRY_10FT, DRY_20FT, DRY_40FT, HIGH_CUBE_40FT, REEFER_20FT, REEFER_40FT",
          sort: "NEWEST (default) or LOWEST_PRICE",
          page: "from 0", size: "1 to 50, 20 by default",
        } },
      { method: "GET", path: `${OPEN_API}/marketplace/{code}`, description: "One listing by its code (e.g. LST-10001), with its description and photos." },
      { method: "GET", path: `${OPEN_API}/marketplace/{code}/photos/{photoId}`, description: "A listing's photo (the links in photoUrls)." },
      { method: "GET", path: `${OPEN_API}/rent-bands`, description: "Monthly rent by order total: an order earns its band's rate on every container's price." },
    ],
  }, { headers: OPEN_HEADERS });
}

export const OPTIONS = openPreflight;
