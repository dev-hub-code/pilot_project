import { proxyReport } from "@/lib/server/routes/report-proxy";

const FILTERS = ["category", "action", "actor", "entityType", "entityId", "ip", "from", "to"] as const;

/** The audit log as CSV with the page's filters; the backend checks AUDIT_VIEW and audits each export. */
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const query = new URLSearchParams();
  for (const key of FILTERS) {
    const value = params.get(key);
    if (value) query.set(key, value.slice(0, 320));
  }
  return proxyReport(`/api/v1/admin/audit-logs/export?${query}`);
}
