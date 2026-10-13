import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** A plan's photo, for staff with INVESTMENT_VIEW (also drafts, which the marketplace does not show). */
export async function GET(_request: Request, { params }: RouteContext<"/admin/products/[id]/photos/[documentId]">) {
  const { id, documentId } = await params;
  if (!isUuid(id) || !isUuid(documentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/admin/investment-products/${id}/photos/${documentId}/content`);
}
