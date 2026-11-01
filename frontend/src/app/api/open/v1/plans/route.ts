import type { NextRequest } from "next/server";
import { openJson, openPreflight } from "@/lib/server/routes/open-api";

const PARAMS = ["status", "page", "size"];

/** Listed plans, a page at a time. */
export async function GET(request: NextRequest) {
  const query = new URLSearchParams();
  for (const name of PARAMS) {
    const value = request.nextUrl.searchParams.get(name);
    if (value) query.set(name, value);
  }
  return openJson(`/api/v1/open/plans?${query}`);
}

export const OPTIONS = openPreflight;
