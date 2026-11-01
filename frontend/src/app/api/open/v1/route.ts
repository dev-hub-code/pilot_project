import { NextResponse } from "next/server";
import { OPEN_API, OPEN_HEADERS, openPreflight } from "@/lib/server/routes/open-api";

/** What the open API offers: plans, read-only, without signing in. */
export function GET() {
  return NextResponse.json({
    name: "SeaLease open API",
    description: "SeaLease's online investment plans, read-only. No sign-in or key; nothing can be bought or changed. "
      + "At most 120 requests a minute from one address. Amounts are strings in the currency given.",
    endpoints: [
      { method: "GET", path: `${OPEN_API}/plans`, description: "Listed plans, shortest tenure first, a page at a time.",
        query: {
          status: "OPEN (default: on sale) or CLOSED (no longer sold)",
          page: "from 0", size: "1 to 50, 20 by default",
        } },
      { method: "GET", path: `${OPEN_API}/plans/{code}`, description: "One plan by its code (e.g. LST-10001): tenure, monthly return, unit price and minimum units." },
    ],
  }, { headers: OPEN_HEADERS });
}

export const OPTIONS = openPreflight;
