import type { Metadata } from "next";
import { cookies } from "next/headers";
import { ColumnChart, type ColumnSeries } from "@/components/charts/column-chart";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { type Stat, StatGrid } from "@/components/ui/stat-grid";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { monthColumns, monthKey } from "@/features/dashboard/months";
import { ReturnsBreakdown } from "@/features/dashboard/returns-breakdown";
import { TermProgress } from "@/features/dashboard/term-progress";
import { UpcomingReturns } from "@/features/dashboard/upcoming-returns";
import { AgreementPrompt } from "@/features/agreement/agreement-prompt";
import { VerifyEmailDialog } from "@/features/auth/verify-email-dialog";
import { nextCreditNote } from "@/features/earnings/labels";
import { CURRENCY } from "@/lib/currency";
import { Permission, hasPermission } from "@/lib/permissions";
import { cookieNames } from "@/lib/server/auth/cookies";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { CurrentUser } from "@/types/auth";
import type { EarningsSummary, PayoutMonth } from "@/types/earning";
import type { Money } from "@/types/plan";
import type { Portfolio } from "@/types/order";
import type { ReferralMonth, ReferralOverview } from "@/types/referral";
import type { Profile } from "@/types/user";
import { humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { holdingBreakdown } from "@/features/orders/holding-summary";

export const metadata: Metadata = { title: "Dashboard" };

/** Months shown around the current one: the last five, this one, and the next six. */
const PAST = -5;
const AHEAD = 6;

const PAYOUT_SERIES: readonly ColumnSeries[] = [
  { key: "rentPaid", label: "Returns", fill: "fill-chart-1", swatch: "bg-chart-1" },
  { key: "capitalPaid", label: "Capital returned", fill: "fill-chart-2", swatch: "bg-chart-2" },
  { key: "rentScheduled", label: "Returns", fill: "fill-chart-1", swatch: "bg-chart-1", scheduled: true },
  { key: "capitalScheduled", label: "Capital returned", fill: "fill-chart-2", swatch: "bg-chart-2", scheduled: true },
];

/** Plans pay rent only; capital appears only for units bought before that, so its series shows only then. */
const RENT_SERIES = PAYOUT_SERIES.filter((s) => !s.key.startsWith("capital"));

const REFERRAL_SERIES: readonly ColumnSeries[] = [
  { key: "total", label: "Commission", fill: "fill-chart-3", swatch: "bg-chart-3" },
];

export default async function DashboardPage() {
  const credit = creditLabel(await getRentCredit());
  const [me, profile] = await Promise.all([
    authFetch<CurrentUser>("/api/v1/auth/me"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const needsVerification = profile.kycStatus !== "APPROVED";
  const setupHref = !needsVerification ? null
    : profile.kycStatus === "PENDING" ? "/profile/verification" : await onboardingHref();
  // The email reminder pops up once per sign-in; onboarding keeps asking after that.
  const emailPromptShown = (await cookies()).get(cookieNames().emailPrompt)?.value === (await requireSession()).sessionId;
  const investor = hasPermission(me.permissions, Permission.INVESTOR_PORTAL);
  const [portfolio, earnings, payoutMonths, referralMonths, referrals] = investor
    ? await Promise.all([
      authFetch<Portfolio>("/api/v1/portfolio"),
      authFetch<EarningsSummary>("/api/v1/earnings/summary"),
      // The charts are a convenience: a failure there must not break the dashboard.
      authFetch<PayoutMonth[]>(`/api/v1/earnings/monthly?from=${monthKey(PAST)}&to=${monthKey(AHEAD)}`).catch(() => null),
      authFetch<ReferralMonth[]>("/api/v1/referrals/monthly").catch(() => null),
      authFetch<ReferralOverview>("/api/v1/referrals/me").catch(() => null),
    ])
    : [null, null, null, null, null];
  // Only online investors request withdrawals; network investors are paid when our team makes one.
  const canWithdraw = referrals?.scheme === "ONLINE";
  // Online investors can also put their returns and referral commission into a new plan (paid from the balance).
  const reinvestable = (earnings?.wallet ?? [])
    .filter((w) => w.balance === "RENT_BANK" || w.balance === "REFERRAL")
    .reduce((total, w) => total + Number(w.amount.amount), 0);
  const canReinvest = canWithdraw && reinvestable > 0;
  const list = (amounts: Money[] | undefined) => (amounts && amounts.length > 0
    ? amounts.map((m) => formatMoney(m, { compact: true })).join(" · ")
    : "—");
  const invested = portfolio && portfolio.totalsByCurrency.length > 0
    ? portfolio.totalsByCurrency.map((m) => formatMoney(m, { compact: true })).join(" · ")
    : "—";
  // Only investments made before plans became returns-only return capital.
  const hasCapital = (earnings?.capitalReturned.length ?? 0) > 0
    || (payoutMonths ?? []).some((m) => Number(m.capitalPaid.amount) > 0 || Number(m.capitalScheduled.amount) > 0);
  const monthlyIncome = (earnings?.leases ?? []).reduce((total, l) => total + Number(l.monthlyRent.amount), 0);
  const stats: Stat[] = [
    { label: "Total invested", value: invested, note: portfolio ? (holdingBreakdown(portfolio.holdings) || "No investments yet") : undefined },
    { label: "Returns earned", value: list(earnings?.rentPaid),
      note: monthlyIncome > 0 ? `${formatMoney({ amount: monthlyIncome.toFixed(2), currency: CURRENCY })} a month` : "Credited to your wallet" },
    { label: "Available balance", value: list(earnings?.balances), note: earnings ? nextCreditNote(earnings.nextCreditAt, credit) : undefined },
    { label: "Total withdrawn", value: list(earnings?.totalWithdrawn),
      note: earnings && earnings.withdrawalInProgress.length > 0 ? `${list(earnings.withdrawalInProgress)} in progress` : "Paid out to you so far" },
  ];

  return (
    <div className="space-y-6">
      {/* Signing in does not wait for the address to be confirmed. */}
      {!me.emailVerified && !emailPromptShown && <VerifyEmailDialog email={me.email} />}
      <PageHeader title={`Welcome back, ${me.firstName}`} description="Your investments and returns at a glance."
        actions={!needsVerification && (
          <div className="flex flex-wrap gap-2">
            {canWithdraw && <LinkButton href="/withdrawals" variant="secondary">Withdraw</LinkButton>}
            {canReinvest && <LinkButton href="/invest?reinvest=1" variant="secondary">Reinvest</LinkButton>}
            <LinkButton href="/invest">Invest</LinkButton>
          </div>
        )} />
      {needsVerification && (
        <section aria-label="Account setup" className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm">Verify your identity to start investing and to receive withdrawals.</p>
          {/* Account setup in order: personal details, identity verification, then a bank account. */}
          <LinkButton href={setupHref ?? "/profile/verification"}>
            {profile.kycStatus === "PENDING" ? "View Verification Status" : "Complete Onboarding"}
          </LinkButton>
        </section>
      )}

      {portfolio && <AgreementPrompt holdings={portfolio.holdings} />}

      <StatGrid stats={stats} label="Portfolio summary" />

      {earnings && (
        <>
          <section className="grid gap-6 lg:grid-cols-3" aria-label="Returns">
            <Card title="Monthly returns" description={`Credited to your wallet ${credit}, by month`}
              className="lg:col-span-2">
              {payoutMonths === null ? (
                <EmptyState title="The chart could not be loaded" description="Your returns are listed on the Earnings page." />
              ) : payoutMonths.length === 0 && earnings.leases.length === 0 ? (
                <EmptyState title="No returns yet" description="Invest in a plan and the returns it earns each month appear here." />
              ) : (
                <ColumnChart series={hasCapital ? PAYOUT_SERIES : RENT_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Returns by month"
                  data={monthColumns(payoutMonths, PAST, AHEAD, CURRENCY, (m) => ({
                    rentPaid: m.rentPaid, capitalPaid: m.capitalPaid,
                    rentScheduled: m.rentScheduled, capitalScheduled: m.capitalScheduled,
                  }))} />
              )}
            </Card>
            <Card title="Upcoming returns" description="Scheduled for the months ahead">
              {payoutMonths === null
                ? <EmptyState title="Could not be loaded" description="Your returns are listed on the Earnings page." />
                : <UpcomingReturns months={payoutMonths} fromMonth={monthKey(0)} />}
            </Card>
          </section>

          <section className="grid gap-6 lg:grid-cols-3" aria-label="Terms and earnings">
            <Card title="Term progress" description="How far each investment has come" className="lg:col-span-2"
              actions={<LinkButton href="/earnings" variant="secondary">Earnings</LinkButton>}>
              <TermProgress leases={earnings.leases} />
            </Card>
            <Card title="Your returns" description="Everything paid into your wallet">
              <ReturnsBreakdown currency={CURRENCY} parts={[
                { label: "Returns", amount: sum(earnings.rentPaid), fill: "fill-chart-1", swatch: "bg-chart-1" },
                ...(hasCapital ? [{ label: "Capital returned", amount: sum(earnings.capitalReturned), fill: "fill-chart-2", swatch: "bg-chart-2" }] : []),
                { label: "Referral commission", amount: sum(earnings.referralEarned), fill: "fill-chart-3", swatch: "bg-chart-3" },
              ]} />
            </Card>
          </section>

          <Card title="Referral commission" description="Earned from your network, last six months"
            actions={<LinkButton href="/referrals" variant="secondary">Referrals</LinkButton>}>
            {referralMonths && referralMonths.length > 0 ? (
              <ColumnChart series={REFERRAL_SERIES} currency={CURRENCY} label="Referral commission by month"
                data={monthColumns(referralMonths, PAST, 0, CURRENCY, (m) => ({ total: m.total }))} />
            ) : (
              <EmptyState title="No commission yet"
                description="Share your referral link: you earn a share of what the people you invite invest, every month." />
            )}
          </Card>
        </>
      )}

      <Card title="Account">
        <dl className="grid gap-6 text-sm sm:grid-cols-4">
          <Item label="Account status">
            <StatusBadge tone={toneFor(me.status)}>{humanize(me.status)}</StatusBadge>
          </Item>
          <Item label="Identity verification">
            <StatusBadge tone={toneFor(profile.kycStatus)}>{humanize(profile.kycStatus)}</StatusBadge>
          </Item>
          <Item label="Roles">{me.roles.join(", ") || "—"}</Item>
        </dl>
      </Card>
    </div>
  );
}

/** The platform-currency amount of a list of per-currency totals. */
function sum(amounts: readonly Money[]): number {
  return amounts.filter((m) => m.currency === CURRENCY).reduce((total, m) => total + Number(m.amount), 0);
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  );
}
