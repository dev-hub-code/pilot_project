import { CirclePlus, FileText, TrendingUp, Wallet } from "lucide-react";
import { LogoMark } from "@/components/brand/logo";

/**
 * The hero's floating card: an example investor's portfolio in SeaLease, tilted in 3D, with two chips floating off
 * it. Figures come from the live plans (see examplePortfolio on the home page) and are always labelled "Example".
 */
export function HeroPortfolioCard({ invested, holdings, monthly, topPlan, money }: {
  invested: number;
  holdings: number;
  monthly: number;
  topPlan: { title: string; rate: string } | null;
  money: (amount: number) => string;
}) {
  const actions = [
    { icon: CirclePlus, label: "Invest" },
    { icon: Wallet, label: "Wallet" },
    { icon: TrendingUp, label: "Earnings" },
    { icon: FileText, label: "Agreements" },
  ];
  return (
    <figure aria-label="Example investor's portfolio" className="relative mx-auto w-full max-w-[26rem] pt-4 pb-16 [perspective:1600px] lg:mr-4">
      <div className="relative rounded-[1.75rem] border border-on-ink/10 bg-[linear-gradient(140deg,var(--ink)_0%,var(--ink)_45%,color-mix(in_oklab,var(--ink)_60%,var(--gold))_100%)] p-6 text-on-ink shadow-[0_40px_80px_-30px_rgb(11_42_85/0.65)] transition-transform duration-700 sm:p-7 lg:[transform:rotateY(-16deg)_rotateX(8deg)_rotateZ(1deg)] lg:hover:[transform:rotateY(-8deg)_rotateX(4deg)]">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-[1.75rem] bg-[radial-gradient(circle_at_85%_10%,rgb(255_255_255/0.18),transparent_45%)]" />
        <div className="relative flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 font-wordmark text-sm tracking-[0.2em]">
            <LogoMark className="h-6 w-7" /> SEALEASE
          </span>
          <span className="rounded-full border border-on-ink/25 px-2.5 py-0.5 text-[11px] text-on-ink/75">Example</span>
        </div>
        <p className="relative mt-8 text-xs text-on-ink/65">Total invested</p>
        <p className="relative mt-1 font-display text-4xl font-semibold tracking-tight tabular-nums sm:text-[2.6rem]">{money(invested)}</p>
        <p className="relative mt-1 text-sm text-gold-on-ink">In {holdings} plans · returns paid every month</p>
        <ul aria-hidden="true" className="relative mt-8 grid grid-cols-4 gap-2 text-center text-[11px] text-on-ink/80">
          {actions.map(({ icon: Icon, label }) => (
            <li key={label} className="flex flex-col items-center gap-2">
              <span className="flex size-11 items-center justify-center rounded-2xl border border-on-ink/15 bg-on-ink/10 shadow-inner backdrop-blur-md">
                <Icon className="size-[18px]" />
              </span>
              {label}
            </li>
          ))}
        </ul>
      </div>

      {/* Floating chips */}
      <div className="absolute top-0 -right-2 rounded-2xl border border-on-ink/10 bg-ink/90 px-4 py-3 text-on-ink shadow-xl shadow-ink/30 backdrop-blur-md sm:-right-8">
        <p className="flex items-center gap-1.5 text-[11px] text-on-ink/65"><TrendingUp aria-hidden="true" className="size-3.5 text-gold-on-ink" /> Returns credited</p>
        <p className="mt-0.5 font-display text-lg font-semibold tabular-nums">+{money(monthly)}</p>
        <p className="text-[11px] text-on-ink/55">This month</p>
      </div>
      {topPlan && (
        <div className="absolute right-2 bottom-2 flex items-center gap-3 rounded-2xl border border-on-ink/10 bg-ink/90 px-4 py-3 text-on-ink shadow-xl shadow-ink/30 backdrop-blur-md sm:-right-4">
          <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-on-ink/10 font-display text-base font-semibold text-gold-on-ink">
            {topPlan.title.charAt(0)}
          </span>
          <span>
            <span className="block text-[11px] text-on-ink/65">{topPlan.title}</span>
            <span className="block font-display text-base font-semibold">{topPlan.rate} a year</span>
          </span>
        </div>
      )}
    </figure>
  );
}
