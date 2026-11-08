import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, Briefcase, CheckCircle2, Package, Wallet } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { AgreementPrompt } from "@/features/agreement/agreement-prompt";
import { nextCreditNote } from "@/features/earnings/labels";
import { HOLDING_LABEL, HoldingCard } from "@/features/orders/holding-card";
import { investmentsHeld, holdingBreakdown } from "@/features/orders/holding-summary";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { LeaseAgreement } from "@/types/agreement";
import type { EarningsSummary } from "@/types/earning";
import type { HoldingStatus, Portfolio } from "@/types/order";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Portfolio" };

const FILTERS: { status: HoldingStatus | null; label: string }[] = [
  { status: null, label: "All" },
  { status: "ACTIVE", label: HOLDING_LABEL.ACTIVE },
  { status: "AWAITING_ACCEPTANCE", label: "Needs acceptance" },
  { status: "MATURED", label: "Completed" },
];

export default async function PortfolioPage({ searchParams }: PageProps<"/portfolio">) {
  const params = await searchParams;
  const accepted = params.accepted;
  const filter = FILTERS.find((f) => f.status !== null && f.status === params.status)?.status ?? null;
  const credit = creditLabel(await getRentCredit());
  const [portfolio, earnings, agreementList] = await Promise.all([
    authFetch<Portfolio>("/api/v1/portfolio"),
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<LeaseAgreement[]>("/api/v1/agreements"),
  ]);
  const agreements = new Map(agreementList.map((a) => [a.holdingId, a]));
  const leases = new Map(earnings.leases.map((l) => [l.holdingId, l]));
  const rent = earnings.wallet.filter((w) => w.balance !== "REFERRAL").map((w) => w.amount);
  const rentWallet = rent.length === 0 ? "—"
    : formatMoney({ amount: rent.reduce((sum, m) => sum + Number(m.amount), 0).toFixed(2), currency: CURRENCY });
  const invested = portfolio.totalsByCurrency.length === 0 ? "—" : portfolio.totalsByCurrency.map((m) => formatMoney(m)).join(" · ");
  const count = (status: HoldingStatus | null) => status === null ? portfolio.holdings.length
    : portfolio.holdings.filter((h) => h.status === status).length;
  const shown = filter === null ? portfolio.holdings : portfolio.holdings.filter((h) => h.status === filter);
  const acceptedAgreement = typeof accepted === "string" ? agreements.get(accepted) : undefined;

  const figures = [
    { icon: Briefcase, label: "Total invested", value: invested },
    { icon: Package, label: "Investments", value: String(investmentsHeld(portfolio.holdings)), note: holdingBreakdown(portfolio.holdings) || "None yet" },
    { icon: Wallet, label: "Returns wallet", value: rentWallet, note: nextCreditNote(earnings.nextCreditAt, credit) },
  ];

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="portfolio-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="yard-stacks" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_50%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="space-y-8 p-6 sm:p-8 lg:p-10">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="space-y-3">
              <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Portfolio</p>
              <h1 id="portfolio-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
                Your <span className="text-gold-on-ink">investments</span>
              </h1>
              <p className="max-w-lg text-sm leading-relaxed text-on-ink/75">Your investments, their terms and the returns they earn.</p>
            </div>
            <Link href="/invest"
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
              Invest more <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
          <dl className="grid gap-3 sm:grid-cols-3">
            {figures.map(({ icon: Icon, label, value, note }) => (
              <div key={label} className="flex flex-col-reverse justify-end gap-1 rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-4 backdrop-blur-md transition-colors hover:bg-on-ink/[0.12] sm:p-5">
                {note && <dd className="text-xs text-on-ink/60">{note}</dd>}
                <dd className="font-display text-2xl font-semibold tracking-tight tabular-nums">{value}</dd>
                <dt className="mb-1 flex items-center gap-2 text-sm text-on-ink/70">
                  <span aria-hidden="true" className="flex size-7 items-center justify-center rounded-lg bg-on-ink/10 text-gold-on-ink">
                    <Icon className="size-3.5" />
                  </span>
                  {label}
                </dt>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {acceptedAgreement?.acceptedAt && (
        <div role="status" className="flex items-start gap-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-900 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-200">
          <CheckCircle2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          <p className="text-sm leading-relaxed">
            Agreement {acceptedAgreement.agreementNumber} accepted. Your term starts at the next full hour, and returns are
            credited to your wallet {credit} from then on. You can download the agreement from the investment below.
          </p>
        </div>
      )}
      <AgreementPrompt holdings={portfolio.holdings} />

      {portfolio.holdings.length === 0 ? (
        <div className="space-y-4 rounded-3xl border border-border bg-surface p-8 text-center shadow-sm">
          <EmptyState title="No investments yet" description="Your investments appear here once your payment is confirmed." />
          <Link href="/invest"
            className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-gold px-5 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
            See the plans <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </div>
      ) : (
        <section aria-label="Investments" className="space-y-5">
          <nav aria-label="Filter investments" className="flex flex-wrap gap-2 text-sm">
            {FILTERS.filter((f) => f.status === null || count(f.status) > 0).map((f) => {
              const on = f.status === filter;
              return (
                <Link key={f.label} href={f.status ? `/portfolio?status=${f.status}` : "/portfolio"} aria-current={on ? "page" : undefined}
                  className={`inline-flex items-center gap-2 rounded-full border px-4 py-1.5 font-medium transition-colors duration-200 ${on
                    ? "border-ink bg-ink text-on-ink dark:border-gold dark:bg-gold dark:text-on-gold"
                    : "border-border bg-surface text-muted hover:border-gold/40 hover:text-foreground"}`}>
                  {f.label}
                  <span className={`rounded-full px-1.5 text-xs tabular-nums ${on ? "bg-on-ink/20" : "bg-background"}`}>{count(f.status)}</span>
                </Link>
              );
            })}
          </nav>
          {shown.length === 0 ? (
            <EmptyState title="No investments here" description="Choose another filter to see your other investments." />
          ) : (
            <ul className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
              {shown.map((h) => (
                <li key={h.id}><HoldingCard holding={h} lease={leases.get(h.id)} agreement={agreements.get(h.id)} /></li>
              ))}
            </ul>
          )}
        </section>
      )}

      <p className="text-xs leading-relaxed text-muted">
        Once your payment is confirmed, your investment agreement is issued. When you accept the agreement, the term starts at the next full hour and returns are credited to your wallet {credit} from then on. See{" "}
        <Link href="/earnings" className="underline underline-offset-2 hover:text-foreground">Earnings</Link> for each term&apos;s returns.
      </p>
    </div>
  );
}
