import type { Metadata } from "next";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { ColumnChart, type ColumnSeries } from "@/components/charts/column-chart";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { LeaseProgress } from "@/features/dashboard/lease-progress";
import { monthColumns, monthKey } from "@/features/dashboard/months";
import { ReturnsBreakdown } from "@/features/dashboard/returns-breakdown";
import { CURRENCY } from "@/lib/currency";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { CurrentUser } from "@/types/auth";
import type { EarningsSummary, PayoutMonth } from "@/types/earning";
import type { Money } from "@/types/marketplace";
import type { Portfolio } from "@/types/order";
import type { ReferralMonth } from "@/types/referral";
import type { Profile } from "@/types/user";
import { formatDate, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Dashboard" };

/** Months shown around the current one: the last five, this one, and the next six. */
const PAST = -5;
const AHEAD = 6;

const PAYOUT_SERIES: readonly ColumnSeries[] = [
  { key: "rentPaid", label: "Rent", fill: "fill-chart-1", swatch: "bg-chart-1" },
  { key: "capitalPaid", label: "Capital returned", fill: "fill-chart-2", swatch: "bg-chart-2" },
  { key: "rentScheduled", label: "Rent", fill: "fill-chart-1", swatch: "bg-chart-1", scheduled: true },
  { key: "capitalScheduled", label: "Capital returned", fill: "fill-chart-2", swatch: "bg-chart-2", scheduled: true },
];

const REFERRAL_SERIES: readonly ColumnSeries[] = [
  { key: "total", label: "Commission", fill: "fill-chart-3", swatch: "bg-chart-3" },
];

export default async function DashboardPage() {
  const [me, profile] = await Promise.all([
    authFetch<CurrentUser>("/api/v1/auth/me"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const needsVerification = profile.kycStatus !== "APPROVED";
  const investor = hasPermission(me.permissions, Permission.INVESTOR_PORTAL);
  const [portfolio, earnings, payoutMonths, referralMonths] = investor
    ? await Promise.all([
      authFetch<Portfolio>("/api/v1/portfolio"),
      authFetch<EarningsSummary>("/api/v1/earnings/summary"),
      // The charts are a convenience: a failure there must not break the dashboard.
      authFetch<PayoutMonth[]>(`/api/v1/earnings/monthly?from=${monthKey(PAST)}&to=${monthKey(AHEAD)}`).catch(() => null),
      authFetch<ReferralMonth[]>("/api/v1/referrals/monthly").catch(() => null),
    ])
    : [null, null, null, null];
  const list = (amounts: Money[] | undefined) => (amounts && amounts.length > 0
    ? amounts.map((m) => formatMoney(m, { compact: true })).join(" · ")
    : "—");
  const invested = portfolio && portfolio.totalsByCurrency.length > 0
    ? portfolio.totalsByCurrency.map((m) => formatMoney(m, { compact: true })).join(" · ")
    : "—";
  const kpis = [
    { label: "Total invested", value: invested, note: portfolio ? `${portfolio.activeHoldings} container(s) on lease` : "Investor accounts only" },
    { label: "Next payout", value: earnings?.nextPayout ? formatMoney(earnings.nextPayout.total, { compact: true }) : "—",
      note: earnings?.nextPayout ? `Due ${formatDate(earnings.nextPayout.dueOn)}: rent plus capital back` : earnings ? "Buy a container to start earning" : "Investor accounts only" },
    { label: "Available balance", value: list(earnings?.balances), note: earnings ? "Ready to withdraw to your bank account" : "Investor accounts only" },
  ];

  return (
    <div className="space-y-8">
      <section className="relative isolate overflow-hidden bg-ink px-6 py-12 text-on-ink sm:px-10 sm:py-16">
        <ContainerScene focus="side" className="absolute inset-0 -z-10 size-full" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-r from-ink via-ink/85 to-ink/10" />
        <div className="max-w-xl space-y-5">
          <Eyebrow tone="light">Investor Dashboard</Eyebrow>
          <h1 className="text-4xl leading-tight font-bold tracking-tight sm:text-5xl">Welcome back, {me.firstName}</h1>
          {needsVerification ? (
            <div className="space-y-5">
              <p className="text-on-ink/85">
                Verify your identity to start investing and to receive withdrawals.
              </p>
              <LinkButton href="/profile/verification">
                {profile.kycStatus === "PENDING" ? "View Verification Status" : "Verify My Identity"}
              </LinkButton>
            </div>
          ) : (
            <div className="space-y-5">
              <p className="text-on-ink/85">Your account is verified. Explore containers open for investment.</p>
              <LinkButton href="/marketplace">Browse The Marketplace</LinkButton>
            </div>
          )}
        </div>
      </section>

      <section className="grid gap-px bg-border sm:grid-cols-3" aria-label="Portfolio summary">
        {kpis.map((kpi) => (
          <div key={kpi.label} className="space-y-3 bg-surface p-6">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">{kpi.label}</p>
            <p className="font-display text-4xl font-semibold tabular-nums">{kpi.value}</p>
            <p className="text-xs text-muted">{kpi.note}</p>
          </div>
        ))}
      </section>

      {earnings && (
        <>
          <section className="grid gap-6 lg:grid-cols-3" aria-label="Payouts">
            <Card title="Monthly payouts" description="Rent plus capital back, by due date" className="lg:col-span-2">
              {payoutMonths === null ? (
                <EmptyState title="The chart could not be loaded" description="Your payouts are listed on the Earnings page." />
              ) : payoutMonths.length === 0 && earnings.holdings.length === 0 ? (
                <EmptyState title="No payouts yet" description="Buy a container and its monthly payouts appear here." />
              ) : (
                <ColumnChart series={PAYOUT_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Monthly payouts"
                  data={monthColumns(payoutMonths, PAST, AHEAD, CURRENCY, (m) => ({
                    rentPaid: m.rentPaid, capitalPaid: m.capitalPaid,
                    rentScheduled: m.rentScheduled, capitalScheduled: m.capitalScheduled,
                  }))} />
              )}
            </Card>
            <Card title="Your returns" description="Everything paid into your wallet">
              <ReturnsBreakdown currency={CURRENCY} parts={[
                { label: "Rent", amount: sum(earnings.rentPaid), fill: "fill-chart-1", swatch: "bg-chart-1" },
                { label: "Capital returned", amount: sum(earnings.capitalReturned), fill: "fill-chart-2", swatch: "bg-chart-2" },
                { label: "Referral commission", amount: sum(earnings.referralEarned), fill: "fill-chart-3", swatch: "bg-chart-3" },
              ]} />
            </Card>
          </section>

          <section className="grid gap-6 lg:grid-cols-2" aria-label="Leases and referrals">
            <Card title="Lease progress" description="Months paid out of each container's tenure"
              actions={<LinkButton href="/earnings" variant="secondary">Earnings</LinkButton>}>
              {earnings.holdings.some((h) => h.paid < h.installments)
                ? <LeaseProgress holdings={earnings.holdings} />
                : <EmptyState title="No active leases" description="Containers you own appear here with their payout progress." />}
            </Card>
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
          </section>
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
      <dt className="text-xs uppercase tracking-[0.1em] text-muted">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  );
}
