import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** Any order's invoice as a PDF, for staff with ORDER_VIEW. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/orders/[id]/invoice/pdf">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/admin/reports/invoices/${id}?format=pdf`);
}
