import { ArrowLeft, Building2, KeyRound, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { PortPhoto } from "@/components/brand/port-photo";

const POINTS = [
  { icon: Building2, text: "Orders, payments, KYC and payouts in one console" },
  { icon: KeyRound, text: "What you see follows the roles an administrator gave you" },
  { icon: ShieldCheck, text: "Every change you make is kept in the audit log" },
];

/** The staff platform's own sign-in screen: dark and plain, apart from the investor pages. */
export default function PlatformLayout({ children }: LayoutProps<"/platform/login">) {
  return (
    <div className="relative isolate flex min-h-dvh flex-1 items-center justify-center overflow-hidden bg-ink px-4 py-10 text-on-ink sm:px-6">
      <PortPhoto name="container-wall" alt="" eager sizes="100vw"
        className="absolute inset-0 -z-20 size-full object-cover opacity-25 saturate-0" />
      <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_20%_0%,color-mix(in_oklab,var(--gold)_35%,transparent),transparent_50%),linear-gradient(180deg,rgb(11_42_85/0.7),var(--ink))]" />
      <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(to_right,rgb(255_255_255/0.04)_1px,transparent_1px),linear-gradient(to_bottom,rgb(255_255_255/0.04)_1px,transparent_1px)] bg-[size:48px_48px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)]" />

      <div className="grid w-full max-w-5xl items-center gap-10 lg:grid-cols-[1fr_440px]">
        <section className="hidden space-y-8 lg:block">
          <Logo tone="light" />
          <div className="space-y-4">
            <p className="inline-flex items-center gap-2 rounded-full border border-on-ink/20 bg-on-ink/10 px-3.5 py-1.5 text-xs font-medium tracking-wide backdrop-blur-md">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-emerald-400" /> Staff platform
            </p>
            <h2 className="max-w-md font-display text-4xl leading-tight font-semibold">Run SeaLease from one place.</h2>
          </div>
          <ul className="max-w-md space-y-4">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-on-ink/80">
                <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl border border-on-ink/15 bg-on-ink/10 text-gold-on-ink">
                  <Icon className="size-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </section>

        <main className="w-full">
          <div className="mb-6 flex items-center justify-between lg:hidden">
            <Logo tone="light" />
          </div>
          <div className="rounded-3xl border border-on-ink/10 bg-surface p-6 text-foreground shadow-2xl shadow-black/40 sm:p-8">
            {children}
          </div>
          <Link href="/" className="mt-6 inline-flex items-center gap-1.5 text-sm text-on-ink/70 hover:text-on-ink">
            <ArrowLeft aria-hidden="true" className="size-4" /> Back to sealease home
          </Link>
        </main>
      </div>
    </div>
  );
}
