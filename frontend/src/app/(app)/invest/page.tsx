import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { PlanCard } from "@/features/plans/plan-card";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PaymentMethods } from "@/types/order";
import type { Plan } from "@/types/plan";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Plans" };

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

  return (
    <div className="space-y-6">
      <PageHeader title="Investment plans" description="Choose a plan by its tenure and buy units of it. Once your payment is confirmed and you accept your investment agreement, you receive returns every month of the tenure." />
      {reinvesting && available && Number(available.amount) > 0 && (
        <Notice tone="info">
          You have {formatMoney(available)} to reinvest. Choose a plan and place an order of up to that amount; on the
          order page, pay it from your available balance with your password.
        </Notice>
      )}
      <nav aria-label="Filter plans" className="flex gap-2 text-sm">
        {[{ href: "/invest", label: "Open for investment", on: !closed }, { href: "/invest?status=CLOSED", label: "Closed", on: closed }].map((tab) => (
          <Link key={tab.href} href={tab.href} aria-current={tab.on ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${tab.on ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {tab.label}
          </Link>
        ))}
      </nav>
      {plans.content.length === 0 ? (
        <EmptyState title="No plans here right now" description="New plans are added regularly." />
      ) : (
        <ul className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          {plans.content.map((plan) => (
            <li key={plan.id} className="flex"><PlanCard plan={plan} href={`/invest/${plan.id}`} action="Invest" /></li>
          ))}
        </ul>
      )}
      <Pagination page={plans} basePath="/invest" params={{ status: closed ? "CLOSED" : undefined }} />
    </div>
  );
}
