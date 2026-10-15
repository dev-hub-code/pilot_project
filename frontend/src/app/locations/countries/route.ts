import { locationResponse } from "@/lib/server/routes/location-proxy";

/** Every country, alphabetical: the first address dropdown. */
export async function GET() {
  return locationResponse("/api/v1/public/locations/countries");
}
