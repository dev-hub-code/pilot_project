import { randomUUID } from "node:crypto";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { yearsLabel } from "@/features/orders/lease-years";
import { BuyUnitsForm } from "@/features/plans/buy-units-form";
import { PRODUCT_STATUS_LABEL } from "@/features/plans/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { PaymentMethods } from "@/types/order";
import type { PlanDetail, PlanQuote } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plan" };

/** One plan: the investor enters how many units to buy, sees what they cost and pay, and invests. */
export default async function PlanPage({ params }: PageProps<"/invest/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireSession();
  const investor = hasPermission(session.permissions, Permission.INVESTOR_PORTAL);

  let detail: PlanDetail;
  try {
    detail = await authFetch<PlanDetail>(`/api/v1/plans/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { plan, eligibility } = detail;
  const [quote, payment] = await Promise.all([
    authFetch<PlanQuote>(`/api/v1/plans/${id}/quote?units=${plan.minUnits}`),
    // Only to warn about the checkout's limit; the page works without it.
    investor ? authFetch<PaymentMethods>("/api/v1/payments/methods").catch(() => null) : Promise.resolve(null),
  ]);
  const open = plan.status === "OPEN";

  return (
    <div className="space-y-8">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/invest" className="inline-flex min-h-6 items-center hover:text-foreground">Plans</Link> / <span>{plan.title}</span>
      </nav>
      <header className="space-y-3">
        {!open && <StatusBadge tone="neutral">{PRODUCT_STATUS_LABEL[plan.status]}</StatusBadge>}
        <h1 className="text-3xl leading-tight font-bold tracking-tight sm:text-4xl">{plan.title}</h1>
        <p className="max-w-2xl text-muted">{plan.summary}</p>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1fr_1.1fr] [&>*]:min-w-0">
        <div className="space-y-8">
          <dl className="grid grid-cols-2 gap-px border border-border bg-border">
            <Fact label="Tenure" value={yearsLabel(plan.tenureYears)} />
            <Fact label="Returns a year" value={formatPercent(plan.annualReturnPercent)} emphasis />
            <Fact label="Price of 1 unit" value={formatMoney(plan.unitPrice)} />
            <Fact label="Minimum" value={plan.minUnits === 1 ? "1 unit" : `${plan.minUnits} units`} />
          </dl>
          <Card title="How it works">
            <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
              <li>Enter how many units to buy.</li>
              <li>Pay by bank within 30 minutes. Online transfer or cheque are accepted.</li>
              <li>Once your payment is confirmed, accept your investment agreement. Your term starts at the next full hour.</li>
              <li>Your annual return is paid monthly into your wallet for the whole tenure. The amount you invest is not paid back.</li>
              <li>SeaLease runs a leasing business and pays returns from that business.</li>
            </ol>
          </Card>
        </div>

        <section className="space-y-5 rounded-xl border border-border bg-surface p-6 lg:sticky lg:top-6 lg:self-start" aria-label="Buy units">
          <BuyUnitsForm productId={id} tenureYears={plan.tenureYears} initial={quote} idempotencyKey={randomUUID()}
            canBuy={investor && eligibility.eligible} checkoutLimit={payment?.checkoutLimit}
            payableOtherwise={(payment?.methods ?? []).some((m) => m !== "GATEWAY")} />
          {!investor ? (
            <Notice tone="info">Employee preview: this is what investors see. Investing needs an investor account.</Notice>
          ) : !eligibility.eligible && (
            open ? (
              <>
                <ul className="space-y-1 text-sm text-muted">
                  {eligibility.reasons.map((reason) => <li key={reason}>• {reason}</li>)}
                </ul>
                <LinkButton href={await onboardingHref()} className="w-full">Complete onboarding</LinkButton>
              </>
            ) : <Notice tone="info">This plan is not accepting investments.</Notice>
          )}
        </section>
      </div>
    </div>
  );
}

function Fact({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div className="bg-surface p-4">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className={`mt-1 ${emphasis ? "font-display text-2xl font-semibold text-gold-text" : "font-semibold"}`}>{value}</dd>
    </div>
  );
}
