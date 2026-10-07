import type { Metadata } from "next";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { CurrentUser } from "@/types/auth";
import type { EarningsSummary } from "@/types/earning";
import type { Money } from "@/types/marketplace";
import type { Portfolio } from "@/types/order";
import type { Profile } from "@/types/user";
import { formatDate, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Dashboard" };

export default async function DashboardPage() {
  const [me, profile] = await Promise.all([
    authFetch<CurrentUser>("/api/v1/auth/me"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const needsVerification = profile.kycStatus !== "APPROVED";
  const investor = hasPermission(me.permissions, Permission.INVESTOR_PORTAL);
  const [portfolio, earnings] = investor
    ? await Promise.all([authFetch<Portfolio>("/api/v1/portfolio"), authFetch<EarningsSummary>("/api/v1/earnings/summary")])
    : [null, null];
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

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <dt className="text-xs uppercase tracking-[0.1em] text-muted">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  );
}
