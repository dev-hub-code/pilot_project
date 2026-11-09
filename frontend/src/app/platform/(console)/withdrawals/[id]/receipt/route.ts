import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** A paid withdrawal's receipt, as the investor gets it (PDF). */
export async function GET(_request: Request, { params }: RouteContext<"/platform/withdrawals/[id]/receipt">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/admin/withdrawals/${id}/receipt`);
}
