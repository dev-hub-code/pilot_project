import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** Any attachment on a ticket, for SUPPORT_TICKET_VIEW holders; the backend audits every view. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/support/[id]/attachments/[attachmentId]">) {
  const { id, attachmentId } = await params;
  if (!isUuid(id) || !isUuid(attachmentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/admin/support/tickets/${id}/attachments/${attachmentId}`);
}
