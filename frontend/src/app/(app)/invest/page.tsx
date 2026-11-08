import type { Metadata } from "next";
import Link from "next/link";
import { CalendarClock, FileSignature, Layers, Percent, Wallet, WalletCards } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { yearsLabel } from "@/features/orders/lease-years";
import { PlanCard, highestReturnCode } from "@/features/plans/plan-card";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PaymentMethods } from "@/types/order";
import type { Plan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plans" };

const STEPS = [
  { icon: Layers, title: "Choose your units", body: "Open a plan and enter how many units to buy to see your exact returns." },
  { icon: WalletCards, title: "Pay for your order", body: "Pay by bank, in cash at our office, or from your balance when reinvesting." },
  { icon: FileSignature, title: "Accept and earn", body: "Accept your investment agreement and returns start at the next full hour." },
];

/** The quick facts in the banner, from the plans listed: the best return, the tenures and the smallest start. */
function facts(plans: readonly Plan[]) {
  if (plans.length === 0) return null;
  const tenures = plans.map((p) => p.tenureYears);
  const shortest = Math.min(...tenures);
  const longest = Math.max(...tenures);
  const cheapest = plans.reduce((best, p) =>
    Number(p.unitPrice.amount) * p.minUnits < Number(best.unitPrice.amount) * best.minUnits ? p : best);
  return [
    { icon: Percent, label: "Returns up to", value: `${formatPercent(Math.max(...plans.map((p) => p.annualReturnPercent)))} p.a.` },
    { icon: CalendarClock, label: "Tenures", value: shortest === longest ? yearsLabel(shortest) : `${shortest}–${longest} years` },
    { icon: Wallet, label: "Start from", value: formatMoney({
      amount: (Number(cheapest.unitPrice.amount) * cheapest.minUnits).toFixed(2), currency: cheapest.unitPrice.currency,
    }, { compact: true }) },
  ];
}

/** The plans for the investor's type, shortest tenure first; closed ones on their own tab. */
export default async function PlansPage({ searchParams }: PageProps<"/invest">) {
  const params = await searchParams;
  const closed = params.status === "CLOSED";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "12", status: closed ? "CLOSED" : "OPEN" });
  const reinvesting = params.reinvest === "1";
  const [plans, payment] = await Promise.all([
    authFetch<PageResponse<Plan>>(`/api/v1/plans?${query}`),
    // Only for the reinvest note; the page works without it.
    reinvesting ? authFetch<PaymentMethods>("/api/v1/payments/methods").catch(() => null) : Promise.resolve(null),
  ]);
  const available = payment?.methods.includes("WALLET") ? payment.availableBalance : undefined;
  const summary = closed ? null : facts(plans.content);
  const top = closed ? undefined : highestReturnCode(plans.content);

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="plans-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="terminal-above" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.9)_50%,rgb(11_42_85/0.55)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.3fr_1fr] lg:items-end lg:p-10">
          <div className="space-y-3">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Plans</p>
            <h1 id="plans-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
              Investment <span className="text-gold-on-ink">plans</span>
            </h1>
            <p className="max-w-lg text-sm leading-relaxed text-on-ink/75">
              Choose a plan by its tenure and buy units of it. Once your payment is confirmed and you accept your
              investment agreement, you receive returns every month of the tenure.
            </p>
          </div>
          {summary && (
            <dl className="grid grid-cols-3 gap-2 sm:gap-3">
              {summary.map(({ icon: Icon, label, value }) => (
                <div key={label} className="flex flex-col-reverse justify-end gap-1 rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-3 backdrop-blur-md sm:p-4">
                  <dd className="font-display text-base font-semibold whitespace-nowrap tabular-nums sm:text-lg">{value}</dd>
                  <dt className="flex items-center gap-1.5 text-[11px] text-on-ink/65 sm:text-xs">
                    <Icon aria-hidden="true" className="size-3.5 text-gold-on-ink" /> {label}
                  </dt>
                </div>
              ))}
            </dl>
          )}
        </div>
      </section>

      {reinvesting && available && Number(available.amount) > 0 && (
        <div role="status" className="flex items-start gap-4 rounded-2xl border border-gold/30 bg-gold/[0.06] p-5">
          <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold text-on-gold">
            <Wallet className="size-5" />
          </span>
          <p className="text-sm leading-relaxed">
            You have <span className="font-semibold">{formatMoney(available)}</span> to reinvest. Choose a plan and place
            an order of up to that amount; on the order page, pay it from your available balance with your password.
          </p>
        </div>
      )}

      {/* ------------------------------------------------------------------- tabs */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <nav aria-label="Filter plans" className="inline-flex rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {[{ href: "/invest", label: "Open for investment", on: !closed }, { href: "/invest?status=CLOSED", label: "Closed", on: closed }].map((tab) => (
            <Link key={tab.href} href={tab.href} aria-current={tab.on ? "page" : undefined}
              className={`rounded-lg px-4 py-2 font-medium transition-colors duration-200 ${tab.on
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              {tab.label}
            </Link>
          ))}
        </nav>
        {plans.totalElements > 0 && (
          <p className="text-sm text-muted">
            {plans.totalElements} {plans.totalElements === 1 ? "plan" : "plans"} {closed ? "closed" : "open to you"}
          </p>
        )}
      </div>

      {/* ------------------------------------------------------------------ plans */}
      {plans.content.length === 0 ? (
        <EmptyState title="No plans here right now" description="New plans are added regularly." />
      ) : (
        // Rows that fill evenly: four plans sit two by two (four across on very wide screens), not three and one.
        <ul className={`grid gap-6 sm:grid-cols-2 ${plans.content.length % 4 === 0 ? "2xl:grid-cols-4" : "xl:grid-cols-3"}`}>
          {plans.content.map((plan) => (
            <li key={plan.id} className="flex"><PlanCard plan={plan} href={`/invest/${plan.id}`} action="Invest"
              highlight={plan.code === top} /></li>
          ))}
        </ul>
      )}
      <Pagination page={plans} basePath="/invest" params={{ status: closed ? "CLOSED" : undefined }} />

      {/* ------------------------------------------------------------ how to invest */}
      {!closed && plans.content.length > 0 && (
        <section aria-labelledby="how-title" className="rounded-3xl border border-border bg-surface p-6 shadow-sm sm:p-8">
          <h2 id="how-title" className="text-lg font-semibold tracking-tight">How investing works</h2>
          <ol className="mt-6 grid gap-6 md:grid-cols-3">
            {STEPS.map(({ icon: Icon, title, body }, index) => (
              <li key={title} className="group flex gap-4">
                <span aria-hidden="true" className="relative flex size-11 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text transition-colors duration-300 group-hover:bg-gold group-hover:text-on-gold">
                  <Icon className="size-5" />
                  <span className="absolute -top-1.5 -right-1.5 flex size-5 items-center justify-center rounded-full bg-ink text-[10px] font-semibold text-on-ink dark:bg-gold dark:text-on-gold">
                    {index + 1}
                  </span>
                </span>
                <span className="text-sm leading-relaxed">
                  <span className="block font-semibold">{title}</span>
                  <span className="block text-muted">{body}</span>
                </span>
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}
