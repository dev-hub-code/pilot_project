import { NextResponse } from "next/server";
import { isUuid, proxyDocument } from "@/lib/server/routes/document-proxy";

/** A bank account's statement or cancelled cheque, for its verifier (BANK_ACCOUNT_VERIFY); each view is audited. */
export async function GET(_request: Request, { params }: RouteContext<"/admin/bank-accounts/[id]/proof">) {
  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });
  return proxyDocument(`/api/v1/admin/bank-accounts/${id}/proof`);
}
