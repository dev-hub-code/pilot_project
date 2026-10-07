import "server-only";
import { NextResponse } from "next/server";
import { BackendError } from "../backend-client";
import { authRequest } from "../auth/session";

const DOWNLOAD_TYPES = ["application/pdf", "text/csv"];
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isIsoDate(value: string | null): value is string {
  return value !== null && DATE.test(value);
}

export function downloadFormat(value: string | null): "pdf" | "csv" | null {
  return value === "pdf" || value === "csv" ? value : null;
}

/**
 * Streams a generated report (PDF or CSV) from the backend, which authorizes, audits and names it.
 * Never cached. Refusals (e.g. a period that is too long) come back as plain text.
 */
export async function proxyReport(backendPath: string): Promise<NextResponse> {
  try {
    // JSON is acceptable too, so error bodies can be read.
    const upstream = await authRequest(backendPath, { headers: { Accept: "application/pdf, text/csv, application/json" } });
    const contentType = upstream.headers.get("Content-Type") ?? "";
    if (!DOWNLOAD_TYPES.some((t) => contentType.startsWith(t))) return new NextResponse(null, { status: 415 });
    return new NextResponse(upstream.body, {
      headers: {
        "Content-Type": contentType,
        "Content-Disposition": upstream.headers.get("Content-Disposition") ?? "attachment",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    if (error instanceof BackendError) {
      const message = error.status < 500 && error.apiError?.message ? error.apiError.message : "The report could not be produced.";
      return new NextResponse(message, { status: error.status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
    }
    throw error;
  }
}
