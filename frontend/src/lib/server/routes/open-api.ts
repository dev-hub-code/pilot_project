import { headers } from "next/headers";
import { NextResponse } from "next/server";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import { forwardedClientHeaders } from "@/lib/server/request-context";

/**
 * The open API (/api/open/v1): plans, read-only, for anyone. Each route passes a GET through to the
 * backend's /api/v1/open endpoints, which limit each visitor to 120 requests a minute.
 */
export const OPEN_API = "/api/open/v1";

/** Any site may call it from a browser; responses may be cached for a minute. */
export const OPEN_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Accept, Content-Type",
  "Cache-Control": "public, max-age=60",
};

/** The backend's answer as JSON, reshaped by {@code shape}; its error (status and message) if it refuses. */
export async function openJson<T>(path: string, shape: (body: T) => unknown = (body) => body): Promise<NextResponse> {
  try {
    const body = await backendFetch<T>(path, { headers: forwardedClientHeaders(await headers()) });
    return NextResponse.json(shape(body), { headers: OPEN_HEADERS });
  } catch (error) {
    return openError(error);
  }
}

export function openPreflight(): Response {
  return new Response(null, { status: 204, headers: OPEN_HEADERS });
}

function openError(error: unknown): NextResponse {
  const known = error instanceof BackendError && [400, 404, 429].includes(error.status);
  const status = known ? (error as BackendError).status : 502;
  const message = known ? ((error as BackendError).apiError?.message ?? "Request refused") : "Plans are unavailable just now";
  return NextResponse.json({ status, message }, {
    status,
    headers: { ...OPEN_HEADERS, "Cache-Control": "no-store" },
  });
}
