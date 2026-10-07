import { NextResponse } from "next/server";
import { downloadFormat, isIsoDate, proxyReport } from "@/lib/server/routes/report-proxy";

/** The investor's own statement as PDF or CSV. */
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const from = params.get("from");
  const to = params.get("to");
  const format = downloadFormat(params.get("format"));
  if (!isIsoDate(from) || !isIsoDate(to) || !format) return new NextResponse(null, { status: 400 });
  return proxyReport(`/api/v1/reports/statement?${new URLSearchParams({ from, to, format })}`);
}
