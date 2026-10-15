import { NextResponse } from "next/server";
import { locationResponse } from "@/lib/server/routes/location-proxy";

/** A state's cities, once the state is chosen. */
export async function GET(_request: Request, { params }: RouteContext<"/locations/states/[id]/cities">) {
  const { id } = await params;
  if (!/^\d{1,9}$/.test(id)) return NextResponse.json([], { status: 404 });
  return locationResponse(`/api/v1/public/locations/states/${id}/cities`);
}
