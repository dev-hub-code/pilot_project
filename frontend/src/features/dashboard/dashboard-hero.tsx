import Link from "next/link";
import { ArrowRight, ShieldAlert, Wallet } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";

export interface HeroAction {
  href: string;
  label: string;
  /** The main action: a solid light button. Others are glass. */
  primary?: boolean;
}

/**
 * The dashboard's opening banner: a greeting over a container photo, with the available balance and the main actions.
 * While the investor still has to be verified, the balance gives way to that next step.
 */
export function DashboardHero({ firstName, balance, balanceNote, actions, setup }: {
  firstName: string;
  balance: string | null;
  balanceNote?: string;
  actions: readonly HeroAction[];
  setup: { href: string; label: string; text: string } | null;
}) {
  const today = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long", timeZone: "Asia/Kolkata" });
  return (
    <section aria-label="Overview" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
      <PortPhoto name="container-wall" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
        className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
      <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_45%,rgb(11_42_85/0.55)_100%)]" />
      <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />

      <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.2fr_1fr] lg:items-end lg:p-10">
        <div className="space-y-3">
          <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">{today}</p>
          <h1 className="text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
            Welcome back, <span className="text-gold-on-ink">{firstName}</span>
          </h1>
          <p className="max-w-md text-sm leading-relaxed text-on-ink/75">Your investments and returns at a glance.</p>
          {actions.length > 0 && (
            <div className="flex flex-wrap gap-3 pt-3">
              {actions.map((action) => (
                <Link key={action.href} href={action.href}
                  className={`group inline-flex min-h-11 items-center gap-2 rounded-xl px-5 text-sm font-semibold transition duration-300 hover:-translate-y-0.5 focus-visible:ring-2 focus-visible:ring-gold-on-ink focus-visible:outline-none ${action.primary
                    ? "bg-on-ink text-ink shadow-lg hover:bg-gold-on-ink"
                    : "border border-on-ink/20 bg-on-ink/10 text-on-ink backdrop-blur-md hover:bg-on-ink/20"}`}>
                  {action.label}
                  {action.primary && <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />}
                </Link>
              ))}
            </div>
          )}
        </div>

        {setup ? (
          <div className="space-y-4 rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-5 backdrop-blur-md sm:p-6">
            <p className="flex items-start gap-3 text-sm leading-relaxed">
              <ShieldAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-gold-on-ink" />
              {setup.text}
            </p>
            <Link href={setup.href}
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
              {setup.label} <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
        ) : balance !== null && (
          <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-5 backdrop-blur-md sm:p-6 lg:justify-self-end lg:min-w-80">
            <p className="flex items-center gap-2 text-sm text-on-ink/70">
              <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-on-ink/10 text-gold-on-ink">
                <Wallet className="size-4" />
              </span>
              Available balance
            </p>
            <p className="mt-3 font-display text-4xl font-semibold tracking-tight tabular-nums sm:text-5xl">{balance}</p>
            {balanceNote && (
              <p className="mt-2 flex items-center gap-2 text-xs text-on-ink/65">
                <span aria-hidden="true" className="relative flex size-2">
                  <span className="absolute inline-flex size-full rounded-full bg-gold-on-ink opacity-70 motion-safe:animate-ping" />
                  <span className="relative inline-flex size-2 rounded-full bg-gold-on-ink" />
                </span>
                {balanceNote}
              </p>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
