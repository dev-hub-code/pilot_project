import Link from "next/link";
import { connection } from "next/server";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
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
  { href: "/login", label: "Sign In" },
];

const STEPS = [
  {
    title: "Choose a container",
    body: "Browse vetted shipping containers on lease to logistics operators, with the price, expected rental yield and term shown up front.",
  },
  {
    title: "Invest your share",
    body: "Retail investors co-own a container from a small minimum. Ownership is recorded transaction by transaction, never just as a percentage.",
  },
  {
    title: "Earn rental income",
    body: "Rental income is distributed in proportion to ownership, credited to an auditable ledger and withdrawable to your verified bank account.",
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
                From Shared Retail Holdings To Standalone High-Net-Worth Assets, SeaLease Lets You Own Leased
                Shipping Containers And Earn Transparent, Ledger-Backed Rental Income Every Month.
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
                Three Steps From Sign-Up To Rental Income
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
            <Eyebrow>Retail Investors</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Co-Own A Container</h3>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Invest alongside others in a shared container. Your ownership is proportional to your
              contribution, and so is your share of every rental payment.
            </p>
          </div>
          <div className="space-y-5 bg-ink px-5 py-16 text-on-ink sm:px-10 lg:px-14">
            <Eyebrow tone="light">HNI Investors</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Own Containers Outright</h3>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Verified high-net-worth investors can hold standalone containers exclusively — 100% of the
              asset and 100% of its rental income.
            </p>
          </div>
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
