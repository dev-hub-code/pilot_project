import { NextResponse } from "next/server";
import { REPORT_KEY, exportParams } from "@/features/analytics/query";
import { proxyReport } from "@/lib/server/routes/report-proxy";

/** Catalogue report downloads (CSV, Excel, PDF); the backend authorizes, audits and records each one. */
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const key = params.get("report");
  const format = params.get("format");
  if (!key || !REPORT_KEY.test(key) || !format || !["csv", "xlsx", "pdf"].includes(format)) {
    return new NextResponse(null, { status: 400 });
  }
  const passed = exportParams(params);
  if (!passed) return new NextResponse(null, { status: 400 });
  passed.set("format", format);
  return proxyReport(`/api/v1/admin/analytics/reports/${key}/export?${passed}`);
}
