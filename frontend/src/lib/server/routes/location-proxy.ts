import { NextResponse } from "next/server";
import { BackendError, backendFetch } from "@/lib/server/backend-client";

/** Countries, states and cities change only with a backend migration: browsers may keep them for a day. */
const CACHE = "public, max-age=86400";

/** Passes one level of the backend's public location data through to the address form in the browser. */
export async function locationResponse(path: string): Promise<NextResponse> {
  try {
    return NextResponse.json(await backendFetch<unknown>(path), { headers: { "Cache-Control": CACHE } });
  } catch (error) {
    const status = error instanceof BackendError && error.status === 404 ? 404 : 502;
    return NextResponse.json([], { status, headers: { "Cache-Control": "no-store" } });
  }
}
