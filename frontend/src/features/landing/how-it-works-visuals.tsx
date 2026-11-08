import { Check, Landmark, ShieldCheck, UserRound, Wallet } from "lucide-react";
import type { ReactNode } from "react";
import type { PublicPlan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";

/**
 * Small mock-ups of the screens behind each "How It Works" step, drawn on the ink panel at the top of each card.
 * They are illustrations only (aria-hidden): each step's text says the same thing in words.
 */

const EXAMPLE_INVESTMENT = 250000;
/** Used when no plan is open. */
const FALLBACK_PLANS = [
  { title: "3-year plan", tenureYears: 3, annualReturnPercent: 15 },
  { title: "5-year plan", tenureYears: 5, annualReturnPercent: 15 },
];

type PlanTerms = Pick<PublicPlan, "title" | "tenureYears" | "annualReturnPercent">;

const examplePlans = (plans: PlanTerms[]): [PlanTerms, ...PlanTerms[]] =>
  plans.length > 0 ? (plans as [PlanTerms, ...PlanTerms[]]) : (FALLBACK_PLANS as [PlanTerms, ...PlanTerms[]]);

const inr = (amount: number) => formatMoney({ amount: amount.toFixed(2), currency: "INR" });
const monthlyReturn = (plan: PlanTerms) => Math.round(EXAMPLE_INVESTMENT * plan.annualReturnPercent / 12) / 100;

function Panel({ children }: { children: ReactNode }) {
  return (
    <div aria-hidden="true"
      className="relative -mx-8 -mt-8 mb-8 flex aspect-[16/10] items-center justify-center overflow-hidden bg-ink px-6 text-on-ink">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,var(--gold)_0%,transparent_55%)] opacity-15" />
      <div className="relative w-full max-w-72 rounded-lg border border-on-ink/15 bg-on-ink/[0.06] p-4 shadow-lg backdrop-blur-sm">
        {children}
      </div>
    </div>
  );
}

function PanelTitle({ children, tag }: { children: ReactNode; tag?: string }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <p className="text-xs font-semibold">{children}</p>
      {tag && <span className="rounded-full border border-on-ink/20 px-2 py-0.5 text-[10px] text-on-ink/60">{tag}</span>}
    </div>
  );
}

/** Step 1: the three onboarding steps, two done and the bank account in progress. */
export function OnboardingVisual() {
  const items = [
    { icon: UserRound, label: "Personal details", done: true },
    { icon: ShieldCheck, label: "Aadhaar & PAN verified", done: true },
    { icon: Landmark, label: "Bank account for payouts", done: false },
  ];
  return (
    <Panel>
      <PanelTitle tag="2 of 3">Account setup</PanelTitle>
      <div className="mb-3 h-1 overflow-hidden rounded-full bg-on-ink/15">
        <div className="h-full w-2/3 rounded-full bg-gold-on-ink" />
      </div>
      <ul className="space-y-2">
        {items.map(({ icon: Icon, label, done }) => (
          <li key={label} className="flex items-center gap-2.5 text-xs">
            <Icon className="size-3.5 shrink-0 text-on-ink/60" />
            <span className={done ? "text-on-ink/80" : "text-on-ink"}>{label}</span>
            {done ? (
              <span className="ml-auto flex size-4 items-center justify-center rounded-full bg-gold-on-ink text-ink">
                <Check className="size-2.5" strokeWidth={3} />
              </span>
            ) : (
              <span className="ml-auto size-4 rounded-full border border-dashed border-on-ink/40" />
            )}
          </li>
        ))}
      </ul>
    </Panel>
  );
}

/** Step 2: picking a plan by tenure and seeing what an example amount returns. */
export function ChoosePlanVisual({ plans }: { plans: PlanTerms[] }) {
  const [selected, ...others] = examplePlans(plans);
  const shown = [selected, ...others.slice(0, 1)];
  return (
    <Panel>
      <PanelTitle>Choose a plan</PanelTitle>
      <ul className="space-y-1.5">
        {shown.map((plan) => {
          const active = plan === selected;
          return (
            <li key={plan.title}
              className={`flex items-center justify-between gap-3 rounded-md border px-2.5 py-1.5 text-xs ${
                active ? "border-gold-on-ink bg-gold-on-ink/10" : "border-on-ink/15"}`}>
              <span className="flex items-center gap-2">
                <span className={`size-2.5 rounded-full border ${active ? "border-gold-on-ink bg-gold-on-ink" : "border-on-ink/40"}`} />
                {plan.tenureYears}-year term
              </span>
              <span className="text-on-ink/70">{formatPercent(plan.annualReturnPercent)} a year</span>
            </li>
          );
        })}
      </ul>
      <div className="mt-3 flex items-end justify-between gap-3 border-t border-on-ink/15 pt-3 text-xs">
        <div>
          <p className="text-on-ink/60">You invest</p>
          <p className="font-semibold">{inr(EXAMPLE_INVESTMENT)}</p>
        </div>
        <div className="text-right">
          <p className="text-on-ink/60">Returns a month</p>
          <p className="font-semibold text-gold-on-ink">{inr(monthlyReturn(selected))}</p>
        </div>
      </div>
    </Panel>
  );
}

/** Step 3: monthly returns arriving in the wallet. */
export function EarnReturnsVisual({ plans }: { plans: PlanTerms[] }) {
  const monthly = monthlyReturn(examplePlans(plans)[0]);
  const months = [3, 2, 1];
  return (
    <Panel>
      <PanelTitle tag="Example">
        <span className="flex items-center gap-1.5"><Wallet className="size-3.5 text-gold-on-ink" /> Wallet</span>
      </PanelTitle>
      <p className="text-[10px] uppercase tracking-wider text-on-ink/60">Balance</p>
      <p className="font-display text-xl font-semibold">{inr(monthly * months.length)}</p>
      <ul className="mt-3 space-y-1.5 border-t border-on-ink/15 pt-3">
        {months.map((month) => (
          <li key={month} className="flex items-center justify-between gap-3 text-xs">
            <span className="text-on-ink/70">Returns · month {month}</span>
            <span className="font-medium text-gold-on-ink">+{inr(monthly)}</span>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
