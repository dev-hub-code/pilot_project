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
import { WithdrawalRequestForm, type RequestableWallet } from "@/features/withdrawals/request-form";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { PageResponse } from "@/types/api";
import type { EarningsBalance, EarningsSummary } from "@/types/earning";
import type { Money } from "@/types/plan";
import type { ReferralOverview } from "@/types/referral";
import type { BankAccount, Profile } from "@/types/user";
import type { ReferralPayoutMode, Withdrawal, WithdrawalPolicy, WithdrawalStatus } from "@/types/withdrawal";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

const OPEN: WithdrawalStatus[] = ["PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "FAILED"];

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
  const credit = creditLabel(await getRentCredit());
  const [summary, accounts, profile, policy, referralMode, history, referrals] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<WithdrawalPolicy>("/api/v1/withdrawals/policy"),
    authFetch<ReferralPayoutMode>("/api/v1/withdrawals/referral-mode"),
    authFetch<PageResponse<Withdrawal>>(`/api/v1/withdrawals?page=${page}&size=20`),
    authFetch<ReferralOverview>("/api/v1/referrals/me"),
  ]);
  const primary = accounts.find((a) => a.primary && a.status === "VERIFIED" && a.currency === CURRENCY) ?? null;
  const rentBank = wallet(summary, ["RENT_BANK"]);
  const rentCash = wallet(summary, ["RENT_CASH"]);
  const tiles: { label: string; amount: Money; note: string }[] = [
    {
      label: "Returns wallet",
      amount: wallet(summary, ["RENT_BANK", "RENT_CASH"]),
      note: nextCreditNote(summary.nextCreditAt, credit),
    },
    { label: "Referral wallet", amount: wallet(summary, ["REFERRAL"]), note: `Commission on your referrals' investments, credited ${credit}` },
    { label: "Withdrawal in progress", amount: total(summary.withdrawalInProgress), note: "Being paid out by our team" },
    { label: "Total withdrawn", amount: total(summary.totalWithdrawn), note: "Paid out to you so far" },
  ];
  const minimum = formatMoney({ amount: String(policy.minimumAmount), currency: CURRENCY });
  const paidInCash = referralMode.mode === "OFFICE_CASH";
  const bankTo = primary ? `${primary.bankName ?? "Account"} ${primary.accountNumberMasked}` : null;
  const online = referrals.scheme === "ONLINE";
  // A wallet can be requested with a withdrawal from it not already open, once it reaches the minimum, if it can be paid.
  const openBalances = new Set(history.content.filter((w) => OPEN.includes(w.status)).map((w) => w.balance));
  // One choice covers every wallet: online investors are always paid by bank, network investors as they chose.
  const paidTo = paidInCash ? "Collected in cash at our office" : bankTo ? `Paid into ${bankTo}` : null;
  const requestable: RequestableWallet[] = ([
    { balance: "RENT_BANK", label: "Returns wallet", amount: rentBank, to: paidTo },
    { balance: "RENT_CASH", label: "Returns wallet (paid at office)", amount: rentCash, to: paidTo },
    { balance: "REFERRAL", label: "Referral wallet", amount: wallet(summary, ["REFERRAL"]), to: paidTo },
  ] satisfies { balance: EarningsBalance; label: string; amount: Money; to: string | null }[])
    .filter((w) => w.to !== null && !openBalances.has(w.balance) && Number(w.amount.amount) >= policy.minimumAmount)
    .map((w) => ({ balance: w.balance, label: w.label, amount: formatMoney(w.amount), to: w.to as string }));

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals"
        description={online
          ? `Returns and referral earnings are credited to your wallets ${credit}. Request a withdrawal at any time: it is paid into your bank account.`
          : `Returns and referral earnings are credited to your wallets ${credit}, and paid out when our team makes a withdrawal, the way you choose below.`} />

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
          Verify your identity to be paid. <Link href={await onboardingHref()} className="underline">Start verification</Link>
        </Notice>
      )}

      {online && (
        <Card title="Request a withdrawal" description={`Withdraw a whole wallet at any time, once it has at least ${minimum}.`}>
          {requestable.length > 0
            ? <WithdrawalRequestForm wallets={requestable} idempotencyKey={crypto.randomUUID()} />
            : <p className="text-sm text-muted">Nothing to withdraw right now. A wallet needs at least {minimum}, a way to be paid, and no withdrawal already in progress.</p>}
        </Card>
      )}

      <Card title="How you are paid"
        description={referralMode.changeable ? "One choice covers your returns and referral earnings. You can change it at any time; it applies to withdrawals made from then on." : "Your returns and referral earnings are always paid by bank transfer."}>
        <div className="space-y-5 text-sm">
          {referralMode.changeable ? (
            <>
              {referralMode.chosen && referralMode.chosenAt && (
                <p className="text-muted">Last changed on {formatDate(referralMode.chosenAt)}.</p>
              )}
              <ReferralModeForm current={referralMode.mode} bankAvailable={primary !== null} />
            </>
          ) : (
            <p>
              {bankTo
                ? <>Paid into <strong>{bankTo}</strong>.</>
                : <>Paid into your bank account once you have a verified primary bank account. <Link href="/profile/bank-accounts" className="underline">Bank accounts</Link></>}
            </p>
          )}
          <div className="border-t border-border pt-4 text-xs text-muted">
            {referralMode.changeable && (
              <p className="mb-2">
                Cash is collected at <span className="whitespace-pre-line text-foreground">{policy.officeAddress}</span> ({policy.officeHours}).
                Bring photo ID; you sign a receipt for the cash.
              </p>
            )}
            <p>A withdrawal pays your wallets when they come to at least {minimum}; smaller amounts wait for the next one.</p>
          </div>
        </div>
      </Card>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">History</h2>
        {history.content.length === 0 ? (
          <EmptyState title="No withdrawals yet" description={online ? "Withdrawals you request appear here." : "Your earnings are paid out when our team next makes a withdrawal."} />
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
