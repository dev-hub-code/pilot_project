import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** KYC evidence for reviewers; the backend checks KYC_REVIEW and audits every view. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/kyc/[id]/documents/[documentId]">) {
  const { id, documentId } = await params;
  if (!isUuid(id) || !isUuid(documentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/admin/kyc/${id}/documents/${documentId}`);
}
