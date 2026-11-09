import Link from "next/link";
import { connection } from "next/server";
import { ArrowRight } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { LandingHeader } from "@/components/layout/landing-header";
import { LandingMenu } from "@/components/layout/landing-menu";
import { LandingNavLinks } from "@/components/layout/landing-nav-links";
import { ContainerCategories } from "@/features/landing/containers";
import { Contact, Footer } from "@/features/landing/contact-footer";
import { DashboardPreview } from "@/features/landing/dashboard-preview";
import { Faq } from "@/features/landing/faq";
import { Hero } from "@/features/landing/hero";
import { HowItWorks } from "@/features/landing/how-it-works";
import { glass } from "@/features/landing/landing-ui";
import { GlobalNetwork } from "@/features/landing/network";
import { BusinessOverview } from "@/features/landing/overview";
import { planFacts } from "@/features/landing/plan-facts";
import { PlansSection } from "@/features/landing/plans-section";
import { WhySeaLease } from "@/features/landing/why-sealease";
import { getPhoneCountries } from "@/lib/server/phone-countries";
import { getPlatformStats } from "@/lib/server/platform-stats";
import { getPublicPlans } from "@/lib/server/public-plans";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";

const NAV = [
  { href: "#about", label: "About" },
  { href: "#how-it-works", label: "How It Works" },
  { href: "#plans", label: "Plans" },
  { href: "#network", label: "Network" },
  { href: "#faq", label: "FAQ" },
  { href: "#contact", label: "Contact" },
  { href: "/login", label: "Sign In" },
];

/**
 * The public website. Sections run from who we are, to how it works, to the plans and their risks, then trust and
 * contact. Each topic is covered in one section only. Plans, track-record figures and the credit interval come
 * from the backend on every request.
 */
export default async function Home() {
  // Live data, never a build-time snapshot.
  await connection();
  const [stats, phoneCountries, plans, rentCredit] = await Promise.all([getPlatformStats(), getPhoneCountries(),
    getPublicPlans(), getRentCredit()]);
  const credit = creditLabel(rentCredit);

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-8">
      {/* overflow-clip, not hidden: the sticky header needs no scroll container in between. */}
      <div className="relative isolate mx-auto max-w-[1440px] overflow-clip rounded-[1.5rem] border border-glass-border bg-[linear-gradient(180deg,var(--sheen-top)_0%,var(--sheen-bottom)_28%,var(--sheen-top)_55%,var(--sheen-bottom)_80%,var(--sheen-top)_100%)] shadow-2xl shadow-ink/15 sm:rounded-[2rem]">
        <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[900px] bg-[radial-gradient(ellipse_70%_60%_at_20%_0%,var(--glass-strong),transparent_70%)]" />
        <a href="#main" className="sr-only z-50 rounded-lg bg-ink px-4 py-2 text-on-ink focus:not-sr-only focus:absolute focus:top-3 focus:left-3">
          Skip to content
        </a>
        <LandingHeader>
          <Logo />
          <nav aria-label="Main" className="flex items-center gap-3 xl:flex-1 xl:justify-end xl:gap-6">
            <LandingNavLinks links={NAV.filter((item) => item.href.startsWith("#"))} />
            <Link href="/login"
              className="hidden whitespace-nowrap px-2 py-2 text-sm font-medium text-foreground underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-brass focus-visible:outline-none xl:inline-flex">
              Sign In
            </Link>
            <Link href="/register"
              className={`group hidden items-center gap-2 whitespace-nowrap rounded-xl px-5 py-2.5 text-sm font-semibold text-foreground transition duration-300 hover:border-ink hover:bg-ink hover:text-on-ink focus-visible:ring-2 focus-visible:ring-brass focus-visible:outline-none sm:inline-flex dark:hover:bg-on-ink dark:hover:text-ink ${glass}`}>
              Get Started
              <ArrowRight aria-hidden="true" className="-mr-1 size-4 transition-transform duration-300 group-hover:translate-x-0.5" />
            </Link>
            <LandingMenu links={NAV} />
          </nav>
        </LandingHeader>

        <main id="main">
          <Hero facts={planFacts(plans)} credit={credit} />
          <BusinessOverview stats={stats} />
          <HowItWorks credit={credit} />
          <ContainerCategories />
          <PlansSection plans={plans} credit={credit} />
          <DashboardPreview plans={plans} credit={credit} />
          <GlobalNetwork />
          <WhySeaLease />
          <Faq />
          <Contact countries={phoneCountries} />
        </main>
        <Footer />
      </div>
    </div>
  );
}
