import { NextResponse } from "next/server";
import { isUuid } from "@/lib/server/routes/document-proxy";
import { downloadFormat, isIsoDate, proxyReport } from "@/lib/server/routes/report-proxy";

/** Staff report downloads; the backend checks REPORT_GENERATE and audits each one. */
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const format = downloadFormat(params.get("format"));
  const from = params.get("from");
  const to = params.get("to");
  const userId = params.get("userId");
  if (!format) return new NextResponse(null, { status: 400 });
  switch (params.get("report")) {
    case "offerings":
      return proxyReport(`/api/v1/admin/reports/offerings?format=${format}`);
    case "financial-summary":
      if (!isIsoDate(from) || !isIsoDate(to)) return new NextResponse(null, { status: 400 });
      return proxyReport(`/api/v1/admin/reports/financial-summary?${new URLSearchParams({ from, to, format })}`);
    case "statement":
      if (!isIsoDate(from) || !isIsoDate(to) || !userId || !isUuid(userId)) return new NextResponse(null, { status: 400 });
      return proxyReport(`/api/v1/admin/reports/statement?${new URLSearchParams({ userId, from, to, format })}`);
    default:
      return new NextResponse(null, { status: 404 });
  }
}
