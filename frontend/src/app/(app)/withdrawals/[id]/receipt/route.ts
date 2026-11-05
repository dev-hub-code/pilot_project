import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** The investor's receipt for one of their paid withdrawals, as a PDF. */
export async function GET(_request: Request, { params }: RouteContext<"/withdrawals/[id]/receipt">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/withdrawals/${id}/receipt`);
}
