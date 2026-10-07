import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** An attachment on the investor's own ticket (never one on an internal note); the backend decides. */
export async function GET(_request: Request, { params }: RouteContext<"/support/[id]/attachments/[attachmentId]">) {
  const { id, attachmentId } = await params;
  if (!isUuid(id) || !isUuid(attachmentId)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/support/tickets/${id}/attachments/${attachmentId}`);
}
