import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelWithdrawalAction } from "@/features/withdrawals/actions";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { WithdrawalRequestForm } from "@/features/withdrawals/request-form";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { EarningsSummary } from "@/types/earning";
import type { BankAccount, Profile } from "@/types/user";
import type { Withdrawal, WithdrawalPolicy } from "@/types/withdrawal";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

export default async function WithdrawalsPage({ searchParams }: PageProps<"/withdrawals">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [summary, accounts, profile, policy, history] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<WithdrawalPolicy>("/api/v1/withdrawals/policy"),
    authFetch<PageResponse<Withdrawal>>(`/api/v1/withdrawals?page=${page}&size=20`),
  ]);
  const verified = accounts.filter((a) => a.status === "VERIFIED");
  // Withdrawals are paid in the bank account's currency, from the balance in that currency.
  const fundedCurrencies = new Set(summary.balances.filter((m) => Number(m.amount) > 0).map((m) => m.currency));
  const payable = verified.filter((a) => fundedCurrencies.has(a.currency));
  const balances = summary.balances.length === 0 ? "—" : summary.balances.map((m) => formatMoney(m)).join(" · ");

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals" description="Move your rental and referral income to your bank account." />
      <section className="bg-surface p-6" aria-label="Available balance">
        <p className="text-xs uppercase tracking-[0.1em] text-muted">Available balance</p>
        <p className="font-display text-4xl font-semibold tabular-nums">{balances}</p>
      </section>

      <Card title="Request a withdrawal"
        description={`Minimum ${policy.minimumAmount} per withdrawal, one at a time per currency. Paid in the bank account's currency.`}>
        {profile.kycStatus !== "APPROVED" ? (
          <Notice tone="warning">
            Verify your identity before withdrawing. <Link href="/profile/verification" className="underline">Start verification</Link>
          </Notice>
        ) : verified.length === 0 ? (
          <Notice tone="warning">
            Add a bank account and wait for it to be verified. <Link href="/profile/bank-accounts" className="underline">Bank accounts</Link>
          </Notice>
        ) : fundedCurrencies.size === 0 ? (
          <Notice tone="info">There is nothing to withdraw yet.</Notice>
        ) : payable.length === 0 ? (
          <Notice tone="warning">
            Your balance is in {[...fundedCurrencies].join(", ")}, but none of your verified bank accounts is.
            Add an account in that currency. <Link href="/profile/bank-accounts" className="underline">Bank accounts</Link>
          </Notice>
        ) : (
          <WithdrawalRequestForm idempotencyKey={crypto.randomUUID()}
            accounts={payable.map((a) => ({
              value: a.id,
              label: `${a.bankName} ${a.accountNumberMasked} (${a.currency})${a.primary ? " · primary" : ""}`,
            }))} />
        )}
      </Card>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">History</h2>
        {history.content.length === 0 ? (
          <EmptyState title="No withdrawals yet" />
        ) : (
          <>
            <DataTable columns={["Requested", "Reference", "To", "Amount", "Status", ""]}>
              {history.content.map((w) => (
                <tr key={w.id}>
                  <Cell className="text-muted">{formatDate(w.createdAt)}</Cell>
                  <Cell className="font-mono">{w.reference}</Cell>
                  <Cell>{w.bankName} <span className="text-muted">{w.bankAccountMasked}</span></Cell>
                  <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
                  <Cell>
                    <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
                    {(w.rejectionReason ?? w.failureReason) && (
                      <span className="block max-w-xs truncate text-xs text-muted">{w.rejectionReason ?? w.failureReason}</span>
                    )}
                    {w.status === "PAID" && w.closedAt && <span className="block text-xs text-muted">on {formatDate(w.closedAt)}</span>}
                  </Cell>
                  <Cell>
                    {w.status === "PENDING_APPROVAL" && (
                      <ActionButton action={cancelWithdrawalAction.bind(null, w.id)} label="Cancel" variant="quiet"
                        confirm={`Cancel ${w.reference}? The amount returns to your balance.`} />
                    )}
                  </Cell>
                </tr>
              ))}
            </DataTable>
            <Pagination page={history} basePath="/withdrawals" params={{}} />
          </>
        )}
      </section>
    </div>
  );
}
