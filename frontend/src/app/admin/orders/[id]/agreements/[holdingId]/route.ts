import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** The lease agreement issued for one of an order's containers, as a PDF. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/orders/[id]/agreements/[holdingId]">) {
  const { holdingId } = await params;
  if (!isUuid(holdingId)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/admin/agreements/${holdingId}/pdf`);
}
