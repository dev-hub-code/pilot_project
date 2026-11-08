import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { AdjustmentForm } from "@/features/admin/ledger-forms";
import { ACCOUNT_LABEL } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AccountType, LedgerAccount, TrialBalanceLine } from "@/types/earning";
import { formatMoney } from "@/utils/money";
import {
  ArrowDownLeft, ArrowUpRight, Building2, CheckCircle2, Landmark, type LucideIcon, Scale, SlidersHorizontal, TriangleAlert, UserRound, Wallet,
} from "lucide-react";

export const metadata: Metadata = { title: "Ledger" };

const TYPES: readonly AccountType[] = [
  "RENTAL_CASH", "INVESTOR_EARNINGS", "INVESTOR_CASH_EARNINGS", "INVESTOR_REFERRAL_EARNINGS", "PLATFORM_RENT_EXPENSE", "PLATFORM_CAPITAL_RETURNS", "PLATFORM_ADJUSTMENTS",
  "PLATFORM_REFERRAL_EXPENSE", "WITHDRAWALS_IN_TRANSIT", "WITHDRAWAL_FEE_INCOME", "TAX_WITHHELD",
  "DEPOSITS_RECEIVED", "INVESTOR_PRINCIPAL", "DEPOSITS_TO_REFUND",
];

/** The filters, grouped by whose money the account holds. */
const GROUPS: { label: string; icon: LucideIcon; types: readonly AccountType[] }[] = [
  { label: "Investors", icon: UserRound, types: ["INVESTOR_EARNINGS", "INVESTOR_CASH_EARNINGS", "INVESTOR_REFERRAL_EARNINGS", "INVESTOR_PRINCIPAL"] },
  { label: "Platform", icon: Building2, types: ["PLATFORM_RENT_EXPENSE", "PLATFORM_CAPITAL_RETURNS", "PLATFORM_ADJUSTMENTS", "PLATFORM_REFERRAL_EXPENSE", "WITHDRAWAL_FEE_INCOME", "TAX_WITHHELD"] },
  { label: "Cash and deposits", icon: Landmark, types: ["RENTAL_CASH", "DEPOSITS_RECEIVED", "DEPOSITS_TO_REFUND", "WITHDRAWALS_IN_TRANSIT"] },
];

export default async function LedgerPage({ searchParams }: PageProps<"/admin/ledger">) {
  const denied = await deniedUnlessInMenu("/admin/ledger");
  if (denied) return denied;
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

  return (
    <div className="space-y-6">
      <PageHeader title="Ledger" description="Double-entry record of every investor payout, commission, adjustment and withdrawal. Entries are never edited." />

      <section aria-labelledby="trial-title" className="relative isolate overflow-hidden rounded-3xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 sm:p-8">
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/25 blur-3xl" />
        <div aria-hidden="true" className="absolute -bottom-32 -left-16 -z-10 size-64 rounded-full bg-on-ink/5 blur-3xl" />
        <h2 id="trial-title" className="flex items-center gap-3 text-lg font-semibold tracking-tight">
          <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-on-ink/10 text-gold-on-ink ring-1 ring-on-ink/15">
            <Scale className="size-[18px]" />
          </span>
          Trial balance
        </h2>
        {trial.length === 0 ? (
          <p className="mt-4 text-sm text-on-ink/70">Nothing has been posted yet.</p>
        ) : (
          <div className="mt-5 space-y-4">
            {trial.map((t) => (
              <div key={t.currency} className="grid gap-3 sm:grid-cols-[auto_1fr_1fr_auto] sm:items-stretch">
                <span className="flex items-center rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 font-mono text-sm font-semibold backdrop-blur-md">
                  {t.currency}
                </span>
                <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-5 py-3 backdrop-blur-md">
                  <p className="flex items-center gap-1.5 text-xs text-on-ink/60"><ArrowUpRight aria-hidden="true" className="size-3.5" /> Total debits</p>
                  <p className="mt-1 font-display text-2xl font-semibold tracking-tight tabular-nums">{formatMoney(t.debits)}</p>
                </div>
                <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-5 py-3 backdrop-blur-md">
                  <p className="flex items-center gap-1.5 text-xs text-on-ink/60"><ArrowDownLeft aria-hidden="true" className="size-3.5" /> Total credits</p>
                  <p className="mt-1 font-display text-2xl font-semibold tracking-tight tabular-nums">{formatMoney(t.credits)}</p>
                </div>
                <span className={`flex items-center gap-2 rounded-2xl px-5 py-3 text-sm font-semibold ${t.balanced
                  ? "bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-400/30"
                  : "bg-rose-500/15 text-rose-300 ring-1 ring-rose-400/30"}`}>
                  {t.balanced ? <CheckCircle2 aria-hidden="true" className="size-5" /> : <TriangleAlert aria-hidden="true" className="size-5" />}
                  {t.balanced ? "Balanced" : "Out of balance"}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-4" aria-labelledby="accounts-title">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="accounts-title" className="flex items-center gap-3 text-lg font-semibold tracking-tight">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
              <Wallet className="size-[18px]" />
            </span>
            Accounts
          </h2>
          <p className="text-sm text-muted">
            {accounts.totalElements.toLocaleString("en-IN")} {accounts.totalElements === 1 ? "account" : "accounts"}{type ? ` · ${ACCOUNT_LABEL[type]}` : ""}
          </p>
        </div>

        <nav aria-label="Filter by account" className="space-y-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Link href="/admin/ledger" aria-current={!type ? "page" : undefined} className={chip(!type)}>
              <SlidersHorizontal aria-hidden="true" className="size-3.5" /> All accounts
            </Link>
          </div>
          {GROUPS.map((g) => (
            <div key={g.label} className="flex flex-col gap-2 border-t border-border pt-3 sm:flex-row sm:items-start">
              <p className="flex w-44 shrink-0 items-center gap-2 pt-1.5 text-xs font-semibold tracking-wide text-muted uppercase">
                <g.icon aria-hidden="true" className="size-4 text-gold-text" /> {g.label}
              </p>
              <div className="flex flex-wrap gap-2 text-sm">
                {g.types.map((t) => (
                  <Link key={t} href={`/admin/ledger?type=${t}`} aria-current={t === type ? "page" : undefined} className={chip(t === type)}>
                    {ACCOUNT_LABEL[t]}
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </nav>

        {accounts.content.length === 0 ? (
          <EmptyState title="No accounts yet" description="Accounts open with their first posting." />
        ) : (
          <DataTable columns={["Account", "Held for", "Normal side", "Balance"]}>
            {accounts.content.map((a) => {
              const Icon = a.ownerUserId ? UserRound : Building2;
              const zero = Number(a.balance.amount) === 0;
              return (
                <tr key={a.id} className="group hover:bg-background">
                  <Cell>
                    <span className="flex items-center gap-3">
                      <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text transition-transform duration-300 group-hover:scale-105">
                        <Icon className="size-4" />
                      </span>
                      <Link href={`/admin/ledger/${a.id}`} className="card-link font-medium text-foreground group-hover:text-gold-text">{ACCOUNT_LABEL[a.accountType]}</Link>
                    </span>
                  </Cell>
                  <Cell>
                    {a.ownerUserId
                      ? <Link href={`/admin/users/${a.ownerUserId}`} className="relative z-10 font-mono text-xs text-gold-text hover:underline">{a.ownerUserId}</Link>
                      : <StatusBadge tone="neutral">Platform</StatusBadge>}
                  </Cell>
                  <Cell>
                    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${a.normalBalance === "DEBIT"
                      ? "bg-sky-500/10 text-sky-700 ring-sky-500/20 dark:text-sky-300" : "bg-violet-500/10 text-violet-700 ring-violet-500/20 dark:text-violet-300"}`}>
                      {a.normalBalance === "DEBIT" ? <ArrowUpRight aria-hidden="true" className="size-3" /> : <ArrowDownLeft aria-hidden="true" className="size-3" />}
                      {a.normalBalance === "DEBIT" ? "Debit" : "Credit"}
                    </span>
                  </Cell>
                  <Cell className={`text-base font-semibold tabular-nums ${zero ? "text-muted" : ""}`}>{formatMoney(a.balance)}</Cell>
                </tr>
              );
            })}
          </DataTable>
        )}
        <Pagination page={accounts} basePath="/admin/ledger" params={{ type }} />
      </section>

      {canAdjust && (
        <Card title="Adjust an investor's balance" description="Booked against platform adjustments; audited with your reason.">
          <AdjustmentForm idempotencyKey={crypto.randomUUID()} />
        </Card>
      )}
    </div>
  );
}

function chip(active: boolean): string {
  return `inline-flex min-h-8 items-center gap-1.5 rounded-full border px-3 font-medium transition-colors duration-200 ${active
    ? "border-ink bg-ink text-on-ink dark:border-gold dark:bg-gold dark:text-on-gold"
    : "border-border bg-background/60 text-muted hover:border-gold/40 hover:text-foreground"}`;
}
