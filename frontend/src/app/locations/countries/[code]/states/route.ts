import { NextResponse } from "next/server";
import { locationResponse } from "@/lib/server/routes/location-proxy";

/** A country's states, once the country is chosen. */
export async function GET(_request: Request, { params }: RouteContext<"/locations/countries/[code]/states">) {
  const { code } = await params;
  if (!/^[A-Za-z]{2}$/.test(code)) return NextResponse.json([], { status: 404 });
  return locationResponse(`/api/v1/public/locations/countries/${code.toUpperCase()}/states`);
}
