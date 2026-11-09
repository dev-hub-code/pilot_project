import { CalendarClock, Coins, Percent, Wallet } from "lucide-react";
import { LogoMark } from "@/components/brand/logo";
import { PortPhoto } from "@/components/brand/port-photo";
import { formatMoney } from "@/utils/money";
import { CtaLink, Pill, gutter } from "./landing-ui";
import type { PlanFacts } from "./plan-facts";

/** The opening screen: the promise, one main action, the key risk in plain words, and the live plan terms. */
export function Hero({ facts, credit }: { facts: PlanFacts; credit: string }) {
  return (
    <section aria-labelledby="hero-heading" className="relative isolate overflow-hidden pt-28 lg:pt-36">
      <div className={`grid items-center gap-12 lg:grid-cols-[1.25fr_1fr] ${gutter}`}>
        <div className="space-y-7">
          <Pill>Container leasing · Monthly returns</Pill>
          <h1 id="hero-heading"
            className="max-w-2xl text-[2.5rem] leading-[1.05] font-semibold tracking-tight text-foreground min-[400px]:text-5xl sm:text-6xl lg:text-[3.3rem] xl:text-[3.9rem]">
            Invest In The <span className="text-heading-soft">Business</span><br className="hidden sm:block" />{" "}
            <span className="text-heading-soft">That</span> Moves The World
          </h1>
          <p className="max-w-lg text-base leading-relaxed text-muted sm:text-lg">
            Buy units of a SeaLease plan and receive returns from our container leasing business every month of
            your term, under an agreement in your name.
          </p>
          <div className="flex flex-wrap items-center gap-x-6 gap-y-4">
            <CtaLink href="#plans">Explore Plans</CtaLink>
            <a href="#how-it-works"
              className="text-sm font-medium text-foreground underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-brass focus-visible:outline-none">
              How it works
            </a>
          </div>
          <p className="max-w-lg border-l-2 border-brass pl-4 text-xs leading-relaxed text-muted">
            Monthly returns from real container leasing income, with every term shown before you invest: your return
            is the monthly payments over the term.{" "}
            <a href="#disclosures" className="font-medium text-foreground underline underline-offset-2">How your investment works</a>
          </p>
        </div>
        <PlanSnapshot facts={facts} credit={credit} />
      </div>

      {/* A port at the foot of the hero, fading up into the page. */}
      <div aria-hidden="true" className="relative -z-10 -mt-10 h-48 sm:h-64 lg:-mt-16 lg:h-72">
        <div className="absolute inset-0 [mask-image:linear-gradient(to_bottom,transparent,black_55%)]">
          <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1440px) 1440px, 100vw"
            className="size-full object-cover object-[center_65%] saturate-[0.65]" />
          <div className="absolute inset-0 bg-gradient-to-t from-ink/70 via-ink/20 to-transparent" />
        </div>
      </div>
    </section>
  );
}

/** The hero's card: the plans open right now, straight from the backend. Nothing on it is an example. */
function PlanSnapshot({ facts, credit }: { facts: PlanFacts; credit: string }) {
  const rows = [
    { icon: Percent, label: "Annual return", value: facts.returns && `${facts.returns}`, note: "Fixed for your term, paid monthly" },
    { icon: CalendarClock, label: "Terms", value: facts.terms, note: "One term per plan" },
    { icon: Coins, label: "Start from", value: facts.cheapest && formatMoney(facts.cheapest.amount), note: facts.cheapest ? `Minimum for ${facts.cheapest.plan.title}` : undefined },
    { icon: Wallet, label: "Credited to your wallet", value: credit, note: "Every credit on record" },
  ];
  return (
    <figure aria-labelledby="snapshot-title" className="relative mx-auto w-full max-w-[27rem] [perspective:1600px] lg:mr-2">
      <div className="relative overflow-hidden rounded-[1.75rem] border border-on-ink/10 bg-[linear-gradient(145deg,var(--ink)_0%,var(--ink)_55%,color-mix(in_oklab,var(--ink)_70%,var(--brass))_100%)] p-6 text-on-ink shadow-[0_40px_80px_-30px_rgb(11_42_85/0.65)] transition-transform duration-700 sm:p-7 lg:[transform:rotateY(-12deg)_rotateX(6deg)] lg:hover:[transform:rotateY(-5deg)_rotateX(2deg)] motion-reduce:transform-none">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_85%_10%,rgb(255_255_255/0.16),transparent_45%)]" />
        <div className="relative flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 font-wordmark text-sm tracking-[0.2em]">
            <LogoMark className="h-6 w-7" /> SEALEASE
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-on-ink/25 px-2.5 py-0.5 text-[11px] text-on-ink/80">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-brass-on-ink motion-safe:animate-pulse" /> Live terms
          </span>
        </div>
        <figcaption id="snapshot-title" className="relative mt-7 text-xs tracking-wide text-on-ink/65 uppercase">
          {facts.count > 0 ? `${facts.count} ${facts.count === 1 ? "plan" : "plans"} open now` : "Plans"}
        </figcaption>
        {facts.count > 0 ? (
          <dl className="relative mt-4 divide-y divide-on-ink/10">
            {rows.filter((row) => row.value).map(({ icon: Icon, label, value, note }) => (
              <div key={label} className="flex items-start gap-3 py-3.5">
                <span aria-hidden="true" className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl border border-on-ink/15 bg-on-ink/10 text-brass-on-ink">
                  <Icon className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <dt className="text-xs text-on-ink/65">{label}</dt>
                  <dd className="font-display text-lg font-semibold first-letter:uppercase">{value}</dd>
                  {note && <dd className="text-[11px] text-on-ink/55">{note}</dd>}
                </div>
              </div>
            ))}
          </dl>
        ) : (
          <p className="relative mt-4 text-sm leading-relaxed text-on-ink/80">
            No plan is open right now. Leave your details below and we will tell you when the next one opens.
          </p>
        )}
      </div>
    </figure>
  );
}
