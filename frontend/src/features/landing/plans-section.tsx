import { AlertTriangle, Calculator, CalendarCheck, Wallet } from "lucide-react";
import { yearsLabel } from "@/features/orders/lease-years";
import { PlanCarousel } from "@/features/plans/plan-carousel";
import { minimumAmount, planReturns } from "@/features/plans/returns";
import type { PublicPlan } from "@/types/plan";
import { CtaLink, Section, SectionHeader, glass } from "./landing-ui";

/**
 * The plans, and the single place their returns are explained. Every plan figure comes from the live plans; the
 * cards work out returns with the same rules as the backend (features/plans/returns.ts).
 */
export function PlansSection({ plans, credit }: { plans: PublicPlan[]; credit: string }) {
  // Plans whose returns over the whole term add up to less than the amount invested: the disclosures name one.
  const shortfall = plans
    .map((plan) => ({ plan, percent: planReturns(plan, minimumAmount(plan)).overTermPercent }))
    .filter((p) => p.percent < 100)
    .sort((a, b) => a.percent - b.percent)[0];

  const rules = [
    {
      icon: Calculator,
      title: "Monthly returns",
      body: "The amount you invest × the plan's annual return ÷ 12. The rate is fixed for your whole term when you invest.",
    },
    {
      icon: Wallet,
      title: "Credited as you earn",
      body: `Returns are credited to your SeaLease wallet ${credit} and recorded under Earnings. You withdraw them to your bank.`,
    },
    {
      icon: CalendarCheck,
      title: "Over the term",
      body: "Your total is the monthly returns × the months in the term. The amount you invest is not paid back at the end.",
    },
  ];

  return (
    <Section id="plans" labelledBy="plans-heading">
      <SectionHeader id="plans-heading" pill="Investment Plans" lead="Choose Your" soft="Term" split>
        Each plan has one term and one annual return. Buy as many units as you like, from the plan&apos;s minimum,
        and see exactly what you will receive before you pay.
      </SectionHeader>

      {plans.length > 0 ? (
        <div className="mt-10">
          <PlanCarousel plans={plans} href="/invest" action="Invest" />
        </div>
      ) : (
        <div className={`mt-10 flex flex-wrap items-center justify-between gap-4 rounded-3xl p-7 ${glass}`}>
          <p className="text-sm text-muted">No plan is open right now. Tell us what you are looking for and we will be in touch when one opens.</p>
          <CtaLink href="#contact" tone="secondary">Talk To Us</CtaLink>
        </div>
      )}

      <div className="mt-12 grid gap-5 lg:grid-cols-[1.4fr_1fr] lg:items-start">
        <div className={`rounded-3xl p-7 sm:p-8 ${glass}`}>
          <h3 className="text-lg font-semibold tracking-tight">How your returns are worked out</h3>
          <ul className="mt-6 grid gap-6 sm:grid-cols-3">
            {rules.map(({ icon: Icon, title, body }) => (
              <li key={title} className="space-y-2">
                <Icon aria-hidden="true" className="size-5 text-brass-text" />
                <p className="text-sm font-semibold">{title}</p>
                <p className="text-sm leading-relaxed text-muted">{body}</p>
              </li>
            ))}
          </ul>
        </div>

        <aside id="disclosures" aria-labelledby="disclosures-heading"
          className="scroll-mt-24 rounded-3xl border border-brass/50 bg-surface p-7 shadow-sm sm:p-8">
          <h3 id="disclosures-heading" className="flex items-center gap-2 text-lg font-semibold tracking-tight">
            <AlertTriangle aria-hidden="true" className="size-5 text-brass-text" /> Key facts before you invest
          </h3>
          <ul className="mt-5 space-y-3 text-sm leading-relaxed text-muted">
            <li><strong className="font-semibold text-foreground">Returns are not guaranteed.</strong> They are paid from SeaLease&apos;s leasing income, and past returns do not promise future ones. This is not a bank deposit.</li>
            <li>
              <strong className="font-semibold text-foreground">The amount you invest is not paid back.</strong> Your return is the monthly payments over the term; the investment is completed when the last one is credited.
              {shortfall && ` On a shorter term these can add up to less than you invest: on ${shortfall.plan.title} (${yearsLabel(shortfall.plan.tenureYears)}) they come to ${shortfall.percent}% of the amount.`}
            </li>
            <li><strong className="font-semibold text-foreground">Withdrawals</strong> can carry a charge and tax withheld at source (TDS). Both are shown before you confirm.</li>
            <li><strong className="font-semibold text-foreground">Read your investment agreement</strong> before you accept it: it sets out the terms of your investment.</li>
          </ul>
        </aside>
      </div>
    </Section>
  );
}
