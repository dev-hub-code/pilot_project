import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { EARNINGS_BALANCE_LABEL, nextCreditNote } from "@/features/earnings/labels";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { ReferralModeForm } from "@/features/withdrawals/referral-mode-form";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { getRentCredit } from "@/lib/server/rent-credit";
import type { PageResponse } from "@/types/api";
import type { EarningsBalance, EarningsSummary } from "@/types/earning";
import type { Money } from "@/types/marketplace";
import type { BankAccount, Profile } from "@/types/user";
import type { ReferralPayoutMode, Withdrawal, WithdrawalPolicy } from "@/types/withdrawal";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

/** Adds up amounts in the platform currency (the only one investors earn in). */
function total(amounts: Money[]): Money {
  const sum = amounts.filter((m) => m.currency === CURRENCY).reduce((acc, m) => acc + Number(m.amount), 0);
  return { amount: sum.toFixed(2), currency: CURRENCY };
}

function wallet(summary: EarningsSummary, balances: EarningsBalance[]): Money {
  return total(summary.wallet.filter((w) => balances.includes(w.balance)).map((w) => w.amount));
}

export default async function WithdrawalsPage({ searchParams }: PageProps<"/withdrawals">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const credit = await getRentCredit();
  const [summary, accounts, profile, policy, referralMode, history] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<WithdrawalPolicy>("/api/v1/withdrawals/policy"),
    authFetch<ReferralPayoutMode>("/api/v1/withdrawals/referral-mode"),
    authFetch<PageResponse<Withdrawal>>(`/api/v1/withdrawals?page=${page}&size=20`),
  ]);
  const primary = accounts.find((a) => a.primary && a.status === "VERIFIED" && a.currency === CURRENCY) ?? null;
  const rentBank = wallet(summary, ["RENT_BANK"]);
  const rentCash = wallet(summary, ["RENT_CASH"]);
  const tiles: { label: string; amount: Money; note: string }[] = [
    {
      label: "Rent wallet",
      amount: wallet(summary, ["RENT_BANK", "RENT_CASH"]),
      note: Number(rentCash.amount) > 0
        ? `${formatMoney(rentBank)} by bank · ${formatMoney(rentCash)} in cash at our office`
        : nextCreditNote(summary.nextCreditAt, credit.label),
    },
    { label: "Referral wallet", amount: wallet(summary, ["REFERRAL"]), note: `Commission on your referrals' rent, credited ${credit.label}` },
    { label: "Withdrawal in progress", amount: total(summary.withdrawalInProgress), note: "Being paid out by our team" },
    { label: "Total withdrawn", amount: total(summary.totalWithdrawn), note: "Paid out to you so far" },
  ];
  const minimum = formatMoney({ amount: String(policy.minimumAmount), currency: CURRENCY });
  const referralCash = referralMode.mode === "OFFICE_CASH";
  const bankTo = primary ? `${primary.bankName} ${primary.accountNumberMasked}` : null;

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals"
        description={`Rent and referral earnings are credited to your wallets ${credit.label}, and paid out when our team makes a withdrawal: into your bank account, or in cash at our office.`} />

      <section className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-4" aria-label="Balances">
        {tiles.map((tile) => (
          <div key={tile.label} className="space-y-1 bg-surface p-6">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">{tile.label}</p>
            <p className="font-display text-3xl font-semibold tabular-nums">{formatMoney(tile.amount)}</p>
            <p className="text-xs text-muted">{tile.note}</p>
          </div>
        ))}
      </section>

      {profile.kycStatus !== "APPROVED" && (
        <Notice tone="warning">
          Verify your identity to be paid. <Link href="/profile/verification" className="underline">Start verification</Link>
        </Notice>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card title="How you are paid" description="When our team makes a withdrawal, your wallets move to withdrawal in progress and are paid out.">
          <dl className="space-y-5 text-sm">
            <div>
              <dt className="font-medium">Rent on containers paid by bank</dt>
              <dd className="mt-1 text-muted">
                {bankTo
                  ? <>Paid into {bankTo}.</>
                  : <>Paid into your bank account once you have a verified primary bank account. <Link href="/profile/bank-accounts" className="underline">Bank accounts</Link></>}
              </dd>
            </div>
            <div>
              <dt className="font-medium">Rent on containers paid in cash at our office</dt>
              <dd className="mt-1 text-muted">Collected in cash at our office.</dd>
            </div>
            <div>
              <dt className="font-medium">Referral earnings</dt>
              <dd className="mt-1 text-muted">
                {referralCash ? <>Collected in cash at our office.</> : <>Paid into {bankTo ?? "your bank account"}.</>}
              </dd>
            </div>
            <div className="border-t border-border pt-4 text-xs text-muted">
              <p>
                Cash is collected at <span className="whitespace-pre-line text-foreground">{policy.officeAddress}</span> ({policy.officeHours}).
                Bring photo ID; you sign a receipt for the cash.
              </p>
              <p className="mt-2">A withdrawal pays your wallets when they come to at least {minimum}; smaller amounts wait for the next one.</p>
            </div>
          </dl>
        </Card>

        <Card title={`Referral earnings in ${referralMode.year}`}
          description="Choose once a year how your referral earnings are paid.">
          {referralMode.chosen ? (
            <p className="text-sm">
              You chose to be paid <strong>{referralCash ? "in cash at our office" : "into your bank account"}</strong> in {referralMode.year}
              {referralMode.chosenAt && <span className="text-muted"> (on {formatDate(referralMode.chosenAt)})</span>}.
              <span className="mt-2 block text-muted">
                To change it before next year, <Link href="/support/new" className="underline">contact support</Link>.
              </span>
            </p>
          ) : (
            <ReferralModeForm year={referralMode.year} current={referralMode.mode} bankAvailable={primary !== null} />
          )}
        </Card>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">History</h2>
        {history.content.length === 0 ? (
          <EmptyState title="No withdrawals yet" description="Your earnings are paid out when our team next makes a withdrawal." />
        ) : (
          <>
            <DataTable columns={["Date", "Reference", "From", "To", "Amount", "Status"]}>
              {history.content.map((w) => (
                <tr key={w.id}>
                  <Cell className="text-muted">{formatDate(w.createdAt)}</Cell>
                  <Cell className="font-mono">{w.reference}</Cell>
                  <Cell>{EARNINGS_BALANCE_LABEL[w.balance]}</Cell>
                  <Cell>
                    {w.payoutMethod === "OFFICE_CASH"
                      ? <>Cash{w.payoutReference && <span className="block text-xs text-muted">Receipt {w.payoutReference}</span>}</>
                      : <>{w.bankName} <span className="text-muted">{w.bankAccountMasked}</span></>}
                  </Cell>
                  <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
                  <Cell>
                    <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
                    {w.failureReason && <span className="block max-w-xs truncate text-xs text-muted">{w.failureReason}</span>}
                    {w.status === "FAILED" && w.ticketId && (
                      <Link href={`/support/${w.ticketId}`} className="block text-xs text-gold-text hover:underline">See the support ticket</Link>
                    )}
                    {w.status === "PAID" && w.closedAt && <span className="block text-xs text-muted">on {formatDate(w.closedAt)}</span>}
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
