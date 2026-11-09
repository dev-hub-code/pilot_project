import { proxyReport } from "@/lib/server/routes/report-proxy";

/** The agreement wording in force, filled in with example values, as a PDF. */
export async function GET() {
  return proxyReport("/api/v1/admin/agreement-template/sample");
}
