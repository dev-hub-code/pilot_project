import { openJson, openPreflight } from "@/lib/server/routes/open-api";

/** Monthly rent by order total, lowest band first. */
export async function GET() {
  return openJson("/api/v1/open/rent-bands");
}

export const OPTIONS = openPreflight;
