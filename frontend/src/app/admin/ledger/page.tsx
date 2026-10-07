import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { AdjustmentForm } from "@/features/admin/rental-forms";
import { ACCOUNT_LABEL } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AccountType, LedgerAccount, TrialBalanceLine } from "@/types/earning";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Ledger" };

const TYPES: readonly AccountType[] = ["RENTAL_CASH", "INVESTOR_EARNINGS", "PLATFORM_FEE_REVENUE", "PLATFORM_RETAINED", "PLATFORM_ADJUSTMENTS"];

export default async function LedgerPage({ searchParams }: PageProps<"/admin/ledger">) {
  const session = await requireStaff();
  const params = await searchParams;
  const type = TYPES.find((t) => t === params.type);
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "50" });
  if (type) query.set("type", type);
  const [trial, accounts] = await Promise.all([
    authFetch<TrialBalanceLine[]>("/api/v1/admin/ledger/trial-balance"),
    authFetch<PageResponse<LedgerAccount>>(`/api/v1/admin/ledger/accounts?${query}`),
  ]);
  const canAdjust = hasPermission(session.permissions, Permission.FINANCE_ADJUST);
  const currencies = trial.length > 0 ? trial.map((t) => t.currency) : ["USD"];

  return (
    <div className="space-y-6">
      <PageHeader title="Ledger" description="Double-entry record of every rental distribution and adjustment. Entries are never edited." />

      <section className="space-y-3" aria-label="Trial balance">
        <h2 className="text-lg font-semibold tracking-tight">Trial balance</h2>
        {trial.length === 0 ? (
          <p className="text-sm text-muted">Nothing has been posted yet.</p>
        ) : (
          <DataTable columns={["Currency", "Total debits", "Total credits", "Check"]}>
            {trial.map((t) => (
              <tr key={t.currency}>
                <Cell className="font-mono">{t.currency}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.debits)}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.credits)}</Cell>
                <Cell><StatusBadge tone={t.balanced ? "success" : "danger"}>{t.balanced ? "Balanced" : "Out of balance"}</StatusBadge></Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>

      <section className="space-y-3" aria-label="Accounts">
        <h2 className="text-lg font-semibold tracking-tight">Accounts</h2>
        <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
          {[undefined, ...TYPES].map((t) => (
            <Link key={t ?? "all"} href={t ? `/admin/ledger?type=${t}` : "/admin/ledger"} aria-current={t === type ? "page" : undefined}
              className={`rounded-full border px-3 py-1 ${t === type ? "border-foreground text-foreground" : "border-border text-muted"}`}>
              {t ? ACCOUNT_LABEL[t] : "All"}
            </Link>
          ))}
        </nav>
        {accounts.content.length === 0 ? (
          <EmptyState title="No accounts yet" description="Accounts open with their first posting." />
        ) : (
          <DataTable columns={["Account", "Investor", "Normal side", "Balance"]}>
            {accounts.content.map((a) => (
              <tr key={a.id} className="hover:bg-background">
                <Cell>
                  <Link href={`/admin/ledger/${a.id}`} className="text-gold-text hover:underline">{ACCOUNT_LABEL[a.accountType]}</Link>
                </Cell>
                <Cell>
                  {a.ownerUserId
                    ? <Link href={`/admin/users/${a.ownerUserId}`} className="font-mono text-xs hover:underline">{a.ownerUserId}</Link>
                    : <span className="text-muted">Platform</span>}
                </Cell>
                <Cell className="text-muted">{a.normalBalance === "DEBIT" ? "Debit" : "Credit"}</Cell>
                <Cell className="tabular-nums font-medium">{formatMoney(a.balance)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
        <Pagination page={accounts} basePath="/admin/ledger" params={{ type }} />
      </section>

      {canAdjust && (
        <Card title="Adjust an investor's balance" description="Booked against platform adjustments; audited with your reason.">
          <AdjustmentForm idempotencyKey={crypto.randomUUID()} currencies={currencies} />
        </Card>
      )}
    </div>
  );
}
