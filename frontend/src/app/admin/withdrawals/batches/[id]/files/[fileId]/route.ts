import { NextResponse } from "next/server";
import { BackendError } from "@/lib/server/backend-client";
import { authRequest } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";

const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

/**
 * A file kept with the batch (its bank file or an uploaded reconciliation file). The backend
 * authorizes (WITHDRAWAL_PROCESS) and audits the download; this passes it through, never cached.
 */
export async function GET(_request: Request, { params }: RouteContext<"/admin/withdrawals/batches/[id]/files/[fileId]">) {
  const { id, fileId } = await params;
  if (!isUuid(id) || !isUuid(fileId)) return new NextResponse(null, { status: 404 });
  try {
    const upstream = await authRequest(`/api/v1/admin/withdrawal-batches/${id}/files/${fileId}`,
      { headers: { Accept: `${XLSX}, text/csv, application/json` } });
    // The monthly files are Excel; batches from before them kept CSV files.
    const type = upstream.headers.get("Content-Type") ?? "";
    if (!type.startsWith(XLSX) && !type.startsWith("text/csv")) return new NextResponse(null, { status: 415 });
    return new NextResponse(upstream.body, {
      headers: {
        "Content-Type": type.startsWith(XLSX) ? XLSX : "text/csv; charset=utf-8",
        "Content-Disposition": upstream.headers.get("Content-Disposition") ?? "attachment",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    if (error instanceof BackendError) {
      const message = error.status < 500 && error.apiError?.message ? error.apiError.message : "The file could not be read.";
      return new NextResponse(message, { status: error.status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
    }
    throw error;
  }
}
