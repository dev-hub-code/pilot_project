import "server-only";
import { NextResponse } from "next/server";
import { BackendError } from "../backend-client";
import { authRequest } from "../auth/session";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ALLOWED_TYPES = new Set(["application/pdf", "image/jpeg", "image/png"]);

export function isUuid(value: string): boolean {
  return UUID.test(value);
}

/**
 * Streams a stored document from the backend (which authorizes access) to the browser: never
 * cached, never sniffed, and sandboxed so a crafted file cannot run script on this origin.
 */
export async function proxyDocument(backendPath: string): Promise<NextResponse> {
  try {
    const upstream = await authRequest(backendPath, { headers: { Accept: "application/pdf, image/jpeg, image/png" } });
    const contentType = upstream.headers.get("Content-Type") ?? "";
    if (!ALLOWED_TYPES.has(contentType)) return new NextResponse(null, { status: 415 });
    return new NextResponse(upstream.body, {
      headers: {
        "Content-Type": contentType,
        "Content-Disposition": upstream.headers.get("Content-Disposition") ?? "inline",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox",
      },
    });
  } catch (error) {
    if (error instanceof BackendError) return new NextResponse(null, { status: error.status === 403 ? 403 : 404 });
    throw error;
  }
}
