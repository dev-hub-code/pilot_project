import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmailText } from "@/components/ui/email-text";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { CreateDemoAccountForm } from "@/features/admin/demo-forms";
import { InvestorPicker } from "@/features/admin/investor-picker";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DemoAccountSummary } from "@/types/demo";
import type { AdminUserDetail, AdminUserSummary } from "@/types/user";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = { title: "Demo accounts" };

const HERE = "/admin/demo-accounts";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** YYYY-MM-DD of a local date. */
function isoDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/**
 * Super administrators mark investors as demo accounts (?find= to search, ?pick= to choose one) and open them to give
 * them dummy orders and a downline.
 */
export default async function DemoAccountsPage({ searchParams }: PageProps<"/admin/demo-accounts">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.DEMO_ACCOUNT_MANAGE)) notFound();
  const query = await searchParams;
  const find = typeof query.find === "string" ? query.find.trim().slice(0, 100) : "";
  const pickId = typeof query.pick === "string" && UUID.test(query.pick) ? query.pick : null;

  const [accounts, found] = await Promise.all([
    authFetch<DemoAccountSummary[]>("/api/v1/admin/demo-accounts"),
    find ? authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?kind=INVESTOR&size=10&q=${encodeURIComponent(find)}`)
      .then((p) => p.content) : null,
  ]);
  const demoIds = new Set(accounts.map((a) => a.userId));
  const picked = pickId
    ? await authFetch<AdminUserDetail>(`/api/v1/admin/users/${pickId}`).then((d) => d.summary, () => null) : null;
  // A year up to the end of this month, by default.
  const now = new Date();
  const defaultEnd = isoDay(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  const defaultStart = isoDay(new Date(now.getFullYear() - 1, now.getMonth() + 1, 1));

  return (
    <div className="space-y-6">
      <PageHeader title="Demo accounts"
        description="Give an investor dummy orders, a dummy downline and payouts over a time frame. Their own pages then show these figures: rent paid out on the 1st and referral commission on the 5th of every month, worked out from the percentages. Demo accounts are never paid out for real. Only super administrators can open this page." />
      <Card title="Demo accounts">
        {accounts.length === 0 ? <EmptyState title="No demo accounts yet" description="Find an investor below to make one." /> : (
          <DataTable columns={["Investor", "Time frame", "Dummy orders", "Downline", ""]}>
            {accounts.map((a) => (
              <tr key={a.userId}>
                <Cell label="Investor">
                  <span className="font-medium">{a.name}</span> <span className="font-mono text-xs text-muted">{a.userCode}</span>
                  <span className="block text-xs text-muted"><EmailText>{a.email}</EmailText></span>
                </Cell>
                <Cell label="Time frame">{formatDate(a.startsOn)} – {formatDate(a.endsOn)}</Cell>
                <Cell label="Dummy orders" className="tabular-nums">{a.orders}</Cell>
                <Cell label="Downline" className="tabular-nums">{a.members}</Cell>
                <Cell><Link href={`${HERE}/${a.userId}`} className="text-gold-text hover:underline">Open</Link></Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>
      <div className="grid gap-6 lg:grid-cols-2">
        <InvestorPicker title="Make an investor a demo account" description="Find the investor by email, name or ID."
          find={find} found={found?.filter((u) => !demoIds.has(u.id)) ?? null}
          hrefFor={(id) => `${HERE}?find=${encodeURIComponent(find)}&pick=${id}`} />
        {picked && !demoIds.has(picked.id) && (
          <Card title={`${picked.firstName} ${picked.lastName}`} description="Choose the time frame the dummy figures cover.">
            <CreateDemoAccountForm userId={picked.id} name={`${picked.firstName} ${picked.lastName}`}
              defaultStart={defaultStart} defaultEnd={defaultEnd} />
          </Card>
        )}
      </div>
    </div>
  );
}
