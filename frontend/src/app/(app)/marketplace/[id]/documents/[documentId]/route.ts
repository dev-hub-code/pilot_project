import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** Photos and documents of a listed offering; the backend only serves investor-visible files. */
export async function GET(_request: Request, { params }: RouteContext<"/marketplace/[id]/documents/[documentId]">) {
  const { id, documentId } = await params;
  if (!isUuid(id) || !isUuid(documentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/marketplace/${id}/documents/${documentId}`);
}
