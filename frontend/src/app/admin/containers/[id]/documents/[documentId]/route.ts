import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** Any document of a container, for staff with INVESTMENT_VIEW. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/containers/[id]/documents/[documentId]">) {
  const { id, documentId } = await params;
  if (!isUuid(id) || !isUuid(documentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/admin/containers/${id}/documents/${documentId}`);
}
