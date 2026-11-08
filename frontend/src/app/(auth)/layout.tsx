import Link from "next/link";
import { ArrowLeft, Clock, FileText, ShieldCheck, TrendingUp } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { PortPhoto } from "@/components/brand/port-photo";
import { Heading, IconTile, Pill, glass } from "@/features/landing/landing-ui";

const POINTS = [
  { icon: <TrendingUp className="size-[18px]" />, term: "Monthly returns", detail: "Set by your plan, before you invest" },
  { icon: <FileText className="size-[18px]" />, term: "Your own agreement", detail: "For every investment" },
  { icon: <ShieldCheck className="size-[18px]" />, term: "Encrypted details", detail: "KYC and bank data kept safe" },
];

/**
 * Sign-in, registration and the other account pages: one rounded card in the landing page's style. From `lg` the
 * left half is a container photo with the pitch over it; on smaller screens a short photo banner tops the form.
 */
export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex flex-1 bg-frame p-2 sm:p-5 lg:p-8">
      <div className="relative isolate mx-auto grid w-full max-w-[1440px] overflow-hidden rounded-[1.5rem] border border-glass-border bg-[linear-gradient(160deg,var(--sheen-top)_0%,var(--sheen-bottom)_100%)] p-2 shadow-2xl shadow-ink/15 sm:rounded-[2rem] sm:p-3 lg:grid-cols-[1.1fr_1fr]">
        {/* --------------------------------------------------------------- photo panel (lg+) */}
        <aside className="relative isolate hidden min-h-[40rem] flex-col justify-between overflow-hidden rounded-[1.25rem] bg-ink p-10 text-on-ink sm:rounded-[1.5rem] lg:flex xl:p-12">
          <PortPhoto name="container-wall" alt="" eager sizes="(min-width: 1440px) 760px, 55vw"
            className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8] motion-safe:animate-[auth-pan_30s_ease-in-out_infinite_alternate]" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(180deg,rgb(11_42_85/0.55)_0%,rgb(11_42_85/0.25)_35%,rgb(11_42_85/0.88)_62%,var(--ink)_100%)]" />

          <div className="flex items-center justify-between gap-4">
            <Logo tone="light" />
            <Link href="/"
              className="group inline-flex items-center gap-2 rounded-full border border-on-ink/20 bg-on-ink/10 px-4 py-2 text-sm font-medium text-on-ink backdrop-blur-md transition hover:bg-on-ink hover:text-ink">
              <ArrowLeft aria-hidden="true" className="size-4 transition-transform group-hover:-translate-x-0.5" />
              Back to home
            </Link>
          </div>

          {/* A floating chip, echoing the landing page's hero card. */}
          <div aria-hidden="true" className="absolute top-1/3 right-10 flex items-center gap-3 rounded-2xl border border-on-ink/15 bg-ink/60 px-4 py-3 shadow-xl shadow-ink/40 backdrop-blur-md motion-safe:animate-[auth-float_6s_ease-in-out_infinite] xl:right-12">
            <span className="flex size-9 items-center justify-center rounded-xl bg-on-ink/10 text-gold-on-ink">
              <Clock className="size-[18px]" />
            </span>
            <span>
              <span className="block text-[11px] text-on-ink/65">Returns credited</span>
              <span className="block font-display text-sm font-semibold">Straight to your wallet</span>
            </span>
          </div>

          <div className="space-y-7">
            <Pill tone="light">Investment Platform</Pill>
            <Heading as="p" tone="light" className="max-w-md text-4xl leading-[1.1] xl:text-[2.75rem]"
              lead="Real Assets. Real Returns." soft="Every Return On The Ledger." />
            <ul className="grid gap-3 border-t border-on-ink/15 pt-7 xl:grid-cols-3">
              {POINTS.map((point) => (
                <li key={point.term} className="flex items-center gap-3 xl:flex-col xl:items-start">
                  <IconTile tone="light">{point.icon}</IconTile>
                  <span className="text-sm leading-tight">
                    <span className="block font-semibold">{point.term}</span>
                    <span className="block text-xs text-on-ink/65">{point.detail}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </aside>

        {/* ---------------------------------------------------------------------- form side */}
        <main className="flex flex-col">
          {/* Photo banner below lg. */}
          <div className="relative isolate flex h-40 items-end justify-between overflow-hidden rounded-[1.25rem] bg-ink p-5 text-on-ink sm:h-48 sm:rounded-[1.5rem] sm:p-7 lg:hidden">
            <PortPhoto name="container-wall" alt="" eager sizes="100vw" className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink via-ink/50 to-ink/10" />
            <Logo tone="light" />
            <Link href="/" className="inline-flex items-center gap-1.5 rounded-full border border-on-ink/20 bg-on-ink/10 px-3 py-1.5 text-xs font-medium backdrop-blur-md transition hover:bg-on-ink hover:text-ink">
              <ArrowLeft aria-hidden="true" className="size-3.5" /> Home
            </Link>
          </div>

          <div className="flex flex-1 items-start justify-center px-3 py-8 sm:py-10 lg:items-center sm:px-10 lg:py-12">
            <div className={`w-full max-w-md rounded-[1.75rem] p-6 sm:p-9 ${glass} bg-glass-strong`}>
              {children}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
