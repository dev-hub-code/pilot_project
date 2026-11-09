import { ArrowDownToLine, Coins, Landmark, LayoutDashboard, Wallet } from "lucide-react";
import { LogoMark } from "@/components/brand/logo";
import { ProgressBar } from "@/components/ui/progress-bar";
import { sum, times } from "@/features/plans/returns";
import type { PublicPlan } from "@/types/plan";
import { formatMoney } from "@/utils/money";
import { CtaLink, Heading, Pill, Section } from "./landing-ui";
import { EXAMPLE_MONTHS_IN, EXAMPLE_MONTHS_WITHDRAWN, exampleHoldings } from "./plan-facts";

/**
 * What an investor's account looks like, and the one place the page explains the difference between returns
 * credited, the wallet balance and money withdrawn. The account shown is a hypothetical investor in the live plans,
 * labelled "Example"; its figures follow the backend's rules (features/plans/returns.ts).
 */
export function DashboardPreview({ plans, credit }: { plans: PublicPlan[]; credit: string }) {
  const terms = [
    {
      icon: Coins, title: "Returns credited",
      body: `Your returns are earned through every month of your term and credited to your wallet ${credit}. Each credit is listed under Earnings.`,
    },
    {
      icon: Wallet, title: "Wallet balance",
      body: "Returns credited to you that you have not withdrawn yet. It is yours to withdraw.",
    },
    {
      icon: Landmark, title: "Total withdrawn",
      body: "What has left your wallet for your bank account, before any charge and tax withheld. Each withdrawal is listed with its status.",
    },
  ];
  return (
    <Section id="dashboard" labelledBy="dashboard-heading">
      <div className="grid gap-12 lg:grid-cols-[1fr_1.25fr] lg:items-center lg:gap-16">
        <div className="space-y-6">
          <Pill>Your Account</Pill>
          <Heading id="dashboard-heading" lead="Every Investment," soft="Every Rupee, In One Place" />
          <p className="max-w-xl text-sm leading-relaxed text-muted sm:text-base">
            Your dashboard shows each investment with its agreement and term, and keeps three figures apart:
          </p>
          <dl className="space-y-5">
            {terms.map(({ icon: Icon, title, body }) => (
              <div key={title} className="flex gap-4">
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-ink text-brass-on-ink">
                  <Icon className="size-[18px]" />
                </span>
                <div>
                  <dt className="font-semibold">{title}</dt>
                  <dd className="mt-1 text-sm leading-relaxed text-muted">{body}</dd>
                </div>
              </div>
            ))}
          </dl>
          <CtaLink href="/register">Create Your Account</CtaLink>
        </div>
        {plans.length > 0 && <ExampleDashboard plans={plans} />}
      </div>
    </Section>
  );
}

/** A drawing of the investor dashboard for a hypothetical investor, some months into their terms. */
function ExampleDashboard({ plans }: { plans: PublicPlan[] }) {
  const holdings = exampleHoldings(plans);
  const monthly = sum(holdings.map((h) => h.returns.monthly));
  const tiles = [
    { icon: LayoutDashboard, label: "Total invested", value: sum(holdings.map((h) => h.returns.amount)) },
    { icon: Coins, label: "Returns credited", value: times(monthly, EXAMPLE_MONTHS_IN) },
    { icon: Wallet, label: "Wallet balance", value: times(monthly, EXAMPLE_MONTHS_IN - EXAMPLE_MONTHS_WITHDRAWN) },
    { icon: ArrowDownToLine, label: "Total withdrawn", value: times(monthly, EXAMPLE_MONTHS_WITHDRAWN) },
  ];
  return (
    <figure aria-labelledby="example-caption" className="relative">
      <div className="overflow-hidden rounded-[1.75rem] border border-glass-border bg-surface shadow-[0_40px_80px_-40px_rgb(11_42_85/0.55)]">
        {/* Window bar */}
        <div className="flex items-center justify-between gap-3 bg-ink px-5 py-3 text-on-ink">
          <span className="flex items-center gap-2 font-wordmark text-xs tracking-[0.2em]">
            <LogoMark className="h-5 w-6" /> SEALEASE
          </span>
          <span className="rounded-full border border-brass-on-ink/50 px-2.5 py-0.5 text-[11px] font-medium text-brass-on-ink">Example</span>
        </div>

        <div className="space-y-5 p-4 sm:p-6">
          <dl className="grid grid-cols-2 gap-3">
            {tiles.map(({ icon: Icon, label, value }) => (
              <div key={label} className="rounded-2xl border border-border bg-background p-3.5 sm:p-4">
                <dt className="flex items-center gap-1.5 text-xs text-muted"><Icon aria-hidden="true" className="size-3.5 text-gold-text" /> {label}</dt>
                <dd className="mt-1.5 font-display text-base font-semibold tabular-nums sm:text-xl">{formatMoney(value)}</dd>
              </div>
            ))}
          </dl>

          <div className="overflow-hidden rounded-2xl border border-border">
            <p className="border-b border-border bg-background px-4 py-2.5 text-xs font-semibold tracking-wide text-muted uppercase">Investments</p>
            <ul className="divide-y divide-border">
              {holdings.map(({ plan, units, returns }) => (
                <li key={plan.code} className="space-y-2 px-4 py-3.5 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <p className="font-semibold">{plan.title}</p>
                    <span className="rounded-full bg-gold/10 px-2.5 py-0.5 text-xs text-gold-text">Running</span>
                  </div>
                  <p className="flex flex-wrap justify-between gap-x-4 text-xs text-muted">
                    <span>{units} units · {formatMoney(returns.amount)}</span>
                    <span className="font-medium text-foreground tabular-nums">{formatMoney(returns.monthly)} a month</span>
                  </p>
                  <div className="flex items-center gap-3 text-[11px] text-muted">
                    <div className="flex-1">
                      <ProgressBar percent={(EXAMPLE_MONTHS_IN / returns.months) * 100} label={`${plan.title} term progress`} fill="fill-brass" />
                    </div>
                    Month {EXAMPLE_MONTHS_IN} of {returns.months}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
      <figcaption id="example-caption" className="mt-3 text-xs leading-relaxed text-muted">
        Example: a hypothetical investor {EXAMPLE_MONTHS_IN} months into {holdings.length === 1 ? "a plan" : "two of today's plans"}, at
        each plan&apos;s current rate, who has withdrawn {EXAMPLE_MONTHS_WITHDRAWN} months of returns. Not a real account.
      </figcaption>
    </figure>
  );
}
