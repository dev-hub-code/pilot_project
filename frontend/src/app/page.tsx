import Link from "next/link";
import { connection } from "next/server";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { InterestForm } from "@/features/leads/interest-form";
import { getBackendStatus, type ServiceState } from "@/lib/server/system-status";

const stateTone: Record<ServiceState, StatusTone> = {
  UP: "success",
  DOWN: "warning",
  UNREACHABLE: "danger",
};

const NAV = [
  { href: "#about", label: "About" },
  { href: "#how-it-works", label: "How It Works" },
  { href: "#investors", label: "Investors" },
  { href: "#contact", label: "Contact" },
  { href: "/login", label: "Sign In" },
];

const STEPS = [
  {
    title: "Choose a plan",
    body: "Each plan shows the container type, the price per container, the monthly rent and the length of the lease up front.",
  },
  {
    title: "Buy your containers",
    body: "Buy one or more whole containers and pay by bank. Once your payment is confirmed, each container is assigned to you by its unique container number.",
  },
  {
    title: "Get paid every month",
    body: "Every month of the lease you receive rent plus an equal part of your investment back, so it is all returned by the end. Payouts are credited to an auditable ledger and withdrawable to your verified bank account.",
  },
];

export default async function Home() {
  // System status must reflect the live backend, never a build-time snapshot.
  await connection();
  const backend = await getBackendStatus();

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto max-w-[1440px] bg-surface">
        {/* ------------------------------------------------------------------- hero */}
        <section className="relative isolate flex min-h-[640px] flex-col overflow-hidden bg-ink text-on-ink lg:h-[min(88vh,860px)]">
          <ContainerScene className="absolute inset-0 -z-10 size-full" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink/90 via-ink/45 to-ink/30 lg:hidden" />

          <header className="flex items-center justify-between gap-6 px-5 py-6 sm:px-10 lg:px-14 lg:py-8">
            <Logo tone="light" />
            <nav aria-label="Main" className="flex items-center gap-8">
              <ul className="hidden items-center gap-8 text-sm md:flex">
                {NAV.map((item) => (
                  <li key={item.href}>
                    <Link href={item.href} className="text-on-ink/85 transition-colors hover:text-on-ink">
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
              <Link
                href="/register"
                className="whitespace-nowrap rounded-full border border-on-ink/80 px-4 py-2 text-sm transition-colors hover:bg-on-ink hover:text-ink sm:px-5 sm:py-2.5"
              >
                Start Investing
              </Link>
            </nav>
          </header>

          <div className="mt-auto grid gap-10 px-5 pb-10 sm:px-10 lg:grid-cols-2 lg:items-end lg:px-14 lg:pb-16">
            <h1 className="max-w-xl text-5xl leading-[1.02] font-bold tracking-tight sm:text-6xl lg:text-7xl">
              Invest In The Containers That Move The World
            </h1>
            <div className="max-w-lg space-y-7 lg:justify-self-end">
              <p className="text-base leading-relaxed text-on-ink/90">
                Buy Whole Shipping Containers, Each Assigned To You By Its Own Number, And Receive
                Rent Plus Part Of Your Capital Back Every Month Of The Lease.
              </p>
              <div className="flex flex-wrap gap-3">
                <LinkButton href="/register">Start Investing</LinkButton>
                <Link
                  href="/login"
                  className="inline-flex h-11 items-center px-2 text-sm text-on-ink/90 underline-offset-4 hover:underline"
                >
                  I already have an account
                </Link>
              </div>
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------------ about */}
        <section id="about" className="scroll-mt-8 px-5 py-20 text-center sm:px-10 lg:py-28">
          <Eyebrow>About SeaLease</Eyebrow>
          <h2 className="mx-auto mt-8 max-w-4xl text-3xl leading-tight font-normal tracking-tight sm:text-4xl lg:text-5xl">
            At SeaLease™, We Turn Shipping Containers Into Income-Producing Assets — Open To Every Investor,
            Accounted For To The Last Cent.
          </h2>
        </section>

        {/* ----------------------------------------------------------- how it works */}
        <section id="how-it-works" className="scroll-mt-8 border-t border-border px-5 py-20 sm:px-10 lg:px-14">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="space-y-5">
              <Eyebrow>How It Works</Eyebrow>
              <h2 className="max-w-xl text-3xl font-semibold tracking-tight sm:text-4xl">
                Three Steps From Sign-Up To Monthly Payouts
              </h2>
            </div>
            <LinkButton href="/register" variant="secondary">Create Your Account</LinkButton>
          </div>
          <ol className="mt-12 grid gap-px bg-border md:grid-cols-3">
            {STEPS.map((step, index) => (
              <li key={step.title} className="space-y-4 bg-surface p-8">
                <span className="font-display text-4xl font-semibold text-gold-text">0{index + 1}</span>
                <h3 className="text-xl font-semibold tracking-tight">{step.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* -------------------------------------------------------------- investors */}
        <section id="investors" className="grid scroll-mt-8 md:grid-cols-2">
          <div className="space-y-5 bg-background px-5 py-16 sm:px-10 lg:px-14">
            <Eyebrow>Your Own Containers</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">A Container With Your Name On It</h3>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Every container you buy is a specific, identified container: you see its ISO number, type and
              location in your portfolio as soon as your payment is confirmed.
            </p>
          </div>
          <div className="space-y-5 bg-ink px-5 py-16 text-on-ink sm:px-10 lg:px-14">
            <Eyebrow tone="light">Monthly Payouts</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Rent Plus Capital, Every Month</h3>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Each container is leased for the plan&apos;s tenure. Every month you receive the plan&apos;s rent plus
              100 ÷ tenure % of the price back, so your whole investment is returned by the end of the lease.
              Open to every verified investor.
            </p>
          </div>
        </section>

        {/* ---------------------------------------------------------------- contact */}
        <section id="contact" className="relative grid scroll-mt-8 gap-10 border-t border-border px-5 py-20 sm:px-10 lg:grid-cols-[1fr_1.4fr] lg:px-14">
          <div className="space-y-5">
            <Eyebrow>Talk To Us</Eyebrow>
            <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">Not sure where to start?</h2>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Tell us a little about what you are looking for and an investment specialist will get back to you,
              whether you are buying your first container or adding to your fleet.
            </p>
          </div>
          <InterestForm />
        </section>

        {/* ----------------------------------------------------------------- footer */}
        <footer className="flex flex-wrap items-center justify-between gap-6 border-t border-border px-5 py-8 sm:px-10 lg:px-14">
          <Logo />
          <p className="flex items-center gap-3 text-sm text-muted">
            Platform status <StatusBadge tone={stateTone[backend]}>{backend}</StatusBadge>
          </p>
        </footer>
      </div>
    </div>
  );
}
