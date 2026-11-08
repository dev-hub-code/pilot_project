import Link from "next/link";
import { connection } from "next/server";
import { ArrowRight, Building2, Clock, Layers, Play, ShieldCheck, TrendingUp } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { PortPhoto, type PortPhotoName } from "@/components/brand/port-photo";
import { LandingHeader } from "@/components/layout/landing-header";
import { LandingMenu } from "@/components/layout/landing-menu";
import { LandingNavLinks } from "@/components/layout/landing-nav-links";
import { HeroPortfolioCard } from "@/features/landing/hero-portfolio-card";
import { Heading, IconTile, Pill, glass, glassHover } from "@/features/landing/landing-ui";
import { ChoosePlanVisual, EarnReturnsVisual, OnboardingVisual } from "@/features/landing/how-it-works-visuals";
import { ReviewCarousel, type Review } from "@/features/landing/review-carousel";
import { InterestForm } from "@/features/leads/interest-form";
import { getPlatformStats, type PlatformStats } from "@/lib/server/platform-stats";
import { getPhoneCountries } from "@/lib/server/phone-countries";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import { getPublicPlans } from "@/lib/server/public-plans";
import { PlanCarousel } from "@/features/plans/plan-carousel";
import { yearsLabel } from "@/features/orders/lease-years";
import type { PublicPlan as Plan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";

const NAV = [
  { href: "#about", label: "About" },
  { href: "#how-it-works", label: "How It Works" },
  { href: "#investors", label: "Investors" },
  { href: "#faq", label: "FAQ" },
  { href: "#contact", label: "Contact" },
  { href: "/login", label: "Sign In" },
];

/** "every hour" (or whatever admins set), so the copy below follows the live credit interval. */
const BENEFITS = (credit: string) => [
  {
    title: "Returns from your first hour",
    body: `Your returns start at the next full hour after you accept your investment agreement and are credited to your SeaLease wallet ${credit}.`,
  },
  {
    title: "Longer plans can earn more",
    body: "Each plan has its own annual return, set by its tenure, and your returns are paid monthly. You see exactly what you will receive before you invest.",
  },
  {
    title: "Backed by a real business",
    body: "SeaLease runs a leasing business and pays returns from that income. Every investment has its own agreement you can download.",
  },
  {
    title: "Clear from day one",
    body: "Each plan shows your returns before you invest, every credit is on record under Earnings, and every withdrawal is listed on your Withdrawals page.",
  },
];

/**
 * Investor reviews, shown as a carousel. Only real reviews, published with the investor's consent, may go here. Entries
 * marked `sample` are layout placeholders, never shown in production. Every review must
 * match how the platform works today: plans by tenure and investor type, buying units of a plan, returns only (the amount
 * is not paid back), a fixed annual return for each plan, paid monthly, credited to the wallet as they accrue, onboarding
 * (details, KYC, bank account), the term starting when the agreement is accepted.
 */
const REVIEWS: Review[] = [
  {
    quote: "I can watch my wallet grow through the day, and the Earnings page shows how much each investment has earned. No chasing anyone.",
    name: "Priya S.",
    detail: "Pune",
    sample: true,
  },
  {
    quote: "I started small to see how it works. Once my payment was confirmed the investment showed up in my portfolio with its agreement. I have added more since.",
    name: "Rajesh K.",
    detail: "Ahmedabad",
    sample: true,
  },
  {
    quote: "Onboarding took me ten minutes: my details, Aadhaar and PAN with photos of the cards, then my bank account. Once both were verified I could buy units of a plan.",
    name: "Ananya R.",
    detail: "Bengaluru",
    sample: true,
  },
  {
    quote: "I paid by NEFT to the company account shown at checkout and entered the transaction ID. Finance confirmed it and the order moved to paid without me having to call.",
    name: "Mohammed I.",
    detail: "Hyderabad",
    sample: true,
  },
  {
    quote: "Being able to choose the term in whole years mattered to me. I took a longer term on one plan and a shorter one on another.",
    name: "Sunita D.",
    detail: "Nagpur",
    sample: true,
  },
  {
    quote: "I prefer cash, so I paid at the office with my payment reference and got a receipt on the spot. I chose to collect my returns at the office too, which suits me.",
    name: "Harish P.",
    detail: "Surat",
    sample: true,
  },
  {
    quote: "The plan page showed my returns before I paid anything. I chose a longer plan for its higher return, and my wallet matches it.",
    name: "Kavita N.",
    detail: "Mumbai",
    sample: true,
  },
  {
    quote: "I set nothing up: when the team makes the monthly withdrawal, my balance lands in my primary bank account. The Withdrawals page shows each one.",
    name: "Deepak V.",
    detail: "Indore",
    sample: true,
  },
  {
    quote: "My brother and two colleagues joined through my referral link. The commission comes on top of their returns, so their payouts are not reduced, which made it easy to recommend.",
    name: "Farhan A.",
    detail: "Lucknow",
    sample: true,
  },
  {
    quote: "My payment was confirmed in the middle of the month. I accepted the agreement from the notification and the returns started at the next hour. No waiting for a payout date.",
    name: "Meera J.",
    detail: "Kochi",
    sample: true,
  },
  {
    quote: "I asked support about adding a second bank account. The ticket was answered the same day, and finance verified the new account soon after.",
    name: "Gurpreet S.",
    detail: "Ludhiana",
    sample: true,
  },
  {
    quote: "My accountant needs everything on paper. I download the statement for the year and the invoice for each order, all in my name.",
    name: "Nikhil T.",
    detail: "Jaipur",
    sample: true,
  },
  {
    quote: "I liked seeing each plan with its term and the returns spelled out before I invested anything.",
    name: "Lakshmi V.",
    detail: "Chennai",
    sample: true,
  },
  {
    quote: "I got a notification when my KYC was approved, when my bank account was verified and when my agreement was ready. Tapping it took me straight there, so I always know where things stand.",
    name: "Arvind M.",
    detail: "Kolkata",
    sample: true,
  },
  {
    quote: "I wanted a monthly income without managing anything myself. Returns now come in every month, and the end date is right there in my portfolio.",
    name: "Rekha B.",
    detail: "Vadodara",
    sample: true,
  },
];

/** Every answer must match how the platform works today; plans and the credit interval are live. */
function faqs(plans: Plan[], credit: string) {
  const rates = plans.length > 0
    ? plans.map((p) => `${formatPercent(p.annualReturnPercent)} a year for ${yearsLabel(p.tenureYears)}`).join(", ")
    : null;
  const example = plans[0];
  const cheapest = cheapestPlan(plans);
  return [
    {
      question: "What exactly am I investing in?",
      answer: "You buy units of a SeaLease plan, under an investment agreement. Each plan has its own tenure, annual return and unit price. SeaLease runs a leasing business and pays investors from that business.",
    },
    {
      question: "How much will I receive in returns?",
      answer: `Every month of the tenure you receive returns: your plan's annual return on your investment, paid monthly${rates ? ` (currently ${rates})` : ""}. ${example
        ? `For example, at ${formatPercent(example.annualReturnPercent)}, ₹1,00,000 pays you ${formatMoney({ amount: String(1000 * example.annualReturnPercent), currency: "INR" })} a year, paid monthly. `
        : ""}Each plan's page shows your exact returns before you invest.`,
    },
    {
      question: "How much do I need to start?",
      answer: cheapest
        ? `The least you can start with is ${formatMoney({ amount: String(Number(cheapest.unitPrice.amount) * cheapest.minUnits), currency: cheapest.unitPrice.currency })}: ${cheapest.minUnits === 1 ? "1 unit" : `${cheapest.minUnits} units`} of ${cheapest.title}. Each plan sets its unit price and minimum units, and you can invest again whenever you like.`
        : "Each plan sets its unit price and minimum units. You can invest again whenever you like.",
    },
    {
      question: "What do I need before I can invest?",
      answer: "A free account and three onboarding steps: your personal details, identity verification (Aadhaar and PAN) and a bank account for payouts. Once our team has verified your identity and bank account, you can invest in any plan open to you.",
    },
    {
      question: "How do I pay?",
      answer: "By bank payment to one of our company bank accounts, online (NEFT, RTGS, IMPS or UPI) or by cheque, quoting your payment reference and entering the transaction ID or cheque number; or in cash at our office. Our finance team confirms the payment. Card payments are not accepted.",
    },
    {
      question: "When do my returns start, and how are they credited?",
      answer: `Once your payment is confirmed, you accept your investment agreement. The term starts at the next full hour, and your returns are credited to your SeaLease wallet ${credit}, every credit recorded under Earnings.`,
    },
    {
      question: "How is my money paid out?",
      answer: "Online investors are paid by bank transfer into their verified primary bank account, and can request a withdrawal at any time. Network investors choose bank or cash at our office, can change that at any time, and are paid when our team makes a withdrawal. Small balances wait for the next withdrawal.",
    },
    {
      question: "What happens at the end of the term?",
      answer: "When the last returns of the term are credited, your investment is marked as completed. The amount you invested is not paid back: your return is the monthly payments over the term. You can then invest in a new plan.",
    },
    {
      question: "Can I earn by referring others?",
      answer: "Yes. Share your referral link from the Referrals page. When someone you invite makes their first investment, you earn a one-time commission on it. SeaLease pays it, so their own returns are never reduced. The current rate is shown on your Referrals page.",
    },
    {
      question: "Is my personal and bank information safe?",
      answer: "Sensitive details such as identity documents, tax IDs and bank account numbers are stored encrypted, and only authorised employees can view them. Every view of a KYC document is logged.",
    },
  ];
}

/** The plan that costs least to start with. */
function cheapestPlan(plans: Plan[]): Plan | undefined {
  const start = (p: Plan) => Number(p.unitPrice.amount) * p.minUnits;
  return plans.reduce<Plan | undefined>((best, p) => (best === undefined || start(p) < start(best) ? p : best), undefined);
}

/** Example portfolio for the Investors section: one hypothetical investor in two plans, at the live plans' returns. */
const EXAMPLE_AMOUNTS = [250000, 400000];
/** Used when no plan is open. */
const EXAMPLE_FALLBACK_PLANS = [{ title: "3-year plan", tenureYears: 3, annualReturnPercent: 15 }, { title: "5-year plan", tenureYears: 5, annualReturnPercent: 15 }];

function examplePortfolio(plans: Pick<Plan, "title" | "tenureYears" | "annualReturnPercent">[]) {
  const chosen = plans.length >= 2 ? plans.slice(0, 2) : EXAMPLE_FALLBACK_PLANS;
  const holdings = chosen.map((p, i) => ({
    plan: p.title, price: EXAMPLE_AMOUNTS[i] ?? 0, years: p.tenureYears, returns: Math.round((EXAMPLE_AMOUNTS[i] ?? 0) * p.annualReturnPercent / 12) / 100,
  }));
  return { holdings, monthly: holdings.reduce((sum, h) => sum + h.returns, 0) };
}

const inr = (amount: number) => formatMoney({ amount: String(amount), currency: "INR" });

/**
 * The company's track record since 2022, shown in the "Since 2022" block. Enter the REAL, verifiable totals
 * (including business done before this platform); leave a field null to show the live platform figure instead.
 * Shown as "₹12 Cr+" for the amount and "3.5K+" for the counts.
 */
const COMPANY_RECORD: {
  paidToInvestorsCrore: number | null;
  payoutsMade: number | null;
  investors: number | null;
  investments: number | null;
} = {
  paidToInvestorsCrore: null, // e.g. 12 → "₹12 Cr+"
  payoutsMade: null, // e.g. 3500 → "3.5K+"
  investors: null, // e.g. 1200 → "1.2K+"
  investments: null, // e.g. 2000 → "2K+"
};

/**
 * Sample figures (not real) to preview the "Since 2022" row while developing. Never shown in production, and
 * labelled on the page. They apply only to fields left null in COMPANY_RECORD.
 */
const SAMPLE_RECORD = { paidToInvestorsCrore: 12, payoutsMade: 3500, investors: 1200, investments: 2000 };

/** Whether a figure left null above falls back to the live platform total. Off: the row shows only entered figures. */
const SHOW_LIVE_FIGURES = true;

/** 3500 → "3.5K+", 950 → "950+". */
function thousands(count: number): string {
  if (count < 1000) return `${count.toLocaleString("en-IN")}+`;
  return `${(Math.floor(count / 100) / 10).toLocaleString("en-IN")}K+`;
}

/** Each figure from COMPANY_RECORD when entered, otherwise live from the platform. Null when there is nothing to show. */
function trackRecord(stats: PlatformStats | null, useSamples: boolean) {
  const record = useSamples ? {
    paidToInvestorsCrore: COMPANY_RECORD.paidToInvestorsCrore ?? SAMPLE_RECORD.paidToInvestorsCrore,
    payoutsMade: COMPANY_RECORD.payoutsMade ?? SAMPLE_RECORD.payoutsMade,
    investors: COMPANY_RECORD.investors ?? SAMPLE_RECORD.investors,
    investments: COMPANY_RECORD.investments ?? SAMPLE_RECORD.investments,
  } : COMPANY_RECORD;
  const live = SHOW_LIVE_FIGURES && stats && stats.unitsEarning > 0 ? stats : null;
  const figures = {
    paid: record.paidToInvestorsCrore !== null ? `₹${record.paidToInvestorsCrore.toLocaleString("en-IN")} Cr+`
      : live && formatMoney(live.paidToInvestors, { compact: true }),
    // Rent is credited hourly now, so the platform has no count of payouts: only the company's own figure is shown.
    payouts: record.payoutsMade !== null ? thousands(record.payoutsMade) : null,
    investors: record.investors !== null ? thousands(record.investors) : live?.investors.toLocaleString("en-IN"),
    investments: record.investments !== null ? thousands(record.investments) : live?.units.toLocaleString("en-IN"),
  };
  return Object.values(figures).every(Boolean) ? (figures as Record<keyof typeof figures, string>) : null;
}

/**
 * The photo strip: a large lead photo, then four smaller ones, all at Indian ports. These photos are CC BY-SA / GODL-India
 * (Wikimedia Commons), credited on the /credits page: their licences require it.
 */
const PORT_GALLERY: { photo: PortPhotoName; alt: string; caption: string }[] = [
  {
    photo: "jnpt-cfs", caption: "Nhava Sheva (JNPT), Navi Mumbai",
    alt: "Containers stacked high under a gantry crane at a container freight station at JNPT, Navi Mumbai",
  },
  {
    photo: "chennai-cfs", caption: "Chennai",
    alt: "Rows of shipping containers stacked several high in a container yard in Chennai",
  },
  {
    photo: "kolkata-port", caption: "Kolkata Port",
    alt: "Aerial view of container stacks and a container ship at Netaji Subhas Dock, Kolkata Port",
  },
  {
    photo: "tuticorin-terminal", caption: "V.O. Chidambaranar Port, Tuticorin",
    alt: "Stacked containers on the quay beside a container ship under cranes at the Dakshin Bharat Gateway Terminal, Tuticorin",
  },
  {
    photo: "kochi-terminal", caption: "Vallarpadam terminal, Kochi",
    alt: "A container ship loaded with containers at the quay of the International Container Transshipment Terminal, Kochi",
  },
];

/**
 * Some of the world's largest container lines, shown for reference: SeaLease is not affiliated with them, so the
 * section's text must not present them as partners. Logos are the public-domain files on Wikimedia Commons, loaded
 * from Wikimedia's CDN (upload.wikimedia.org): each path is the file's place there.
 */
const COMMONS_FILES = "https://upload.wikimedia.org/wikipedia/commons";

/**
 * `small`: a solid or very bold logo, drawn a little smaller so it does not outweigh the others.
 * `crop`: the file has wide empty margins; the logo is cut to this aspect ratio around its centre.
 */
const SHIPPING_LINES: { name: string; logo: string; small?: boolean; crop?: string }[] = [
  { name: "MSC", logo: "b/b3/Mediterranean_Shipping_Company_logo.svg" },
  { name: "Maersk", logo: "e/e6/Maersk_Group_Logo.svg" },
  { name: "CMA CGM", logo: "d/d1/CMA_CGM_logo.svg" },
  { name: "COSCO Shipping", logo: "f/f6/COSCOCS-ver1.svg" },
  { name: "Hapag-Lloyd", logo: "6/6a/Hapag-Lloyd.svg" },
  { name: "Ocean Network Express", logo: "4/45/Ocean_Network_Express_logo.svg" },
  { name: "Evergreen Line", logo: "c/c9/Evergreen_Line_Logo.svg" },
  { name: "HMM", logo: "5/56/HMM_Logo_Basic_Form.svg", small: true },
  { name: "ZIM", logo: "6/69/ZIM_Logo.svg" },
  { name: "PIL", logo: "3/30/PIL_logo.png", small: true, crop: "aspect-[360/105]" },
  { name: "SITC", logo: "4/43/SITC_logo.svg", small: true },
  { name: "NYK Line", logo: "c/c6/Nippon_Yusen_text_logo.svg" },
  { name: "Mitsui O.S.K. Lines", logo: "2/22/Mitsui_O.S.K._Lines_logo.svg" },
  { name: "K Line", logo: "thumb/6/6d/Kawasaki_Kisen_Kaisha%2C_Ltd._logo.png/500px-Kawasaki_Kisen_Kaisha%2C_Ltd._logo.png" },
  { name: "Crowley", logo: "f/f6/Crowley_Maritime_logo.svg" },
];

const STEPS = (credit: string): { title: string; body: string; visual: "onboarding" | "plan" | "returns" }[] => [
  {
    title: "Complete onboarding",
    visual: "onboarding",
    body: "Create your free account, add your personal details, verify your identity with Aadhaar and PAN, and add the bank account your earnings are paid into.",
  },
  {
    title: "Choose a plan",
    visual: "plan",
    body: "Pick a plan by its tenure, enter how many units to buy to see your returns, then invest and pay by bank or in cash at our office.",
  },
  {
    title: "Earn returns",
    visual: "returns",
    body: `Once your payment is confirmed, accept your investment agreement and returns are credited to your wallet ${credit}, then paid out as your account allows.`,
  },
];

export default async function Home() {
  // Plans, figures and the credit interval must be live, never a build-time snapshot.
  await connection();
  const [stats, phoneCountries, plans, rentCredit] = await Promise.all([getPlatformStats(), getPhoneCountries(),
    getPublicPlans(), getRentCredit()]);
  const credit = creditLabel(rentCredit);
  const example = examplePortfolio(plans);
  const cheapest = cheapestPlan(plans);
  const lowest = cheapest?.unitPrice;
  const production = process.env.NODE_ENV === "production";
  const usesSampleFigures = !production && Object.values(COMPANY_RECORD).some((value) => value === null);
  const figures = trackRecord(stats, usesSampleFigures);
  const reviews = production ? REVIEWS.filter((review) => !review.sample) : REVIEWS;

  const invested = example.holdings.reduce((sum, h) => sum + h.price, 0);
  const top = plans.reduce<Plan | undefined>((best, p) => (!best || p.annualReturnPercent > best.annualReturnPercent ? p : best), undefined);
  const topPlan = top ? { title: top.title, rate: formatPercent(top.annualReturnPercent) } : null;
  const sectionX = "px-5 sm:px-10 lg:px-14";

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-8">
      {/* overflow-clip, not hidden: the sticky header needs no scroll container in between. */}
      <div className="relative isolate mx-auto max-w-[1440px] overflow-clip rounded-[1.5rem] border border-glass-border bg-[linear-gradient(180deg,var(--sheen-top)_0%,var(--sheen-bottom)_32%,var(--sheen-top)_62%,var(--sheen-bottom)_100%)] shadow-2xl shadow-ink/15 sm:rounded-[2rem]">
        <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[900px] bg-[radial-gradient(ellipse_70%_60%_at_20%_0%,var(--glass-strong),transparent_70%)]" />
        <LandingHeader>
          <Logo />
          <nav aria-label="Main" className="flex items-center gap-3 xl:flex-1 xl:justify-end xl:gap-6">
            <LandingNavLinks links={NAV.filter((item) => item.href.startsWith("#"))} />
            <Link href="/login"
              className="group relative hidden whitespace-nowrap px-2 py-2 text-sm font-medium text-foreground transition-colors hover:text-gold-text focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none xl:inline-flex">
              Sign In
              <span aria-hidden="true" className="absolute inset-x-2 bottom-1 h-px origin-left scale-x-0 bg-current transition-transform duration-300 group-hover:scale-x-100" />
            </Link>
            <Link href="/register"
              className={`group hidden items-center gap-2 whitespace-nowrap rounded-xl px-5 py-2.5 text-sm font-semibold text-foreground transition duration-300 hover:-translate-y-px hover:border-ink hover:bg-ink hover:text-on-ink hover:shadow-lg hover:shadow-ink/25 focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none sm:inline-flex dark:hover:border-gold dark:hover:bg-gold dark:hover:text-on-gold ${glass}`}>
              Get Started
              <ArrowRight aria-hidden="true" className="-mr-1 size-4 transition-transform duration-300 group-hover:translate-x-0.5" />
            </Link>
            <LandingMenu links={NAV} />
          </nav>
        </LandingHeader>

        {/* ------------------------------------------------------------------- hero */}
        <section className="relative isolate overflow-hidden pt-28 lg:pt-36">
          <div className={`grid items-center gap-10 lg:grid-cols-[1.3fr_1fr] ${sectionX}`}>
            <div className="space-y-7">
              <Pill>Monthly returns from a leasing business</Pill>
              <h1 className="max-w-2xl text-[2.6rem] leading-[1.05] font-semibold tracking-tight text-foreground min-[400px]:text-5xl sm:text-6xl lg:text-[3.3rem] xl:text-[3.9rem]">
                Invest In The <span className="text-heading-soft">Business</span><br className="hidden sm:block" />{" "}
                <span className="text-heading-soft">That</span> Moves The World
              </h1>
              <p className="max-w-lg text-base leading-relaxed text-muted sm:text-lg">
                Choose a plan, invest the amount you want, and earn monthly returns from the first hour of your term.
              </p>
              <div className="flex flex-wrap items-center gap-4">
                <Link href="/register"
                  className="group inline-flex min-h-12 items-center gap-2 rounded-xl bg-ink px-6 text-sm font-semibold text-on-ink shadow-lg shadow-ink/30 transition duration-300 hover:-translate-y-0.5 hover:bg-gold hover:shadow-xl hover:shadow-gold/30 focus-visible:ring-2 focus-visible:ring-gold focus-visible:ring-offset-2 focus-visible:outline-none dark:bg-gold dark:text-on-gold">
                  Start Investing <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
                </Link>
                <a href="#how-it-works" className="group inline-flex items-center gap-3 text-sm font-medium text-foreground">
                  <span className={`relative flex size-11 items-center justify-center rounded-full transition duration-300 group-hover:scale-110 group-hover:bg-ink group-hover:text-on-ink dark:group-hover:bg-gold dark:group-hover:text-on-gold ${glass}`}>
                    <span aria-hidden="true" className="absolute inset-0 rounded-full ring-2 ring-gold/40 motion-safe:animate-ping [animation-duration:2.4s]" />
                    <Play aria-hidden="true" className="ml-0.5 size-4 fill-current" />
                  </span>
                  <span className="underline-offset-4 group-hover:underline">See how it works</span>
                </a>
              </div>
              <ul className="flex flex-wrap gap-x-7 gap-y-4 pt-2">
                {[
                  { icon: <Layers className="size-[18px]" />, term: "Plans for you", detail: "Your amount, your term" },
                  { icon: <Clock className="size-[18px]" />, term: "Returns " + credit, detail: "From the first full hour" },
                  { icon: <ShieldCheck className="size-[18px]" />, term: "Your own agreement", detail: "For every investment" },
                ].map((point) => (
                  <li key={point.term} className="group flex items-center gap-3">
                    <IconTile>{point.icon}</IconTile>
                    <span className="text-sm leading-tight">
                      <span className="block font-semibold first-letter:uppercase">{point.term}</span>
                      <span className="block text-xs text-muted">{point.detail}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <HeroPortfolioCard invested={invested} holdings={example.holdings.length} monthly={example.monthly} topPlan={topPlan} money={inr} />
          </div>
          {/* The port at the foot of the hero, fading up into the sheen, with a quiet line of copy over it. */}
          <div className={`relative isolate mt-6 flex h-56 items-end justify-end pb-8 sm:h-72 lg:h-80 ${sectionX}`}>
            <div aria-hidden="true" className="absolute inset-x-0 bottom-0 -z-10 h-[calc(100%+5rem)] [mask-image:linear-gradient(to_bottom,transparent,black_45%)]">
              <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1440px) 1440px, 100vw"
                className="size-full object-cover object-[center_65%] saturate-[0.65]" />
              <div className="absolute inset-0 bg-gradient-to-t from-ink/80 via-ink/25 to-transparent" />
            </div>
            <p className="max-w-40 text-right text-[11px] leading-relaxed font-medium tracking-[0.18em] text-on-ink/85 uppercase">
              Every return on record, every month
            </p>
          </div>
        </section>

        {/* ------------------------------------------------------------------ about */}
        <section id="about" className={`scroll-mt-24 py-20 text-center lg:py-28 ${sectionX}`}>
          <Pill>About SeaLease</Pill>
          <Heading center className="mt-6 max-w-4xl text-3xl leading-tight sm:text-4xl lg:text-5xl"
            lead="Earn Monthly Returns From A Real Leasing Business." soft="Choose Your Tenure, Buy Units, Earn Every Month." />
          <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-muted">
            SeaLease™ runs a leasing business and pays investors returns from that income. We handle everything. You
            don&apos;t need any leasing experience, and
            you can start with just a few units of a plan{lowest ? `, from ${formatMoney(lowest)} a unit` : ""}.
          </p>
          <ul className="mt-14 grid gap-5 text-left sm:grid-cols-2 xl:grid-cols-4">
            {BENEFITS(credit).map((benefit, index) => (
              <li key={benefit.title} className={`group relative space-y-4 overflow-hidden rounded-3xl p-7 ${glass} ${glassHover}`}>
                <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 origin-left scale-x-0 bg-gradient-to-r from-gold to-gold-on-ink transition-transform duration-500 group-hover:scale-x-100" />
                <span aria-hidden="true" className="absolute top-6 right-7 font-display text-4xl font-semibold text-heading-soft/20 transition-colors duration-300 group-hover:text-gold/25">
                  0{index + 1}
                </span>
                <IconTile>{[<Clock key="c" className="size-5" />, <TrendingUp key="t" className="size-5" />,
                  <Building2 key="b" className="size-5" />, <ShieldCheck key="s" className="size-5" />][index % 4]}</IconTile>
                <h3 className="text-lg font-semibold tracking-tight">{benefit.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{benefit.body}</p>
              </li>
            ))}
          </ul>
          {/* Track record: a dark glass panel over a faint port yard, the story on the left and the figures on the right. */}
          <div className="relative isolate mx-auto mt-16 overflow-hidden rounded-[2rem] bg-ink text-left text-on-ink shadow-2xl shadow-ink/25">
            <PortPhoto name="yard-stacks" alt="" sizes="(min-width: 1440px) 1152px, 100vw"
              className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-r from-ink via-ink/90 to-ink/70" />
            <div className={`grid gap-10 px-6 py-12 sm:px-10 lg:items-center lg:gap-14 lg:px-14 lg:py-16 ${figures ? "lg:grid-cols-[1fr_1.1fr]" : ""}`}>
              <div className="space-y-5">
                <Pill tone="light">Since 2022</Pill>
                <Heading tone="light" as="h3" className="text-3xl leading-tight sm:text-4xl" lead="Trusted By Investors" soft="Around The World" />
                <p className="max-w-md text-sm leading-relaxed text-on-ink/75">
                  Since 2022, investors from around the world have invested with SeaLease, and we have been paying them their
                  returns ever since.
                  Every credit is recorded in our ledger, and investors can see each one in their account and are paid
                  out to their own bank.
                </p>
              </div>
              {figures && (
                <dl className="grid grid-cols-2 gap-3">
                  {[
                    { label: "Paid to investors", value: figures.paid },
                    { label: "Payouts made", value: figures.payouts },
                    { label: "Investors", value: figures.investors },
                    { label: "Investments made", value: figures.investments },
                  ].filter((figure) => figure.value).map((figure) => (
                    <div key={figure.label} className="flex flex-col-reverse justify-end gap-2 rounded-2xl border border-on-ink/10 bg-on-ink/[0.06] px-4 py-6 backdrop-blur-md transition duration-300 hover:-translate-y-0.5 hover:border-gold-on-ink/40 hover:bg-on-ink/[0.1] min-[400px]:px-5 sm:px-7 sm:py-8">
                      <dt className="text-xs leading-snug text-on-ink/70 sm:text-sm">{figure.label}</dt>
                      <dd className="font-display text-2xl font-semibold whitespace-nowrap text-gold-on-ink min-[400px]:text-3xl sm:text-4xl">{figure.value}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </div>
            <p className="border-t border-on-ink/10 px-6 py-4 text-xs leading-relaxed text-on-ink/55 sm:px-10 lg:px-14">
              Past payouts are not a guarantee of future returns. Every plan&apos;s returns and term are shown in
              full before you invest.
            </p>
          </div>
          {reviews.length > 0 && (
            <div className="mt-24">
              <Pill>What Our Investors Say</Pill>
              <Heading center className="mt-5 text-3xl leading-tight sm:text-4xl" lead="In Their" soft="Own Words" />
              <div className="mt-10">
                <ReviewCarousel reviews={reviews} />
              </div>
            </div>
          )}
        </section>

        {/* ---------------------------------------------------------------- ports */}
        <section aria-labelledby="ports-heading" className={`pb-20 lg:pb-28 ${sectionX}`}>
          <div className="grid gap-6 lg:grid-cols-2 lg:items-end lg:gap-14">
            <div className="space-y-5">
              <Pill>At The Ports</Pill>
              <Heading id="ports-heading" lead="Where The" soft="Containers Work" />
            </div>
            <p className="max-w-xl text-sm leading-relaxed text-muted lg:justify-self-end">
              Containers are stacked, loaded and moved through India&apos;s container terminals every day, carrying the
              goods of the businesses that lease them.
            </p>
          </div>
          <ul className="mt-12 grid grid-cols-2 gap-3 md:grid-cols-4 md:grid-rows-2 lg:gap-4">
            {PORT_GALLERY.map((item, index) => (
              <li key={item.photo}
                className={`group relative overflow-hidden rounded-3xl bg-ink shadow-lg shadow-ink/15 transition-shadow duration-500 hover:shadow-2xl hover:shadow-ink/30 ${index === 0
                  ? "col-span-2 aspect-[4/3] md:row-span-2 md:aspect-auto"
                  : "aspect-square md:aspect-[4/3]"}`}>
                <figure className="size-full">
                  <PortPhoto name={item.photo} alt={item.alt}
                    sizes={index === 0 ? "(min-width: 768px) 50vw, 100vw" : "(min-width: 768px) 25vw, 50vw"}
                    className="size-full object-cover transition-transform duration-700 ease-out group-hover:scale-110" />
                  <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-t from-ink/60 via-transparent to-transparent opacity-70 transition-opacity duration-500 group-hover:opacity-100" />
                  <figcaption className="absolute bottom-3 left-3 rounded-full border border-on-ink/20 bg-ink/45 px-3 py-1.5 text-xs text-on-ink backdrop-blur-md transition-transform duration-500 group-hover:-translate-y-1 sm:text-sm">
                    {item.caption}
                  </figcaption>
                </figure>
              </li>
            ))}
          </ul>
        </section>

        {/* --------------------------------------------------------- shipping lines */}
        <section aria-labelledby="lines-heading" className={`pb-20 lg:pb-28 ${sectionX}`}>
          <div className={`rounded-[2rem] p-6 sm:p-10 lg:p-14 ${glass}`}>
            <div className="grid gap-6 lg:grid-cols-2 lg:items-end lg:gap-14">
              <div className="space-y-5">
                <Pill>The Global Network</Pill>
                <Heading id="lines-heading" lead="The Lines That Move" soft="The World's Cargo" />
              </div>
              <p className="max-w-xl text-sm leading-relaxed text-muted lg:justify-self-end">
                Most of the world&apos;s container trade, including the ships calling at Nhava Sheva, Mundra, Chennai and
                Kochi, is carried by a handful of large lines. The businesses that lease containers ship their goods on
                services like theirs, which is what keeps containers in demand and earning.
              </p>
            </div>
            <ul className="mt-10 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {SHIPPING_LINES.map((line) => (
                // Always a white tile: the logos are drawn for a light background, in dark mode too.
                <li key={line.name} className="group flex h-24 items-center justify-center rounded-2xl bg-white px-5 shadow-sm ring-1 ring-transparent transition duration-300 hover:-translate-y-1 hover:shadow-lg hover:ring-gold/30 last:col-span-2 sm:h-28 sm:px-7 sm:last:col-span-1">
                  {/* eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP */}
                  <img src={`${COMMONS_FILES}/${line.logo}`} alt={line.name} title={line.name} loading="lazy" decoding="async"
                    className={`w-auto max-w-full object-contain opacity-60 grayscale transition duration-300 group-hover:opacity-100 group-hover:grayscale-0 ${line.crop ? `${line.crop} h-7 object-cover sm:h-9` : line.small ? "max-h-7 sm:max-h-9" : "max-h-10 sm:max-h-12"}`} />
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ----------------------------------------------------------- how it works */}
        <section id="how-it-works" className={`scroll-mt-24 pb-20 lg:pb-28 ${sectionX}`}>
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="space-y-5">
              <Pill>How It Works</Pill>
              <Heading className="max-w-xl text-3xl leading-tight sm:text-4xl lg:text-[2.75rem]" lead="Three Steps From Sign‑Up" soft="To Earning Returns" />
            </div>
            <Link href="/register"
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-ink px-5 text-sm font-semibold text-on-ink shadow-lg shadow-ink/25 transition duration-300 hover:-translate-y-0.5 hover:bg-gold hover:shadow-gold/30 dark:bg-gold dark:text-on-gold">
              Create Your Account <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
          <ol className="mt-12 grid gap-5 md:grid-cols-3">
            {STEPS(credit).map((step, index) => (
              <li key={step.title} className={`group space-y-3 overflow-hidden rounded-3xl p-8 ${glass} ${glassHover}`}>
                {step.visual === "onboarding" && <OnboardingVisual />}
                {step.visual === "plan" && <ChoosePlanVisual plans={plans} />}
                {step.visual === "returns" && <EarnReturnsVisual plans={plans} />}
                <span className="inline-flex rounded-full bg-ink px-3 py-1 font-display text-xs font-semibold tracking-wider text-on-ink transition-colors duration-300 group-hover:bg-gold group-hover:text-on-gold dark:bg-gold dark:text-on-gold">
                  STEP 0{index + 1}
                </span>
                <h3 className="text-xl font-semibold tracking-tight">{step.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* -------------------------------------------------------------- investors */}
        <section id="investors" className={`grid scroll-mt-24 gap-5 pb-20 md:grid-cols-2 lg:pb-28 ${sectionX}`}>
          <div className={`space-y-5 rounded-[2rem] p-7 transition-shadow duration-300 hover:shadow-[0_28px_60px_-28px_rgb(31_98_201/0.45)] sm:p-10 lg:p-12 ${glass}`}>
            <Pill>Your Investments</Pill>
            <Heading as="h3" className="text-2xl leading-tight sm:text-3xl" lead="Every Investment" soft="In One Place" />
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Every investment appears in your portfolio once your payment is confirmed, with its amount, term and
              investment agreement to accept and download.
            </p>
            <div className="max-w-md overflow-hidden rounded-2xl border border-glass-border bg-glass-strong shadow-sm">
              <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3">
                <p className="text-sm font-semibold">Example investor&apos;s portfolio</p>
                <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">Example</span>
              </div>
              <ul className="divide-y divide-border">
                {example.holdings.map((holding) => (
                  <li key={holding.plan} className="space-y-1 px-5 py-4 text-sm transition-colors hover:bg-glass">
                    <div className="flex items-center justify-between gap-4">
                      <p className="font-display font-semibold tracking-wide">{holding.plan}</p>
                      <span className="rounded-full bg-gold/15 px-2.5 py-0.5 text-xs text-gold-text">Running</span>
                    </div>
                    <p className="text-muted">Invested {inr(holding.price)} · {holding.years}-year term</p>
                  </li>
                ))}
              </ul>
            </div>
            <Link href="/plans" className={`group inline-flex min-h-11 items-center gap-2 rounded-xl px-5 text-sm font-semibold transition duration-300 hover:-translate-y-0.5 hover:border-ink hover:bg-ink hover:text-on-ink dark:hover:border-gold dark:hover:bg-gold dark:hover:text-on-gold ${glass}`}>
              View Plans <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
          <div className="relative isolate space-y-5 overflow-hidden rounded-[2rem] bg-ink p-7 text-on-ink shadow-2xl shadow-ink/25 sm:p-10 lg:p-12">
            <PortPhoto name="container-wall" alt="" sizes="(min-width: 768px) 50vw, 100vw"
              className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-br from-ink via-ink/85 to-ink/60" />
            <Pill tone="light">Your Returns</Pill>
            <Heading as="h3" tone="light" className="text-2xl leading-tight sm:text-3xl" lead="Returns Set" soft="By Your Plan" />
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Choose a plan by its tenure and buy units of it: each plan has its own annual return, paid monthly. Returns are credited to your wallet{" "}
              {credit} for the whole term, so you know from day one what each investment will earn.
            </p>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Open to every verified investor.
            </p>
            <div className="max-w-md overflow-hidden rounded-2xl border border-on-ink/15 bg-on-ink/[0.06] backdrop-blur-md">
              <div className="flex items-center justify-between gap-4 border-b border-on-ink/15 px-5 py-3">
                <p className="text-sm font-semibold">The same investor&apos;s returns</p>
                <span className="rounded-full border border-on-ink/30 px-3 py-1 text-xs text-on-ink/70">Example</span>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-on-ink/60">
                    <th scope="col" className="px-5 pt-3 pb-1 font-normal">Plan</th>
                    <th scope="col" className="px-5 pt-3 pb-1 text-right font-normal">Returns a month</th>
                  </tr>
                </thead>
                <tbody>
                  {example.holdings.map((holding) => (
                    <tr key={holding.plan} className="transition-colors hover:bg-on-ink/[0.05]">
                      <td className="px-5 py-2 text-on-ink/80">{holding.plan}</td>
                      <td className="px-5 py-2 text-right tabular-nums">{inr(holding.returns)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-on-ink/15">
                    <th scope="row" className="px-5 py-3 text-left font-semibold">Earned every month</th>
                    <td className="px-5 py-3 text-right font-display text-lg font-semibold text-gold-on-ink tabular-nums">{inr(example.monthly)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p className="max-w-md text-xs leading-relaxed text-on-ink/60">
              Illustration at each plan&apos;s annual return, paid monthly. Your actual returns are shown on the plan&apos;s page before
              you invest.
            </p>
            {/* Signed out, this shows the public plans; signed in, the investor's own. */}
            <Link href="/invest"
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
              Invest Now <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
        </section>

        {/* ------------------------------------------------------------------ plans */}
        {plans.length > 0 && (
          <section aria-labelledby="plans-heading" className={`pb-20 lg:pb-28 ${sectionX}`}>
            <div className="space-y-5">
              <Pill>Plans</Pill>
              <Heading id="plans-heading" lead="Choose Your" soft="Tenure" />
            </div>
            <div className="mt-10">
              <PlanCarousel plans={plans} href="/invest" action="Invest" />
            </div>
          </section>
        )}

        {/* -------------------------------------------------------------------- faq */}
        <section id="faq" className={`grid scroll-mt-24 gap-10 py-20 lg:grid-cols-[1fr_1.6fr] lg:gap-14 lg:py-28 ${sectionX}`}>
          <div className="space-y-5 lg:sticky lg:top-28 lg:self-start">
            <Pill>FAQ</Pill>
            <Heading lead="Questions" soft="Investors Ask Us" />
            <p className="max-w-sm text-sm leading-relaxed text-muted">
              Can&apos;t find your answer? <a href="#contact" className="font-medium text-gold-text underline-offset-4 hover:underline">Talk to us</a> and
              an investment specialist will get back to you.
            </p>
          </div>
          <div className="space-y-3">
            {faqs(plans, credit).map((faq) => (
              <details key={faq.question} className={`group rounded-2xl px-6 py-5 transition duration-300 hover:border-gold/30 hover:bg-glass-strong open:border-gold/30 open:bg-glass-strong open:shadow-[0_24px_50px_-30px_rgb(31_98_201/0.5)] ${glass}`}>
                <summary className="flex cursor-pointer list-none items-center justify-between gap-6 text-base font-semibold transition-colors group-hover:text-gold-text group-open:text-gold-text [&::-webkit-details-marker]:hidden">
                  {faq.question}
                  <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-ink text-lg leading-none text-on-ink transition duration-300 group-hover:scale-110 group-hover:bg-gold group-hover:text-on-gold group-open:rotate-45 group-open:bg-gold group-open:text-on-gold dark:bg-gold dark:text-on-gold">+</span>
                </summary>
                <p className="mt-3 pr-10 text-sm leading-relaxed text-muted">{faq.answer}</p>
              </details>
            ))}
          </div>
        </section>

        {/* ---------------------------------------------------------------- contact */}
        <section id="contact" className={`scroll-mt-24 pb-20 lg:pb-28 ${sectionX}`}>
          <div className={`grid gap-10 rounded-[2rem] p-6 sm:p-10 lg:grid-cols-[1fr_1.4fr] lg:p-14 ${glass}`}>
            <div className="space-y-5">
              <Pill>Talk To Us</Pill>
              <Heading lead="Not Sure" soft="Where To Start?" />
              <p className="max-w-md text-sm leading-relaxed text-muted">
                Tell us a little about what you are looking for and an investment specialist will get back to you,
                whether you are making your first investment or adding to it.
              </p>
            </div>
            <InterestForm countries={phoneCountries} />
          </div>
        </section>

        {/* ----------------------------------------------------------------- footer */}
        <footer className={`border-t border-glass-border py-6 ${sectionX}`}>
          <p className="text-xs text-muted">
            © {new Date().getFullYear()} Leasefleet Solutions Pvt. Ltd. All rights reserved.
          </p>
        </footer>
      </div>
    </div>
  );
}
