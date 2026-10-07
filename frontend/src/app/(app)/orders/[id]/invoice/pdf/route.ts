import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** The investor's own invoice as a PDF. */
export async function GET(_request: Request, { params }: RouteContext<"/orders/[id]/invoice/pdf">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/reports/invoices/${id}?format=pdf`);
}
