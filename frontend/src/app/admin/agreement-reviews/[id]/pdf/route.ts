import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** The agreement a review task is about, as a PDF. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/agreement-reviews/[id]/pdf">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyReport(`/api/v1/admin/agreement-reviews/${id}/pdf`);
}
