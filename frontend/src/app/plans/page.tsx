import type { Metadata } from "next";
import { Eyebrow } from "@/components/brand/eyebrow";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { PlanCard } from "@/features/plans/plan-card";
import { PublicFrame } from "@/features/plans/public-frame";
import { openPlans } from "@/lib/server/public-plans";

export const metadata: Metadata = {
  title: "Plans",
  description: "Investment plans at SeaLease: buy units and receive returns every month of the tenure. Sign in to invest.",
};

/** The plans for visitors who are not signed in (signed in, /invest takes its place): the online plans, read-only. */
export default async function PublicPlansPage({ searchParams }: PageProps<"/plans">) {
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const plans = await openPlans(new URLSearchParams({ page: String(page), size: "12" })).catch(() => null);

  return (
    <PublicFrame next="/invest">
      <div className="space-y-10">
        <header className="space-y-4">
          <Eyebrow>Plans</Eyebrow>
          <h1 className="max-w-2xl text-4xl leading-tight font-bold tracking-tight sm:text-5xl">Investment plans</h1>
          <p className="max-w-2xl text-muted">
            Choose a plan by its tenure and buy units of it. Once your payment is confirmed and you accept your investment
            agreement, you receive returns every month of the tenure. Sign in or register to invest.
          </p>
        </header>
        {plans === null ? (
          <EmptyState title="The plans could not be loaded" description="Please try again in a moment." />
        ) : plans.content.length === 0 ? (
          <EmptyState title="No plans here right now" description="New plans are added regularly." />
        ) : (
          <ul className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
            {plans.content.map((plan) => (
              <li key={plan.code} className="flex">
                <PlanCard plan={plan} href="/login?next=%2Finvest" action="Sign in to invest" />
              </li>
            ))}
          </ul>
        )}
        {plans && <Pagination page={plans} basePath="/plans" params={{}} />}
      </div>
    </PublicFrame>
  );
}
