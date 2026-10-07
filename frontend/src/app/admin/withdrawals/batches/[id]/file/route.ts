import { NextResponse } from "next/server";
import { BackendError } from "@/lib/server/backend-client";
import { authRequest } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";

/**
 * The batch's bank payment file. The backend authorizes (WITHDRAWAL_PROCESS) and audits the
 * download; this only passes it through as a never-cached attachment.
 */
export async function GET(_request: Request, { params }: RouteContext<"/admin/withdrawals/batches/[id]/file">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  try {
    const upstream = await authRequest(`/api/v1/admin/withdrawal-batches/${id}/file`, { headers: { Accept: "text/csv, application/json" } });
    if (!(upstream.headers.get("Content-Type") ?? "").startsWith("text/csv")) return new NextResponse(null, { status: 415 });
    return new NextResponse(upstream.body, {
      headers: {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": upstream.headers.get("Content-Disposition") ?? "attachment",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    if (error instanceof BackendError) {
      // Business-rule refusals (e.g. an account no longer verified) are shown as plain text.
      const message = error.status < 500 && error.apiError?.message ? error.apiError.message : "The file could not be produced.";
      return new NextResponse(message, { status: error.status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
    }
    throw error;
  }
}
