import Link from "next/link";
import { connection } from "next/server";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { PortPhoto, type PortPhotoName } from "@/components/brand/port-photo";
import { LandingHeader } from "@/components/layout/landing-header";
import { LandingMenu } from "@/components/layout/landing-menu";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
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
import { getBackendStatus, type ServiceState } from "@/lib/server/system-status";
import { formatMoney, formatPercent } from "@/utils/money";

const stateTone: Record<ServiceState, StatusTone> = {
  UP: "success",
  DOWN: "warning",
  UNREACHABLE: "danger",
};

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
  // System status must reflect the live backend, never a build-time snapshot.
  await connection();
  const [backend, stats, phoneCountries, plans, rentCredit] = await Promise.all([getBackendStatus(), getPlatformStats(),
    getPhoneCountries(), getPublicPlans(), getRentCredit()]);
  const credit = creditLabel(rentCredit);
  const example = examplePortfolio(plans);
  const cheapest = cheapestPlan(plans);
  const lowest = cheapest?.unitPrice;
  const production = process.env.NODE_ENV === "production";
  const usesSampleFigures = !production && Object.values(COMPANY_RECORD).some((value) => value === null);
  const figures = trackRecord(stats, usesSampleFigures);
  const reviews = production ? REVIEWS.filter((review) => !review.sample) : REVIEWS;

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto max-w-[1440px] bg-surface">
        <LandingHeader>
          <Logo tone="light" />
          <nav aria-label="Main" className="flex items-center gap-3 xl:gap-4">
            {/* In-page sections in a frosted pill; signing in and investing sit apart from it. */}
            <ul className="hidden items-center gap-0.5 rounded-full border border-on-ink/15 bg-ink/35 p-1.5 text-sm shadow-lg shadow-ink/20 backdrop-blur-md xl:flex">
              {NAV.filter((item) => item.href.startsWith("#")).map((item) => (
                <li key={item.href}>
                  {/* In-page sections use plain anchors: <Link> skips the scroll when the URL already has that hash. */}
                  <a href={item.href}
                    className="block whitespace-nowrap rounded-full px-4 py-2 text-on-ink/80 transition-colors hover:bg-on-ink/10 hover:text-on-ink focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none 2xl:px-5">
                    {item.label}
                  </a>
                </li>
              ))}
            </ul>
            <Link href="/login"
              className="hidden whitespace-nowrap rounded-full border border-on-ink/40 bg-ink/35 px-5 py-2.5 text-sm font-medium text-on-ink shadow-lg shadow-ink/20 backdrop-blur-md transition-colors hover:border-on-ink hover:bg-on-ink hover:text-ink focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none xl:inline-flex">
              Sign In
            </Link>
            <Link
              href="/register"
              className="hidden items-center gap-2 whitespace-nowrap rounded-full bg-gold px-5 py-2.5 text-sm font-medium text-on-gold shadow-lg shadow-ink/25 transition-colors hover:bg-gold-hover focus-visible:ring-2 focus-visible:ring-on-ink focus-visible:outline-none sm:inline-flex"
            >
              Register
              <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </Link>
            <LandingMenu links={NAV} />
          </nav>
        </LandingHeader>

        {/* ------------------------------------------------------------------- hero */}
        <section className="relative isolate flex min-h-[640px] flex-col overflow-hidden bg-ink pt-24 text-on-ink lg:h-[min(88vh,860px)] lg:pt-28">
          <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1440px) 1440px, 100vw"
            className="absolute inset-0 -z-20 size-full object-cover" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink/95 via-ink/55 to-ink/40" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 hidden bg-gradient-to-r from-ink/60 to-transparent lg:block" />


          <div className="mt-auto grid gap-10 px-5 pb-10 sm:px-10 lg:grid-cols-2 lg:items-end lg:px-14 lg:pb-16">
            <h1 className="max-w-xl text-4xl leading-[1.04] font-bold tracking-tight min-[400px]:text-5xl sm:text-6xl xl:text-7xl">
              Invest In The Business That Moves The World
            </h1>
            <div className="max-w-lg space-y-7 lg:justify-self-end">
              <p className="text-base leading-relaxed text-on-ink/90">
                Choose A Plan, Invest The Amount You Want, And Earn Monthly Returns From The First
                Hour Of Your Term.
              </p>
              <dl className="grid gap-3 border-t border-on-ink/20 pt-6 sm:grid-cols-3 sm:gap-6">
                {[
                  { term: "Plans for you", detail: "Choose your amount and your term" },
                  { term: "Returns " + credit, detail: "Credited to your wallet from the first full hour" },
                  { term: "Your own agreement", detail: "An agreement for every investment you make" },
                ].map((point) => (
                  <div key={point.term} className="flex items-baseline gap-3 sm:block sm:space-y-1.5">
                    <dt className="w-36 shrink-0 text-sm font-semibold text-gold-on-ink first-letter:uppercase sm:w-auto">{point.term}</dt>
                    <dd className="text-xs leading-relaxed text-on-ink/75">{point.detail}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------------ about */}
        <section id="about" className="scroll-mt-24 px-5 py-20 text-center sm:px-10 lg:py-28">
          <Eyebrow>About SeaLease</Eyebrow>
          <h2 className="mx-auto mt-8 max-w-4xl text-3xl leading-tight font-normal tracking-tight sm:text-4xl lg:text-5xl">
            Earn Monthly Returns From A Real Leasing Business. Choose Your Tenure, Buy Units, Earn Every Month.
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-muted">
            SeaLease™ runs a leasing business and pays investors returns from that income. We handle everything. You
            don&apos;t need any leasing experience, and
            you can start with just a few units of a plan{lowest ? `, from ${formatMoney(lowest)} a unit` : ""}.
          </p>
          <ul className="mt-14 grid gap-px overflow-hidden rounded-xl border border-border bg-border text-left sm:grid-cols-2 xl:grid-cols-4">
            {BENEFITS(credit).map((benefit) => (
              <li key={benefit.title} className="space-y-3 bg-surface p-8">
                <h3 className="text-lg font-semibold tracking-tight">{benefit.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{benefit.body}</p>
              </li>
            ))}
          </ul>
          {/* Track record: a dark panel over a faint port yard, the story on the left and the figures on the right. */}
          <div className="relative isolate mx-auto mt-16 max-w-6xl overflow-hidden bg-ink text-left text-on-ink">
            <PortPhoto name="yard-stacks" alt="" sizes="(min-width: 1440px) 1152px, 100vw"
              className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-r from-ink via-ink/90 to-ink/75" />
            <div className={`grid gap-10 px-6 py-12 sm:px-10 lg:items-center lg:gap-14 lg:px-14 lg:py-16 ${figures ? "lg:grid-cols-[1fr_1.1fr]" : ""}`}>
              <div className="space-y-5">
                <p className="inline-flex items-center gap-2 rounded-full border border-gold/40 bg-gold/10 px-3 py-1 text-xs font-medium uppercase tracking-[0.12em] text-gold-on-ink">
                  <span aria-hidden="true" className="size-1.5 rounded-full bg-gold" />
                  Since 2022
                </p>
                <h3 className="text-3xl leading-tight font-semibold tracking-tight sm:text-4xl">Trusted By Investors Around The World</h3>
                <p className="max-w-md text-sm leading-relaxed text-on-ink/75">
                  Since 2022, investors from around the world have invested with SeaLease, and we have been paying them their
                  returns ever since.
                  Every credit is recorded in our ledger, and investors can see each one in their account and are paid
                  out to their own bank.
                </p>
              </div>
              {figures && (
                <dl className="grid grid-cols-2 gap-px border border-on-ink/15 bg-on-ink/15">
                  {[
                    { label: "Paid to investors", value: figures.paid },
                    { label: "Payouts made", value: figures.payouts },
                    { label: "Investors", value: figures.investors },
                    { label: "Investments made", value: figures.investments },
                  ].filter((figure) => figure.value).map((figure) => (
                    <div key={figure.label} className="flex flex-col-reverse justify-end gap-2 bg-ink/85 px-4 py-6 backdrop-blur-sm min-[400px]:px-5 sm:px-7 sm:py-8">
                      <dt className="text-xs leading-snug text-on-ink/70 sm:text-sm">{figure.label}</dt>
                      <dd className="font-display text-2xl font-semibold whitespace-nowrap text-gold-on-ink min-[400px]:text-3xl sm:text-4xl">{figure.value}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </div>
            <p className="border-t border-on-ink/15 px-6 py-4 text-xs leading-relaxed text-on-ink/55 sm:px-10 lg:px-14">
              Past payouts are not a guarantee of future returns. Every plan&apos;s returns and term are shown in
              full before you invest.
            </p>
          </div>
          {reviews.length > 0 && (
            <div className="mt-20">
              <Eyebrow>What Our Investors Say</Eyebrow>
              <div className="mt-10">
                <ReviewCarousel reviews={reviews} />
              </div>
            </div>
          )}
        </section>

        {/* ---------------------------------------------------------------- ports */}
        <section aria-labelledby="ports-heading" className="border-t border-border px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="space-y-5">
            <Eyebrow>At The Ports</Eyebrow>
            <h2 id="ports-heading" className="max-w-2xl text-3xl font-semibold tracking-tight sm:text-4xl">
              Where The Containers Work
            </h2>
            <p className="max-w-2xl text-sm leading-relaxed text-muted">
              Containers are stacked, loaded and moved through India&apos;s container terminals every day, carrying the
              goods of the businesses that lease them.
            </p>
          </div>
          <ul className="mt-12 grid grid-cols-2 gap-2 md:grid-cols-4 md:grid-rows-2">
            {PORT_GALLERY.map((item, index) => (
              <li key={item.photo}
                className={`group relative overflow-hidden bg-ink ${index === 0
                  ? "col-span-2 aspect-[4/3] md:row-span-2 md:aspect-auto"
                  : "aspect-square md:aspect-[4/3]"}`}>
                <figure className="size-full">
                  <PortPhoto name={item.photo} alt={item.alt}
                    sizes={index === 0 ? "(min-width: 768px) 50vw, 100vw" : "(min-width: 768px) 25vw, 50vw"}
                    className="size-full object-cover transition-transform duration-700 group-hover:scale-105" />
                  <figcaption className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-ink/85 to-transparent px-4 pt-10 pb-3 text-xs text-on-ink sm:text-sm">
                    {item.caption}
                  </figcaption>
                </figure>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-xs text-muted">
            Photos from Wikimedia Commons. <Link href="/credits" className="underline underline-offset-2 hover:text-foreground">Photo credits</Link>
          </p>
        </section>

        {/* --------------------------------------------------------- shipping lines */}
        <section aria-labelledby="lines-heading" className="border-t border-border px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="grid gap-6 lg:grid-cols-[1fr_1fr] lg:items-end lg:gap-14">
            <div className="space-y-5">
              <Eyebrow>The Global Network</Eyebrow>
              <h2 id="lines-heading" className="max-w-xl text-3xl font-semibold tracking-tight sm:text-4xl">
                The Lines That Move The World&apos;s Cargo
              </h2>
            </div>
            <p className="max-w-xl text-sm leading-relaxed text-muted">
              Most of the world&apos;s container trade, including the ships calling at Nhava Sheva, Mundra, Chennai and
              Kochi, is carried by a handful of large lines. The businesses that lease containers ship their goods on
              services like theirs, which is what keeps containers in demand and earning.
            </p>
          </div>
          <ul className="mt-12 grid grid-cols-2 gap-px border border-border bg-border sm:grid-cols-3 lg:grid-cols-5">
            {SHIPPING_LINES.map((line) => (
              // Always a white tile: the logos are drawn for a light background, in dark mode too.
              <li key={line.name} className="group flex h-24 items-center justify-center bg-white px-5 last:col-span-2 sm:h-28 sm:px-7 sm:last:col-span-1">
                {/* eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP */}
                <img src={`${COMMONS_FILES}/${line.logo}`} alt={line.name} title={line.name} loading="lazy" decoding="async"
                  className={`w-auto max-w-full object-contain opacity-60 grayscale transition duration-300 group-hover:opacity-100 group-hover:grayscale-0 ${line.crop ? `${line.crop} h-7 object-cover sm:h-9` : line.small ? "max-h-7 sm:max-h-9" : "max-h-10 sm:max-h-12"}`} />
              </li>
            ))}
          </ul>
        </section>

        {/* ----------------------------------------------------------- how it works */}
        <section id="how-it-works" className="scroll-mt-24 border-t border-border px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="space-y-5">
              <Eyebrow>How It Works</Eyebrow>
              <h2 className="max-w-xl text-3xl font-semibold tracking-tight sm:text-4xl">
                Three Steps From Sign-Up To Earning Returns
              </h2>
            </div>
            <LinkButton href="/register" variant="secondary">Create Your Account</LinkButton>
          </div>
          <ol className="mt-12 grid gap-px overflow-hidden rounded-xl border border-border bg-border md:grid-cols-3">
            {STEPS(credit).map((step, index) => (
              <li key={step.title} className="space-y-4 bg-surface p-8">
                {step.visual === "onboarding" && <OnboardingVisual />}
                {step.visual === "plan" && <ChoosePlanVisual plans={plans} />}
                {step.visual === "returns" && <EarnReturnsVisual plans={plans} />}
                <span className="font-display text-4xl font-semibold text-gold-text">0{index + 1}</span>
                <h3 className="text-xl font-semibold tracking-tight">{step.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* -------------------------------------------------------------- investors */}
        <section id="investors" className="grid scroll-mt-24 md:grid-cols-2">
          <div className="space-y-5 bg-background px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
            <Eyebrow>Your Investments</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Every Investment In One Place</h3>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Every investment appears in your portfolio once your payment is confirmed, with its amount, term and
              investment agreement to accept and download.
            </p>
            <div className="max-w-md rounded-xl border border-border bg-surface">
              <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3">
                <p className="text-sm font-semibold">Example investor&apos;s portfolio</p>
                <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">Example</span>
              </div>
              <ul className="divide-y divide-border">
                {example.holdings.map((holding) => (
                  <li key={holding.plan} className="space-y-1 px-5 py-4 text-sm">
                    <div className="flex items-center justify-between gap-4">
                      <p className="font-display font-semibold tracking-wide">{holding.plan}</p>
                      <span className="rounded-full bg-gold/15 px-2.5 py-0.5 text-xs text-gold-text">Running</span>
                    </div>
                    <p className="text-muted">Invested {inr(holding.price)} · {holding.years}-year term</p>
                  </li>
                ))}
              </ul>
            </div>
            <LinkButton href="/plans" variant="secondary">View Plans</LinkButton>
          </div>
          <div className="relative isolate space-y-5 overflow-hidden bg-ink px-5 py-20 text-on-ink sm:px-10 lg:px-14 lg:py-28">
            <PortPhoto name="container-wall" alt="" sizes="(min-width: 768px) 50vw, 100vw"
              className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-br from-ink via-ink/85 to-ink/60" />
            <Eyebrow tone="light">Your Returns</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Returns Set By Your Plan</h3>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Choose a plan by its tenure and buy units of it: each plan has its own annual return, paid monthly. Returns are credited to your wallet
              {credit} for the whole term, so you know from day one what each investment will earn.
            </p>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Open to every verified investor.
            </p>
            <div className="max-w-md border border-on-ink/20">
              <div className="flex items-center justify-between gap-4 border-b border-on-ink/20 px-5 py-3">
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
                    <tr key={holding.plan}>
                      <td className="px-5 py-2 text-on-ink/80">{holding.plan}</td>
                      <td className="px-5 py-2 text-right tabular-nums">{inr(holding.returns)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-on-ink/20">
                    <th scope="row" className="px-5 py-3 text-left font-semibold">Earned every month</th>
                    <td className="px-5 py-3 text-right font-display text-lg font-semibold text-gold tabular-nums">{inr(example.monthly)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p className="max-w-md text-xs leading-relaxed text-on-ink/60">
              Illustration at each plan&apos;s annual return, paid monthly. Your actual returns are shown on the plan&apos;s page before
              you invest.
            </p>
            {/* Signed out, this shows the public plans; signed in, the investor's own. */}
            <LinkButton href="/invest">Invest Now</LinkButton>
          </div>
        </section>

        {/* ------------------------------------------------------------------ plans */}
        {plans.length > 0 && (
          <section aria-labelledby="plans-heading" className="mt-20 px-5 sm:px-10 lg:mt-28 lg:px-14">
            <Eyebrow>Plans</Eyebrow>
            <h2 id="plans-heading" className="mt-4 text-3xl leading-tight font-normal tracking-tight sm:text-4xl">Choose Your Tenure</h2>
            <div className="mt-8">
              <PlanCarousel plans={plans} href="/invest" action="Invest" />
            </div>
          </section>
        )}

        {/* -------------------------------------------------------------------- cta */}
        <section aria-labelledby="cta-heading" className="relative isolate mt-20 overflow-hidden bg-ink px-5 py-20 text-on-ink sm:px-10 lg:mt-28 lg:px-14 lg:py-28">
          <PortPhoto name="terminal-above" alt="" sizes="(min-width: 1440px) 1440px, 100vw"
            className="absolute inset-0 -z-20 size-full object-cover" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-ink/70" />
          <div className="mx-auto max-w-2xl space-y-6 text-center">
            <Eyebrow tone="light">Start Today</Eyebrow>
            <h2 id="cta-heading" className="text-3xl font-semibold tracking-tight sm:text-4xl lg:text-5xl">
              Start Earning Monthly Returns
            </h2>
            <p className="text-base leading-relaxed text-on-ink/85">
              Pick your plan, choose your term, and earn returns credited to your wallet {credit}.
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <LinkButton href="/register">Register</LinkButton>
              <a href="#contact" className="inline-flex h-11 items-center px-2 text-sm text-on-ink/90 underline-offset-4 hover:underline">
                Talk to us first
              </a>
            </div>
          </div>
        </section>

        {/* -------------------------------------------------------------------- faq */}
        <section id="faq" className="scroll-mt-24 px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="mx-auto max-w-3xl">
            <Eyebrow>FAQ</Eyebrow>
            <h2 className="mt-5 text-3xl font-semibold tracking-tight sm:text-4xl">Questions Investors Ask Us</h2>
            <div className="mt-10 divide-y divide-border border-y border-border">
              {faqs(plans, credit).map((faq) => (
                <details key={faq.question} className="group py-5">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-6 text-base font-semibold [&::-webkit-details-marker]:hidden">
                    {faq.question}
                    <span aria-hidden="true" className="text-xl text-gold-text transition-transform group-open:rotate-45">+</span>
                  </summary>
                  <p className="mt-3 text-sm leading-relaxed text-muted">{faq.answer}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------------- contact */}
        <section id="contact" className="relative grid scroll-mt-24 gap-10 border-t border-border px-5 py-20 sm:px-10 lg:grid-cols-[1fr_1.4fr] lg:px-14 lg:py-28">
          <div className="space-y-5">
            <Eyebrow>Talk To Us</Eyebrow>
            <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">Not sure where to start?</h2>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Tell us a little about what you are looking for and an investment specialist will get back to you,
              whether you are making your first investment or adding to it.
            </p>
          </div>
          <InterestForm countries={phoneCountries} />
        </section>

        {/* ----------------------------------------------------------------- footer */}
        <footer className="flex flex-wrap items-center justify-between gap-6 border-t border-border px-5 py-8 sm:px-10 lg:px-14">
          <Logo />
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-muted">
            <Link href="/terms" className="inline-flex min-h-6 items-center underline-offset-4 hover:text-foreground hover:underline">Terms & conditions</Link>
            <p className="flex items-center gap-3">
              Platform status <StatusBadge tone={stateTone[backend]}>{backend}</StatusBadge>
            </p>
          </div>
          <p className="w-full text-xs text-muted">
            © {new Date().getFullYear()} Leasefleet Solutions Pvt. Ltd. All rights reserved.
          </p>
        </footer>
      </div>
    </div>
  );
}
