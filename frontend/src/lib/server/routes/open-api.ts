import { headers } from "next/headers";
import { NextResponse } from "next/server";
import { BackendError, backendFetch, backendRequest } from "@/lib/server/backend-client";
import { forwardedClientHeaders } from "@/lib/server/request-context";

/**
 * The open API (/api/open/v1): the marketplace, read-only, for anyone. Each route passes a GET through to the
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

/** A listing as the open API returns it: photos as links (relative to https://www.sealease.in) instead of ids. */
interface Listing {
  code: string;
  photoIds?: string[];
  [field: string]: unknown;
}

export function withPhotoUrls({ photoIds = [], ...listing }: Listing) {
  return {
    ...listing,
    photoUrls: photoIds.map((id) => `${OPEN_API}/marketplace/${encodeURIComponent(listing.code)}/photos/${id}`),
  };
}

/** The backend's answer as JSON, reshaped by {@code shape}; its error (status and message) if it refuses. */
export async function openJson<T>(path: string, shape: (body: T) => unknown = (body) => body): Promise<NextResponse> {
  try {
    const body = await backendFetch<T>(path, { headers: forwardedClientHeaders(await headers()) });
    return NextResponse.json(shape(body), { headers: OPEN_HEADERS });
  } catch (error) {
    return openError(error);
  }
}

/** A listing's photo, as the image itself. */
export async function openImage(path: string): Promise<Response> {
  try {
    const response = await backendRequest(path, {
      headers: { ...forwardedClientHeaders(await headers()), Accept: "image/*" },
    });
    return new Response(response.body, {
      headers: {
        ...OPEN_HEADERS,
        "Content-Type": response.headers.get("Content-Type") ?? "application/octet-stream",
        // A photo never changes under its id.
        "Cache-Control": "public, max-age=86400",
      },
    });
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
  const message = known ? ((error as BackendError).apiError?.message ?? "Request refused") : "The marketplace is unavailable just now";
  return NextResponse.json({ status, message }, {
    status,
    headers: { ...OPEN_HEADERS, "Cache-Control": "no-store" },
  });
}
