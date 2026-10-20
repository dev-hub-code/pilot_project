import Link from "next/link";
import { connection } from "next/server";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { LandingMenu } from "@/components/layout/landing-menu";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { Carousel } from "@/components/ui/carousel";
import { ReviewCarousel, type Review } from "@/features/landing/review-carousel";
import { InterestForm } from "@/features/leads/interest-form";
import { getPlatformStats, type PlatformStats } from "@/lib/server/platform-stats";
import { getPublicPlans } from "@/lib/server/public-plans";
import { getPhoneCountries } from "@/lib/server/phone-countries";
import { getBackendStatus, type ServiceState } from "@/lib/server/system-status";
import { formatMoney } from "@/utils/money";

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

const BENEFITS = [
  {
    title: "Income every month",
    body: "Each container you own pays you its monthly rent, credited straight to your SeaLease wallet.",
  },
  {
    title: "Your money comes back",
    body: "Along with the rent, part of what you paid is returned every month, so your full investment is back with you by the end of the lease.",
  },
  {
    title: "Grows with your investment",
    body: "Payouts are per container. Own two containers and your monthly income doubles; own ten and it is ten times as much.",
  },
  {
    title: "Clear from day one",
    body: "You see the price, monthly rent and lease length before you buy, every payout is on record, and you can withdraw to your verified bank account.",
  },
];

/**
 * Investor reviews, shown as a carousel. Only real reviews, published with the investor's consent, may go here. Entries
 * marked `sample` are layout placeholders, never shown in production. Every review must
 * match how the platform works today: whole containers, rent only (the price is not paid back), lease chosen in whole years,
 * rent credited to the wallet every hour, withdrawals to a verified bank account or in cash at the office.
 */
const REVIEWS: Review[] = [
  {
    quote: "My rent reaches my SeaLease wallet every hour, and the Earnings page shows how much each container has earned. No chasing anyone.",
    name: "Priya S.",
    detail: "Pune · 3 containers",
    sample: true,
  },
  {
    quote: "I started with a single container to see how it works. Once finance assigned it, its container number showed up in my portfolio, so I knew exactly which box was mine. I have added two more since.",
    name: "Rajesh K.",
    detail: "Ahmedabad · 3 containers",
    sample: true,
  },
  {
    quote: "KYC took me ten minutes: Aadhaar, PAN and the two card photos. It was approved the next day and the marketplace opened up for me.",
    name: "Ananya R.",
    detail: "Bengaluru · 1 container",
    sample: true,
  },
  {
    quote: "I paid by NEFT to the company account shown at checkout and entered the transaction ID. Finance confirmed it and the order moved to paid without me having to call.",
    name: "Mohammed I.",
    detail: "Hyderabad · 2 containers",
    sample: true,
  },
  {
    quote: "Being able to choose the lease length in whole years mattered to me. I took a longer lease on one container and a shorter one on another.",
    name: "Sunita D.",
    detail: "Nagpur · 2 containers",
    sample: true,
  },
  {
    quote: "I prefer cash, so I paid at the office with my payment reference and got a receipt on the spot. The rent on that container is collected at the office too, which suits me.",
    name: "Harish P.",
    detail: "Surat · 1 container",
    sample: true,
  },
  {
    quote: "The container page told me the monthly rent for the number of containers I picked before I paid anything. What I see in my wallet each month matches it.",
    name: "Kavita N.",
    detail: "Mumbai · 4 containers",
    sample: true,
  },
  {
    quote: "I set nothing up and my balance still lands in my primary bank account every month on the withdrawal day. When I need money sooner, I request a withdrawal myself.",
    name: "Deepak V.",
    detail: "Indore · 5 containers",
    sample: true,
  },
  {
    quote: "My brother and two colleagues joined through my referral link. The commission comes on top of their rent, so their payouts are not reduced, which made it easy to recommend.",
    name: "Farhan A.",
    detail: "Lucknow · 2 containers",
    sample: true,
  },
  {
    quote: "My container was assigned in the middle of the month and the rent started that same day, by the hour. No waiting for a payout date.",
    name: "Meera J.",
    detail: "Kochi · 1 container",
    sample: true,
  },
  {
    quote: "I asked support about adding a second bank account. The ticket was answered the same day, and the account was verified after I uploaded a cancelled cheque.",
    name: "Gurpreet S.",
    detail: "Ludhiana · 2 containers",
    sample: true,
  },
  {
    quote: "My accountant needs everything on paper. I download the statement for the year and the invoice for each order, all in my name.",
    name: "Nikhil T.",
    detail: "Jaipur · 6 containers",
    sample: true,
  },
  {
    quote: "I liked seeing photos of each container type, with the price per container and the rent spelled out, before I bought anything.",
    name: "Lakshmi V.",
    detail: "Chennai · 2 containers",
    sample: true,
  },
  {
    quote: "I got an email when my KYC was approved, when my bank account was verified and when my container was assigned. Every payout is listed under Earnings, so I always know where things stand.",
    name: "Arvind M.",
    detail: "Kolkata · 3 containers",
    sample: true,
  },
  {
    quote: "I wanted a monthly income without managing anything myself. Rent on two containers now comes in every month, and the lease end date is right there in my portfolio.",
    name: "Rekha B.",
    detail: "Vadodara · 2 containers",
    sample: true,
  },
];

/** Every answer must match how the platform actually works (see the README's marketplace, earnings and withdrawal rules). */
const FAQS = [
  {
    question: "What exactly am I investing in?",
    answer: "You buy whole shipping containers. Each one is a specific container with its own container number, assigned to you once your payment is confirmed. We lease it out for the lease period you choose and pay you from it every month. There are no shared or fractional units.",
  },
  {
    question: "How much will I receive every month?",
    answer: "Every month of the lease you receive rent: a fixed percentage of the price you paid, set by your order's total value (larger orders can earn a higher rate). For example, at 2% monthly rent, a ₹2,50,000 container pays you ₹5,000 every month. The marketplace shows the exact monthly amount for the number of containers before you buy.",
  },
  {
    question: "Do I get my investment back?",
    answer: "No. Containers pay rent only: you receive the monthly rent for every month of the lease, and the price you paid is not paid back. The marketplace shows the total rent over the lease before you buy.",
  },
  {
    question: "How much do I need to start?",
    answer: "One container. The price of each container is shown in the marketplace, and you can buy more containers whenever you like. Your monthly income grows with every container you own.",
  },
  {
    question: "What do I need before I can invest?",
    answer: "Create a free account and complete identity verification (KYC). Once your KYC is approved, you can buy any container on sale in the marketplace. The marketplace tells you if anything is still pending.",
  },
  {
    question: "How do I pay?",
    answer: "By bank payment to one of our company bank accounts: online (NEFT, RTGS, IMPS or UPI) or by cheque. You then enter the transaction ID or cheque number, and our finance team confirms the payment. Card payments are not accepted.",
  },
  {
    question: "When is rent credited, and where does the money go?",
    answer: "Every day. Each container's rent for the day (that month's rent ÷ its days) is credited to your SeaLease wallet, and every credit is recorded under Earnings.",
  },
  {
    question: "How is my money paid out?",
    answer: "Automatically, every month. Rent on containers you paid for by bank is paid into your verified bank account on the 10th; rent on containers paid in cash at our office is collected in cash there from the 10th. Referral earnings are paid on the 15th, into your bank account or in cash at our office, as you choose once a year.",
  },
  {
    question: "What happens at the end of the lease?",
    answer: "When the last hour of the lease is credited, your holding is marked as matured: you have received the rent for the whole lease, and the container returns to SeaLease. You can then buy new containers in the marketplace.",
  },
  {
    question: "Can I earn by referring others?",
    answer: "Yes. Share your referral link from the Referrals page. When people you refer receive their monthly rent, you earn a commission on it, and the platform pays it, so their own payout is never reduced. The current rates are shown on your Referrals page.",
  },
  {
    question: "Is my personal and bank information safe?",
    answer: "Sensitive details such as identity documents, tax IDs and bank account numbers are stored encrypted, and only authorised employees can view them. Every view of a KYC document is logged.",
  },
];

/**
 * Example portfolio for the Investors section: one hypothetical investor holding two containers on the demo
 * plans (scripts/seed-demo-data.py). Amounts follow the payout rule: rent = price × rent %, paid every month of the
 * tenure (plans pay rent only).
 */
const EXAMPLE_HOLDINGS = [
  { number: "SLSU 100501 3", type: "20ft dry", location: "Nhava Sheva (JNPT), Mumbai", price: "250000", tenure: 16, rent: "5000" },
  { number: "SLSU 100401 7", type: "40ft dry", location: "Mundra Port, Gujarat", price: "400000", tenure: 18, rent: "9000" },
];
const EXAMPLE_MONTHLY_TOTAL = "14000";

const inr = (amount: string) => formatMoney({ amount, currency: "INR" });

/**
 * The company's track record since 2022, shown in the "Since 2022" block. Enter the REAL, verifiable totals
 * (including business done before this platform); leave a field null to show the live platform figure instead.
 * Shown as "₹12 Cr+" for the amount and "3.5K+" for the counts.
 */
const COMPANY_RECORD: {
  paidToInvestorsCrore: number | null;
  payoutsMade: number | null;
  investors: number | null;
  containers: number | null;
} = {
  paidToInvestorsCrore: null, // e.g. 12 → "₹12 Cr+"
  payoutsMade: null, // e.g. 3500 → "3.5K+"
  investors: null, // e.g. 1200 → "1.2K+"
  containers: null, // e.g. 2000 → "2K+"
};

/**
 * Sample figures (not real) to preview the "Since 2022" row while developing. Never shown in production, and
 * labelled on the page. They apply only to fields left null in COMPANY_RECORD.
 */
const SAMPLE_RECORD = { paidToInvestorsCrore: 12, payoutsMade: 3500, investors: 1200, containers: 2000 };

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
    containers: COMPANY_RECORD.containers ?? SAMPLE_RECORD.containers,
  } : COMPANY_RECORD;
  const live = SHOW_LIVE_FIGURES && stats && stats.containersEarning > 0 ? stats : null;
  const figures = {
    paid: record.paidToInvestorsCrore !== null ? `₹${record.paidToInvestorsCrore.toLocaleString("en-IN")} Cr+`
      : live && formatMoney(live.paidToInvestors, { compact: true }),
    // Rent is credited hourly now, so the platform has no count of payouts: only the company's own figure is shown.
    payouts: record.payoutsMade !== null ? thousands(record.payoutsMade) : null,
    investors: record.investors !== null ? thousands(record.investors) : live?.investors.toLocaleString("en-IN"),
    containers: record.containers !== null ? thousands(record.containers) : live?.containers.toLocaleString("en-IN"),
  };
  return Object.values(figures).every(Boolean) ? (figures as Record<keyof typeof figures, string>) : null;
}

const STEPS = [
  {
    title: "Choose your containers",
    body: "Each container in the marketplace shows its type, price, monthly rent and the length of the lease up front.",
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
  const [backend, stats, plans, phoneCountries] = await Promise.all([getBackendStatus(), getPlatformStats(), getPublicPlans(),
    getPhoneCountries()]);
  const production = process.env.NODE_ENV === "production";
  const usesSampleFigures = !production && Object.values(COMPANY_RECORD).some((value) => value === null);
  const figures = trackRecord(stats, usesSampleFigures);
  const reviews = production ? REVIEWS.filter((review) => !review.sample) : REVIEWS;

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto max-w-[1440px] bg-surface">
        {/* ------------------------------------------------------------------- hero */}
        <section className="relative isolate flex min-h-[640px] flex-col overflow-hidden bg-ink text-on-ink lg:h-[min(88vh,860px)]">
          <ContainerScene className="absolute inset-0 -z-10 size-full" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink/90 via-ink/45 to-ink/30 lg:hidden" />

          <header className="relative flex items-center justify-between gap-6 px-5 py-6 sm:px-10 lg:px-14 lg:py-8">
            <Logo tone="light" />
            <nav aria-label="Main" className="flex items-center gap-3 sm:gap-4 lg:gap-6 xl:gap-8">
              <ul className="hidden items-center gap-6 text-sm lg:flex xl:gap-8">
                {NAV.map((item) => (
                  <li key={item.href}>
                    {/* In-page sections use plain anchors: <Link> skips the scroll when the URL already has that hash. */}
                    {item.href.startsWith("#") ? (
                      <a href={item.href} className="whitespace-nowrap text-on-ink/85 transition-colors hover:text-on-ink">
                        {item.label}
                      </a>
                    ) : (
                      <Link href={item.href} className="whitespace-nowrap text-on-ink/85 transition-colors hover:text-on-ink">
                        {item.label}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
              <Link
                href="/register"
                className="hidden whitespace-nowrap rounded-full border border-on-ink/80 px-4 py-2 text-sm transition-colors hover:bg-on-ink hover:text-ink sm:inline-flex lg:px-5 lg:py-2.5"
              >
                Start Investing
              </Link>
              <LandingMenu links={NAV} />
            </nav>
          </header>

          <div className="mt-auto grid gap-10 px-5 pb-10 sm:px-10 lg:grid-cols-2 lg:items-end lg:px-14 lg:pb-16">
            <h1 className="max-w-xl text-5xl leading-[1.02] font-bold tracking-tight sm:text-6xl lg:text-7xl">
              Invest In The Containers That Move The World
            </h1>
            <div className="max-w-lg space-y-7 lg:justify-self-end">
              <p className="text-base leading-relaxed text-on-ink/90">
                Buy Whole Shipping Containers, Each Assigned To You By Its Own Number, And Receive
                Rent Every Month Of The Lease.
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
            Own Real Shipping Containers And Earn From Them Every Month. The More You Invest, The More You Receive.
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-muted">
            With SeaLease™ you buy real shipping containers that are leased to businesses moving goods around the
            world. We handle the leasing; you collect the income. You don&apos;t need any shipping experience, and
            you can start with a single container.
          </p>
          <ul className="mt-14 grid gap-px bg-border text-left sm:grid-cols-2 lg:grid-cols-4">
            {BENEFITS.map((benefit) => (
              <li key={benefit.title} className="space-y-3 bg-surface p-8">
                <h3 className="text-lg font-semibold tracking-tight">{benefit.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{benefit.body}</p>
              </li>
            ))}
          </ul>
          <div className="mx-auto mt-14 max-w-3xl space-y-4 border border-border bg-background px-6 py-10 sm:px-10">
            <p className="font-display text-4xl font-semibold text-gold-text">Since 2022</p>
            <h3 className="text-xl font-semibold tracking-tight sm:text-2xl">Trusted By Investors Around The World</h3>
            <p className="text-sm leading-relaxed text-muted">
              Since 2022, investors from around the world have invested with SeaLease, and we have been leasing
              their containers and paying them their monthly rent and capital ever since.
              Every payout is recorded in our ledger, and investors can see each one in their account and withdraw it
              to their own bank.
            </p>
            {figures && usesSampleFigures && (
              <p className="pt-2">
                <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">
                  Sample figures, hidden on the live site
                </span>
              </p>
            )}
            {figures && (
              <dl className="grid grid-cols-2 gap-px bg-border pt-px sm:grid-cols-4">
                {[
                  { label: "Paid to investors", value: figures.paid },
                  { label: "Payouts made", value: figures.payouts },
                  { label: "Investors", value: figures.investors },
                  { label: "Containers owned by investors", value: figures.containers },
                ].filter((figure) => figure.value).map((figure) => (
                  <div key={figure.label} className="flex flex-col-reverse justify-end gap-1 bg-background px-3 py-5">
                    <dt className="text-xs text-muted">{figure.label}</dt>
                    <dd className="font-display text-2xl font-semibold">{figure.value}</dd>
                  </div>
                ))}
              </dl>
            )}
            <p className="text-xs leading-relaxed text-muted">
              Past payouts are not a guarantee of future returns. Every container&apos;s rent and lease length are shown in
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
            <div className="max-w-md border border-border bg-surface">
              <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3">
                <p className="text-sm font-semibold">Example investor&apos;s portfolio</p>
                <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">Example</span>
              </div>
              <ul className="divide-y divide-border">
                {EXAMPLE_HOLDINGS.map((holding) => (
                  <li key={holding.number} className="space-y-1 px-5 py-4 text-sm">
                    <div className="flex items-center justify-between gap-4">
                      <p className="font-display font-semibold tracking-wide">{holding.number}</p>
                      <span className="rounded-full bg-gold/15 px-2.5 py-0.5 text-xs text-gold-text">On lease</span>
                    </div>
                    <p className="text-muted">{holding.type} · {holding.location}</p>
                    <p className="text-muted">Bought for {inr(holding.price)} · {holding.tenure}-month lease</p>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="space-y-5 bg-ink px-5 py-16 text-on-ink sm:px-10 lg:px-14">
            <Eyebrow tone="light">Monthly Payouts</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Rent, Every Month</h3>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Each container is leased for the years you choose, and every month of it you receive
              rent. The price you paid is not paid back. Open to every verified investor.
            </p>
            <div className="max-w-md border border-on-ink/20">
              <div className="flex items-center justify-between gap-4 border-b border-on-ink/20 px-5 py-3">
                <p className="text-sm font-semibold">The same investor&apos;s monthly payout</p>
                <span className="rounded-full border border-on-ink/30 px-3 py-1 text-xs text-on-ink/70">Example</span>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-on-ink/60">
                    <th scope="col" className="px-5 pt-3 pb-1 font-normal">Container</th>
                    <th scope="col" className="px-5 pt-3 pb-1 text-right font-normal">Rent</th>
                  </tr>
                </thead>
                <tbody>
                  {EXAMPLE_HOLDINGS.map((holding) => (
                    <tr key={holding.number}>
                      <td className="px-5 py-2 text-on-ink/80">{holding.type}</td>
                      <td className="px-5 py-2 text-right tabular-nums">{inr(holding.rent)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-on-ink/20">
                    <th scope="row" className="px-5 py-3 text-left font-semibold">Paid every month</th>
                    <td className="px-5 py-3 text-right font-display text-lg font-semibold text-gold tabular-nums">{inr(EXAMPLE_MONTHLY_TOTAL)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p className="max-w-md text-xs leading-relaxed text-on-ink/60">
              Illustration on example terms. Each container&apos;s actual price, rent and lease length are shown in the
              marketplace before you buy.
            </p>
          </div>
        </section>

        {/* ---------------------------------------------------------- earnings by container */}
        {plans.length > 0 && (
          <section id="earnings-by-container" className="scroll-mt-8 border-t border-border px-5 py-20 sm:px-10 lg:px-14">
            <div className="flex flex-wrap items-end justify-between gap-6">
              <div className="space-y-5">
                <Eyebrow>Investor Earnings</Eyebrow>
                <h2 className="max-w-2xl text-3xl font-semibold tracking-tight sm:text-4xl">
                  What Each Container Pays You
                </h2>
                <p className="max-w-2xl text-sm leading-relaxed text-muted">
                  Containers on sale now and their terms. Every container pays rent each month of the lease, and you
                  choose the lease length in whole years within its range. Containers pay rent only: the price is not
                  paid back.
                </p>
              </div>
              <LinkButton href="/register" variant="secondary">Start investing</LinkButton>
            </div>
            <div className="mt-12">
              <Carousel label="Containers on sale and what they pay" itemsName="containers" itemClassName="flex flex-col gap-6 p-8">
              {plans.map((plan) => {
                const years = plan.minTenureYears === plan.maxTenureYears
                  ? `${plan.minTenureYears} ${plan.minTenureYears === 1 ? "year" : "years"}`
                  : `${plan.minTenureYears}–${plan.maxTenureYears} years`;
                return (
                  <div key={plan.code} className="contents">
                    <div>
                      <p className="text-xs uppercase tracking-[0.1em] text-muted">{plan.containerTypeLabel} container</p>
                      <p className="mt-2 text-lg font-semibold tracking-tight">{plan.title}</p>
                    </div>
                    <dl className="grid grid-cols-2 gap-x-4 gap-y-5 text-sm">
                      <div>
                        <dt className="text-muted">Price per container</dt>
                        <dd className="mt-1 font-display text-xl font-semibold tabular-nums">{formatMoney(plan.price)}</dd>
                      </div>
                      <div>
                        <dt className="text-muted">Lease</dt>
                        <dd className="mt-1 font-display text-xl font-semibold">{years}</dd>
                      </div>
                    </dl>
                    <p className="mt-auto border-t border-border pt-4 text-sm text-muted">
                      Monthly rent is set by your order&apos;s total value: the more you buy in one order, the higher the
                      rate can be. The marketplace shows the rent for your order before you pay.
                    </p>
                  </div>
                );
              })}
              </Carousel>
            </div>
            <p className="mt-6 text-xs leading-relaxed text-muted">
              Figures are the published terms, not a promise of future returns. Rent is credited to your
              wallet every hour of the lease, from the full hour after your container is assigned.
            </p>
          </section>
        )}

        {/* -------------------------------------------------------------------- faq */}
        <section id="faq" className="scroll-mt-8 border-t border-border px-5 py-20 sm:px-10 lg:px-14">
          <div className="mx-auto max-w-3xl">
            <Eyebrow>FAQ</Eyebrow>
            <h2 className="mt-5 text-3xl font-semibold tracking-tight sm:text-4xl">Questions Investors Ask Us</h2>
            <div className="mt-10 divide-y divide-border border-y border-border">
              {FAQS.map((faq) => (
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
        <section id="contact" className="relative grid scroll-mt-8 gap-10 border-t border-border px-5 py-20 sm:px-10 lg:grid-cols-[1fr_1.4fr] lg:px-14">
          <div className="space-y-5">
            <Eyebrow>Talk To Us</Eyebrow>
            <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">Not sure where to start?</h2>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Tell us a little about what you are looking for and an investment specialist will get back to you,
              whether you are buying your first container or adding to your fleet.
            </p>
          </div>
          <InterestForm countries={phoneCountries} />
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
