import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowDownToLine, Banknote, ChevronRight, Clock, Coins, Landmark, Layers, Lock, Percent, ShieldCheck, TrendingUp, Wallet,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { EARNINGS_BALANCE_LABEL, nextCreditNote } from "@/features/earnings/labels";
import { investorStatusLabel, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { ReferralModeForm } from "@/features/withdrawals/referral-mode-form";
import { WithdrawalRequestForm, type RequestableWallet } from "@/features/withdrawals/request-form";
import { MetricCard, STATUS_ICON } from "@/features/withdrawals/ui";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { PageResponse } from "@/types/api";
import type { EarningsBalance, EarningsSummary } from "@/types/earning";
import type { BankAccount, Profile } from "@/types/user";
import type { ReferralPayoutMode, Withdrawal, WithdrawalOverview, WithdrawalPolicy } from "@/types/withdrawal";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

const WALLET_LABEL: Record<EarningsBalance, string> = {
  RENT_BANK: "Returns wallet", RENT_CASH: "Returns wallet (paid at office)", REFERRAL: "Referral wallet",
};

export default async function WithdrawalsPage({ searchParams }: PageProps<"/withdrawals">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const credit = creditLabel(await getRentCredit());
  const [summary, overview, accounts, profile, policy, referralMode, history] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<WithdrawalOverview>("/api/v1/withdrawals/overview"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<WithdrawalPolicy>("/api/v1/withdrawals/policy"),
    authFetch<ReferralPayoutMode>("/api/v1/withdrawals/referral-mode"),
    authFetch<PageResponse<Withdrawal>>(`/api/v1/withdrawals?page=${page}&size=20`),
  ]);
  const primary = accounts.find((a) => a.primary && a.status === "VERIFIED" && a.currency === CURRENCY) ?? null;
  const minimum = formatMoney(overview.minimum);
  const bankTo = primary ? `${primary.bankName ?? "Account"} ${primary.accountNumberMasked}` : null;
  const online = overview.canRequest;
  const requestable: RequestableWallet[] = overview.wallets
    .filter((w) => !w.openWithdrawalId && Number(w.available.amount) >= Number(overview.minimum.amount))
    .map((w) => ({ balance: w.balance, label: WALLET_LABEL[w.balance], available: w.available }));
  const inProgress = overview.wallets.filter((w) => w.openWithdrawalId);
  const walletsWithMoney = overview.wallets.filter((w) => Number(w.available.amount) > 0 || w.openWithdrawalId);
  const charges = [
    overview.feeFlat > 0 ? formatMoney({ amount: String(overview.feeFlat), currency: CURRENCY }) : null,
    overview.feePercent > 0 ? `${overview.feePercent}%` : null,
  ].filter(Boolean).join(" + ") || "None";
  const tax = overview.taxPercent > 0 ? `${overview.taxPercent}%` : "None";
  const kycApproved = profile.kycStatus === "APPROVED";

  return (
    <div className="space-y-8">
      <PageHeader title="Withdrawals"
        description={online
          ? `Your returns and referral earnings are credited to your wallets ${credit}. Withdraw them to your bank account whenever you like.`
          : `Your returns and referral earnings are credited to your wallets ${credit} and paid out when our team makes a withdrawal.`} />

      {!kycApproved && (
        <Notice tone="warning">
          Verify your identity to be paid. <Link href={await onboardingHref()} className="font-medium underline">Start verification</Link>
        </Notice>
      )}

      {/* Balance hero */}
      <section aria-label="Your balance" className="relative overflow-hidden rounded-2xl bg-ink text-on-ink shadow-lg">
        <div aria-hidden="true" className="pointer-events-none absolute -top-24 -right-24 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div aria-hidden="true" className="pointer-events-none absolute -bottom-32 left-1/3 size-72 rounded-full bg-gold-on-ink/10 blur-3xl" />
        <div className="relative grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.4fr_1fr] lg:items-center">
          <div className="space-y-5">
            <div className="space-y-1">
              <p className="flex items-center gap-2 text-sm text-on-ink/70"><Wallet className="size-4" aria-hidden="true" /> Available to withdraw</p>
              <p className="font-display text-4xl font-semibold tracking-tight tabular-nums sm:text-5xl">{formatMoney(overview.available)}</p>
              <p className="text-xs text-on-ink/60">{nextCreditNote(summary.nextCreditAt, credit)}</p>
            </div>
            <div className="flex flex-wrap gap-x-8 gap-y-3 text-sm">
              <div>
                <p className="text-on-ink/60">Pending withdrawal</p>
                <p className="font-semibold tabular-nums">{formatMoney(overview.pending)}</p>
              </div>
              <div>
                <p className="text-on-ink/60">Total withdrawn</p>
                <p className="font-semibold tabular-nums">{formatMoney(overview.withdrawn)}</p>
              </div>
              <div>
                <p className="text-on-ink/60">Minimum withdrawal</p>
                <p className="font-semibold tabular-nums">{minimum}</p>
              </div>
            </div>
            {online && requestable.length > 0 && kycApproved && (
              <a href="#request" className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-on-ink px-5 text-sm font-semibold text-ink transition-colors hover:bg-white">
                <ArrowDownToLine className="size-4" aria-hidden="true" /> Withdraw funds
              </a>
            )}
          </div>
          <ul className="space-y-2" aria-label="Wallets">
            {(walletsWithMoney.length > 0 ? walletsWithMoney : overview.wallets.slice(0, 1)).map((w) => (
              <li key={w.balance} className="flex items-center justify-between gap-4 rounded-xl border border-on-ink/10 bg-on-ink/5 px-4 py-3 backdrop-blur-sm">
                <div>
                  <p className="text-sm font-medium">{WALLET_LABEL[w.balance]}</p>
                  {w.openWithdrawalId
                    ? <Link href={`/withdrawals/${w.openWithdrawalId}`} className="text-xs text-gold-on-ink hover:underline">{w.openWithdrawalReference} in progress</Link>
                    : <p className="text-xs text-on-ink/60">Ready to withdraw</p>}
                </div>
                <p className="font-semibold tabular-nums">{formatMoney(w.available)}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Portfolio metrics */}
      <section aria-label="Your investments at a glance" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={Layers} label="Total investment" value={formatMoney(overview.totalInvestment)} />
        <MetricCard icon={TrendingUp} label="Total interest earned" value={formatMoney(overview.totalEarned)}
          note="Returns and referral commission" accent="text-emerald-700 bg-emerald-500/10 dark:text-emerald-400" />
        <MetricCard icon={Lock} label="Locked investment" value={formatMoney(overview.locked)}
          note="Principal under your plans' terms" accent="text-slate-600 bg-slate-500/10 dark:text-slate-300" />
        <MetricCard icon={Coins} label="Active plans" value={overview.activePlans}
          note={`${overview.investments.length} investment${overview.investments.length === 1 ? "" : "s"} in all`} accent="text-amber-700 bg-amber-500/10 dark:text-amber-400" />
      </section>

      {/* Request */}
      {online && (
        <div id="request" className="grid scroll-mt-24 gap-6 lg:grid-cols-[1fr_340px] lg:items-start">
          <Card title="Request a withdrawal" description="Four quick steps. You see exactly what you will receive before you confirm.">
            {inProgress.map((w) => (
              <Link key={w.balance} href={`/withdrawals/${w.openWithdrawalId}`}
                className="mb-4 flex items-center justify-between gap-3 rounded-lg border border-border bg-background px-4 py-3 text-sm hover:border-gold">
                <span>
                  <span className="font-medium">{WALLET_LABEL[w.balance]}</span>
                  <span className="text-muted">: withdrawal <span className="font-mono">{w.openWithdrawalReference}</span> is in progress</span>
                </span>
                <ChevronRight className="size-4 text-muted" aria-hidden="true" />
              </Link>
            ))}
            {!kycApproved ? (
              <EmptyState title="Verify your identity first" description="Withdrawals are paid once your identity is verified." />
            ) : requestable.length > 0 ? (
              <WithdrawalRequestForm wallets={requestable} bankAccounts={overview.bankAccounts} cash={overview.payoutMethod === "OFFICE_CASH"}
                minimum={overview.minimum} idempotencyKey={crypto.randomUUID()} />
            ) : (
              <EmptyState title="Nothing to withdraw right now"
                description={`A wallet needs at least ${minimum} and no withdrawal already in progress. Earnings are credited ${credit}.`} />
            )}
          </Card>

          <aside className="space-y-4">
            <div className="rounded-xl border border-border bg-surface p-5 shadow-sm">
              <h2 className="mb-4 text-sm font-semibold">Good to know</h2>
              <ul className="space-y-4 text-sm">
                <Fact icon={Percent} title="Charges and tax" text={`Charges: ${charges}. Tax withheld (TDS): ${tax}. Both are shown before you confirm.`} />
                <Fact icon={Clock} title="Processing time"
                  text={overview.processingDays ? `Usually paid within ${overview.processingDays} working days of approval.` : "Reviewed by our finance team, then paid to your bank."} />
                <Fact icon={Landmark} title="Paid to"
                  text={overview.payoutMethod === "OFFICE_CASH" ? "Cash at our office." : bankTo ? `${bankTo}, or another verified account.` : "A verified bank account. Add one under Bank accounts."} />
                <Fact icon={ShieldCheck} title="Secure" text="Confirmed with your password. You are notified at every step." />
              </ul>
            </div>
            {overview.bankAccounts.length === 0 && overview.payoutMethod !== "OFFICE_CASH" && (
              <Notice tone="warning">You need a verified bank account to withdraw. <Link href="/profile/bank-accounts" className="font-medium underline">Add one</Link></Notice>
            )}
          </aside>
        </div>
      )}

      {/* Investments */}
      {overview.investments.length > 0 && (
        <section className="space-y-4" aria-label="Your investments">
          <div>
            <h2 className="text-lg font-semibold tracking-tight">Withdrawal eligibility by investment</h2>
            <p className="text-sm text-muted">Earnings from each investment are credited to your wallets, where you can withdraw them. Principal stays invested.</p>
          </div>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {overview.investments.map((i) => {
              const invested = Number(i.invested.amount);
              const earnedShare = invested > 0 ? Math.min(100, (Number(i.earned.amount) / invested) * 100) : 0;
              return (
                <article key={i.holdingId} className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-5 shadow-sm">
                  <header className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{i.productTitle}</p>
                      <p className="font-mono text-xs text-muted">{i.productCode}</p>
                    </div>
                    <StatusBadge tone={i.status === "ACTIVE" ? "success" : i.status === "MATURED" ? "neutral" : "warning"}>
                      {i.status === "AWAITING_ACCEPTANCE" ? "Awaiting agreement" : i.status === "ACTIVE" ? "Active" : "Matured"}
                    </StatusBadge>
                  </header>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="rounded-lg bg-emerald-500/5 p-3">
                      <p className="text-xs text-muted">Earned (withdrawable)</p>
                      <p className="font-semibold tabular-nums text-emerald-700 dark:text-emerald-400">{formatMoney(i.earned)}</p>
                    </div>
                    <div className="rounded-lg bg-background p-3">
                      <p className="flex items-center gap-1 text-xs text-muted"><Lock className="size-3" aria-hidden="true" /> Locked</p>
                      <p className="font-semibold tabular-nums">{formatMoney(i.lockedPrincipal)}</p>
                    </div>
                  </div>
                  <div className="space-y-1.5">
                    <div className="flex justify-between text-xs text-muted">
                      <span>Earned so far</span><span>{earnedShare.toFixed(1)}% of {formatMoney(i.invested)}</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-border">
                      <div className="h-full rounded-full bg-emerald-500" style={{ width: `${earnedShare}%` }} />
                    </div>
                  </div>
                  <p className="text-xs leading-relaxed text-muted">
                    {i.lockReason}{i.nextUnlock ? ` Next on ${formatDate(i.nextUnlock)}.` : ""}
                    {i.leaseEndsOn ? ` Term ends ${formatDate(i.leaseEndsOn)}.` : ""}
                  </p>
                </article>
              );
            })}
          </div>
        </section>
      )}

      {/* History */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold tracking-tight">Withdrawal history</h2>
        {history.content.length === 0 ? (
          <EmptyState title="No withdrawals yet" description={online ? "Withdrawals you request appear here." : "Your earnings are paid out when our team next makes a withdrawal."} />
        ) : (
          <>
            <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface shadow-sm">
              {history.content.map((w) => {
                const { icon: Icon, tone } = STATUS_ICON[w.status];
                return (
                  <li key={w.id}>
                    <Link href={`/withdrawals/${w.id}`} className="flex items-center gap-4 px-4 py-4 transition-colors hover:bg-background sm:px-5">
                      <span className={`flex size-10 shrink-0 items-center justify-center rounded-full ${tone}`}>
                        <Icon className="size-4.5" aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="flex flex-wrap items-center gap-x-2 text-sm font-medium">
                          <span className="font-mono">{w.reference}</span>
                          <span className="text-muted">·</span>
                          <span className="font-normal text-muted">{EARNINGS_BALANCE_LABEL[w.balance]}</span>
                        </p>
                        <p className="truncate text-xs text-muted">
                          {formatDate(w.createdAt)} · {w.payoutMethod === "OFFICE_CASH" ? "Cash at the office" : `${w.bankName ?? "Bank"} ${w.bankAccountMasked ?? ""}`}
                          {w.status === "FAILED" && w.failureReason ? ` · ${w.failureReason}` : ""}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-semibold tabular-nums">{formatMoney(w.net)}</p>
                        {w.net.amount !== w.amount.amount && <p className="text-xs text-muted tabular-nums">of {formatMoney(w.amount)}</p>}
                      </div>
                      <div className="hidden w-40 justify-end sm:flex">
                        <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{investorStatusLabel(w.status, w.infoRequested)}</StatusBadge>
                      </div>
                      <ChevronRight className="size-4 shrink-0 text-muted" aria-hidden="true" />
                    </Link>
                  </li>
                );
              })}
            </ul>
            <Pagination page={history} basePath="/withdrawals" params={{}} />
          </>
        )}
      </section>

      {/* Payout preference */}
      <Card title="How you are paid"
        description={referralMode.changeable ? "One choice covers your returns and referral earnings. You can change it at any time; it applies to withdrawals made from then on." : "Your returns and referral earnings are always paid by bank transfer."}>
        <div className="space-y-5 text-sm">
          {referralMode.changeable ? (
            <>
              {referralMode.chosen && referralMode.chosenAt && <p className="text-muted">Last changed on {formatDate(referralMode.chosenAt)}.</p>}
              <ReferralModeForm current={referralMode.mode} bankAvailable={primary !== null} />
              <p className="border-t border-border pt-4 text-xs text-muted">
                Cash is collected at <span className="whitespace-pre-line text-foreground">{policy.officeAddress}</span> ({policy.officeHours}).
                Bring photo ID; you sign a receipt for the cash.
              </p>
            </>
          ) : (
            <p className="flex items-center gap-3">
              <Banknote className="size-5 text-gold-text" aria-hidden="true" />
              {bankTo
                ? <span>Paid into <strong>{bankTo}</strong> unless you choose another verified account when you withdraw.</span>
                : <span>Paid into your bank account once you have a verified bank account. <Link href="/profile/bank-accounts" className="underline">Bank accounts</Link></span>}
            </p>
          )}
        </div>
      </Card>
    </div>
  );
}

function Fact({ icon: Icon, title, text }: { icon: typeof Clock; title: string; text: string }) {
  return (
    <li className="flex gap-3">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div>
        <p className="font-medium">{title}</p>
        <p className="text-muted">{text}</p>
      </div>
    </li>
  );
}
