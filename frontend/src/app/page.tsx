import Link from "next/link";
import { connection } from "next/server";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { PortPhoto, type PortPhotoName } from "@/components/brand/port-photo";
import { LandingMenu } from "@/components/layout/landing-menu";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { ReviewCarousel, type Review } from "@/features/landing/review-carousel";
import { InterestForm } from "@/features/leads/interest-form";
import { getPlatformStats, type PlatformStats } from "@/lib/server/platform-stats";
import { getPhoneCountries } from "@/lib/server/phone-countries";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import { getRentTerms, tierFor, type RentTerms } from "@/lib/server/rent-terms";
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
    title: "Rent from your first hour",
    body: `Your rent starts at the next full hour after you accept your container's lease agreement and is credited to your SeaLease wallet ${credit}.`,
  },
  {
    title: "Bigger orders earn more",
    body: "Your monthly rent is a percentage of each container's price, and the percentage is set by your order's total value: the more you invest in one order, the higher the rate.",
  },
  {
    title: "A real container, by its number",
    body: "You own whole containers, never shares. Each one is assigned to you by its unique container number, with its own lease agreement you can download.",
  },
  {
    title: "Clear from day one",
    body: "The calculator shows your rent before you buy, every credit is on record under Earnings, and our team pays your earnings out to your verified bank account or in cash at our office.",
  },
];

/**
 * Investor reviews, shown as a carousel. Only real reviews, published with the investor's consent, may go here. Entries
 * marked `sample` are layout placeholders, never shown in production. Every review must
 * match how the platform works today: whole containers, rent only (the price is not paid back), lease chosen in whole years,
 * rent by order value, rent credited to the wallet as it accrues, onboarding (details, KYC, bank account), the lease
 * starting when the agreement is accepted, payouts made by the team to a verified bank account or in cash at the office.
 */
const REVIEWS: Review[] = [
  {
    quote: "I can watch my wallet grow through the day, and the Earnings page shows how much each container has earned. No chasing anyone.",
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
    quote: "Onboarding took me ten minutes: my details, Aadhaar and PAN with photos of the cards, then my bank account. Once both were verified the marketplace opened up for me.",
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
    quote: "The calculator on the container page showed my rent before I paid anything. I bought three in one order to get the higher rate, and my wallet matches it.",
    name: "Kavita N.",
    detail: "Mumbai · 4 containers",
    sample: true,
  },
  {
    quote: "I set nothing up: when the team makes the monthly withdrawal, my balance lands in my primary bank account. The Withdrawals page shows each one.",
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
    quote: "My container was assigned in the middle of the month. I accepted the agreement from the notification and the rent started at the next hour. No waiting for a payout date.",
    name: "Meera J.",
    detail: "Kochi · 1 container",
    sample: true,
  },
  {
    quote: "I asked support about adding a second bank account. The ticket was answered the same day, and finance verified the new account soon after.",
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
    quote: "I got a notification when my KYC was approved, when my bank account was verified and when my container was assigned. Tapping it took me straight there, so I always know where things stand.",
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

/** Every answer must match how the platform works today; rates, the minimum order and the credit interval are live. */
function faqs(terms: RentTerms | null, credit: string) {
  const rates = terms
    ? terms.tiers.map((t) => `${formatPercent(t.monthlyRentPercent)} a month for orders ${t.toAmount
      ? `of ${formatMoney(t.fromAmount)} to ${formatMoney(t.toAmount)}` : `of ${formatMoney(t.fromAmount)} and above`}`).join(", ")
    : null;
  const example = terms?.tiers[0];
  return [
    {
      question: "What exactly am I investing in?",
      answer: "You buy whole shipping containers. Each one is a specific container with its own container number, assigned to you once your payment is confirmed, with a lease agreement for the years you choose. We lease it out and pay you rent from it. There are no shared or fractional units.",
    },
    {
      question: "How much rent will I receive?",
      answer: `Every month of the lease you receive rent on each container: a percentage of its price, set by your order's total value${rates ? ` (currently ${rates})` : ""}. ${example
        ? `For example, at ${formatPercent(example.monthlyRentPercent)}, a ₹1,00,000 container pays you ${formatMoney({ amount: String(1000 * example.monthlyRentPercent), currency: "INR" })} a month. `
        : ""}The calculator on each container's page shows your exact rent before you buy.`,
    },
    {
      question: "How much do I need to start?",
      answer: terms?.minimumOrder
        ? `The minimum order is ${formatMoney(terms.minimumOrder)}. Each container's price is shown in the marketplace, and you can invest again whenever you like.`
        : "One container. Each container's price is shown in the marketplace, and you can invest again whenever you like.",
    },
    {
      question: "What do I need before I can invest?",
      answer: "A free account and three onboarding steps: your personal details, identity verification (Aadhaar and PAN) and a bank account for payouts. Once our team has verified your identity and bank account, you can invest in any container on sale.",
    },
    {
      question: "How do I pay?",
      answer: "By bank payment to one of our company bank accounts, online (NEFT, RTGS, IMPS or UPI) or by cheque, quoting your payment reference and entering the transaction ID or cheque number; or in cash at our office. Our finance team confirms the payment. Card payments are not accepted.",
    },
    {
      question: "When does my rent start, and how is it credited?",
      answer: `Once your payment is confirmed, our team assigns each container to you and you accept its lease agreement. The lease starts at the next full hour, and your rent is credited to your SeaLease wallet ${credit}, every credit recorded under Earnings.`,
    },
    {
      question: "How is my money paid out?",
      answer: "Our team makes withdrawals every month: rent on containers paid by bank goes to your verified primary bank account, and rent on containers paid in cash is collected in cash at our office. Referral earnings are paid to your bank or in cash, as you choose once a year. Small balances wait for the next withdrawal.",
    },
    {
      question: "What happens at the end of the lease?",
      answer: "When the last rent of the lease is credited, your holding is marked as matured: you have received the rent for the whole lease, and the container returns to SeaLease. You can then invest in new containers in the marketplace.",
    },
    {
      question: "Can I earn by referring others?",
      answer: "Yes. Share your referral link from the Referrals page. When people in your network (up to four levels) receive their rent, you earn a commission on it. The platform pays it, so their own rent is never reduced. The current rates are shown on your Referrals page.",
    },
    {
      question: "Is my personal and bank information safe?",
      answer: "Sensitive details such as identity documents, tax IDs and bank account numbers are stored encrypted, and only authorised employees can view them. Every view of a KYC document is logged.",
    },
  ];
}

/**
 * Example portfolio for the Investors section: one hypothetical investor who bought two containers in one order.
 * Rent follows the live rule: each container's price × the rate for the order's total, every month of the lease.
 */
const EXAMPLE_HOLDINGS = [
  { number: "SLSU 100501 3", type: "20ft dry", location: "Nhava Sheva (JNPT), Mumbai", price: 250000, years: 3 },
  { number: "SLSU 100401 7", type: "40ft dry", location: "Mundra Port, Gujarat", price: 400000, years: 5 },
];
/** Used when the rates cannot be loaded. */
const EXAMPLE_FALLBACK_PERCENT = 5;

function examplePortfolio(terms: RentTerms | null) {
  const total = EXAMPLE_HOLDINGS.reduce((sum, h) => sum + h.price, 0);
  const percent = (terms && tierFor(terms, total)?.monthlyRentPercent) ?? EXAMPLE_FALLBACK_PERCENT;
  const holdings = EXAMPLE_HOLDINGS.map((h) => ({ ...h, rent: Math.round(h.price * percent) / 100 }));
  return { holdings, total, percent, monthly: holdings.reduce((sum, h) => sum + h.rent, 0) };
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

const STEPS = (credit: string): { title: string; body: string; photo: PortPhotoName; alt: string }[] => [
  {
    title: "Complete onboarding",
    photo: "miami-port",
    alt: "Aerial view of a container port on an island, with ships moored at the quay",
    body: "Create your free account, add your personal details, verify your identity with Aadhaar and PAN, and add the bank account your earnings are paid into.",
  },
  {
    title: "Invest in containers",
    photo: "yard-stacks",
    alt: "Rows of shipping containers stacked in a port yard beside a harbour crane",
    body: "Pick a container, set how many and the lease in years in the calculator to see your rent, then invest now and pay by bank or in cash at our office.",
  },
  {
    title: "Earn rent",
    photo: "container-lift",
    alt: "A shipping container lifted by a ship-to-shore crane onto a truck at the quay",
    body: `Once your payment is confirmed, each container is assigned to you by its number. Accept its lease agreement and rent is credited to your wallet ${credit}, then paid out to your bank account.`,
  },
];

/** The photo strip: a large lead photo, then four smaller ones. */
const PORT_GALLERY: { photo: PortPhotoName; alt: string; caption: string }[] = [
  { photo: "terminal-above", alt: "Container stacks and gantry cranes in a terminal, seen from directly above", caption: "Container terminal from above" },
  { photo: "container-wall", alt: "A wall of red, yellow, blue and teal shipping containers stacked five high", caption: "Stacked five high" },
  { photo: "tacoma-cranes", alt: "Ship-to-shore cranes over long rows of stacked containers at the Port of Tacoma", caption: "Port of Tacoma, USA" },
  { photo: "singapore-terminal", alt: "Container stacks in a grid of lanes at a terminal in Singapore, from the air", caption: "Singapore" },
  { photo: "savannah-aerial", alt: "Container ships at the berths of the Port of Savannah, with the stacking yard behind", caption: "Port of Savannah, USA" },
];

export default async function Home() {
  // System status must reflect the live backend, never a build-time snapshot.
  await connection();
  const [backend, stats, phoneCountries, terms, rentCredit] = await Promise.all([getBackendStatus(), getPlatformStats(),
    getPhoneCountries(), getRentTerms(), getRentCredit()]);
  const credit = creditLabel(rentCredit);
  const example = examplePortfolio(terms);
  const production = process.env.NODE_ENV === "production";
  const usesSampleFigures = !production && Object.values(COMPANY_RECORD).some((value) => value === null);
  const figures = trackRecord(stats, usesSampleFigures);
  const reviews = production ? REVIEWS.filter((review) => !review.sample) : REVIEWS;

  return (
    <div className="flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto max-w-[1440px] bg-surface">
        {/* ------------------------------------------------------------------- hero */}
        <section className="relative isolate flex min-h-[640px] flex-col overflow-hidden bg-ink text-on-ink lg:h-[min(88vh,860px)]">
          <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1440px) 1440px, 100vw"
            className="absolute inset-0 -z-20 size-full object-cover" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-ink/95 via-ink/55 to-ink/40" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 hidden bg-gradient-to-r from-ink/60 to-transparent lg:block" />

          <header className="relative flex items-center justify-between gap-6 px-5 py-6 sm:px-10 lg:px-14 lg:py-8">
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
                Start Investing
                <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M13 6l6 6-6 6" />
                </svg>
              </Link>
              <LandingMenu links={NAV} />
            </nav>
          </header>

          <div className="mt-auto grid gap-10 px-5 pb-10 sm:px-10 lg:grid-cols-2 lg:items-end lg:px-14 lg:pb-16">
            <h1 className="max-w-xl text-4xl leading-[1.04] font-bold tracking-tight min-[400px]:text-5xl sm:text-6xl xl:text-7xl">
              Invest In The Containers That Move The World
            </h1>
            <div className="max-w-lg space-y-7 lg:justify-self-end">
              <p className="text-base leading-relaxed text-on-ink/90">
                Buy Whole Shipping Containers, Each Assigned To You By Its Own Number, And Earn
                Rent From The First Hour Of The Lease.
              </p>
              <dl className="grid gap-3 border-t border-on-ink/20 pt-6 sm:grid-cols-3 sm:gap-6">
                {[
                  { term: "Whole containers", detail: "Never shares: each has its own ISO number" },
                  { term: "Rent " + credit, detail: "Credited to your wallet from the first full hour" },
                  { term: "Your own lease", detail: "An agreement for every container you own" },
                ].map((point) => (
                  <div key={point.term} className="flex items-baseline gap-3 sm:block sm:space-y-1.5">
                    <dt className="w-36 shrink-0 text-sm font-semibold text-gold first-letter:uppercase sm:w-auto">{point.term}</dt>
                    <dd className="text-xs leading-relaxed text-on-ink/75">{point.detail}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------------ about */}
        <section id="about" className="scroll-mt-8 px-5 py-20 text-center sm:px-10 lg:py-28">
          <Eyebrow>About SeaLease</Eyebrow>
          <h2 className="mx-auto mt-8 max-w-4xl text-3xl leading-tight font-normal tracking-tight sm:text-4xl lg:text-5xl">
            Own Real Shipping Containers And Earn Rent From Them. The More You Invest, The Higher Your Rate.
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-muted">
            With SeaLease™ you buy real shipping containers that are leased to businesses moving goods around the
            world. We handle the leasing; you collect the income. You don&apos;t need any shipping experience, and
            you can start with a single order{terms?.minimumOrder ? ` from ${formatMoney(terms.minimumOrder)}` : ""}.
          </p>
          <ul className="mt-14 grid gap-px bg-border text-left sm:grid-cols-2 xl:grid-cols-4">
            {BENEFITS(credit).map((benefit) => (
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
              their containers and paying them their rent ever since.
              Every credit is recorded in our ledger, and investors can see each one in their account and are paid
              out to their own bank.
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
                    <dd className="font-display text-xl font-semibold whitespace-nowrap sm:text-2xl">{figure.value}</dd>
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

        {/* ---------------------------------------------------------------- ports */}
        <section aria-labelledby="ports-heading" className="border-t border-border px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="space-y-5">
            <Eyebrow>At The Ports</Eyebrow>
            <h2 id="ports-heading" className="max-w-2xl text-3xl font-semibold tracking-tight sm:text-4xl">
              Where Your Containers Work
            </h2>
            <p className="max-w-2xl text-sm leading-relaxed text-muted">
              Containers are stacked, loaded and moved through terminals like these every day, carrying the goods of the
              businesses that lease them.
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
        </section>

        {/* ----------------------------------------------------------- how it works */}
        <section id="how-it-works" className="scroll-mt-8 border-t border-border px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="space-y-5">
              <Eyebrow>How It Works</Eyebrow>
              <h2 className="max-w-xl text-3xl font-semibold tracking-tight sm:text-4xl">
                Three Steps From Sign-Up To Earning Rent
              </h2>
            </div>
            <LinkButton href="/register" variant="secondary">Create Your Account</LinkButton>
          </div>
          <ol className="mt-12 grid gap-px bg-border md:grid-cols-3">
            {STEPS(credit).map((step, index) => (
              <li key={step.title} className="space-y-4 bg-surface p-8">
                <div className="-mx-8 -mt-8 mb-8 aspect-[16/10] overflow-hidden bg-ink">
                  <PortPhoto name={step.photo} alt={step.alt} sizes="(min-width: 768px) 33vw, 100vw"
                    className="size-full object-cover" />
                </div>
                <span className="font-display text-4xl font-semibold text-gold-text">0{index + 1}</span>
                <h3 className="text-xl font-semibold tracking-tight">{step.title}</h3>
                <p className="text-sm leading-relaxed text-muted">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* -------------------------------------------------------------- investors */}
        <section id="investors" className="grid scroll-mt-8 md:grid-cols-2">
          <div className="space-y-5 bg-background px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
            <Eyebrow>Your Own Containers</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">A Container With Your Name On It</h3>
            <p className="max-w-md text-sm leading-relaxed text-muted">
              Every container you buy is a specific, identified container: you see its ISO number, type and
              location in your portfolio once it is assigned, with its lease agreement to accept and download.
            </p>
            <div className="max-w-md border border-border bg-surface">
              <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3">
                <p className="text-sm font-semibold">Example investor&apos;s portfolio</p>
                <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">Example</span>
              </div>
              <ul className="divide-y divide-border">
                {example.holdings.map((holding) => (
                  <li key={holding.number} className="space-y-1 px-5 py-4 text-sm">
                    <div className="flex items-center justify-between gap-4">
                      <p className="font-display font-semibold tracking-wide">{holding.number}</p>
                      <span className="rounded-full bg-gold/15 px-2.5 py-0.5 text-xs text-gold-text">On lease</span>
                    </div>
                    <p className="text-muted">{holding.type} · {holding.location}</p>
                    <p className="text-muted">Bought for {inr(holding.price)} · {holding.years}-year lease</p>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="relative isolate space-y-5 overflow-hidden bg-ink px-5 py-20 text-on-ink sm:px-10 lg:px-14 lg:py-28">
            <PortPhoto name="container-wall" alt="" sizes="(min-width: 768px) 50vw, 100vw"
              className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
            <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-br from-ink via-ink/85 to-ink/60" />
            <Eyebrow tone="light">Your Rent</Eyebrow>
            <h3 className="text-2xl font-semibold tracking-tight sm:text-3xl">Rent At Your Order&apos;s Rate</h3>
            <p className="max-w-md text-sm leading-relaxed text-on-ink/80">
              Each container is leased for the years you choose. The rate is set by your order&apos;s total, and rent is
              credited to your wallet {credit}. The price you paid is not paid back. Open to every verified investor.
            </p>
            <div className="max-w-md border border-on-ink/20">
              <div className="flex items-center justify-between gap-4 border-b border-on-ink/20 px-5 py-3">
                <p className="text-sm font-semibold">The same investor&apos;s rent</p>
                <span className="rounded-full border border-on-ink/30 px-3 py-1 text-xs text-on-ink/70">Example</span>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-on-ink/60">
                    <th scope="col" className="px-5 pt-3 pb-1 font-normal">Container</th>
                    <th scope="col" className="px-5 pt-3 pb-1 text-right font-normal">Rent a month</th>
                  </tr>
                </thead>
                <tbody>
                  {example.holdings.map((holding) => (
                    <tr key={holding.number}>
                      <td className="px-5 py-2 text-on-ink/80">{holding.type}</td>
                      <td className="px-5 py-2 text-right tabular-nums">{inr(holding.rent)}</td>
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
              Illustration: one {inr(example.total)} order earns {formatPercent(example.percent)} a month on each container.
              Each container&apos;s actual price and your rent are shown in the marketplace before you buy.
            </p>
          </div>
        </section>

        {/* -------------------------------------------------------------------- cta */}
        <section aria-labelledby="cta-heading" className="relative isolate mt-20 overflow-hidden bg-ink px-5 py-20 text-on-ink sm:px-10 lg:mt-28 lg:px-14 lg:py-28">
          <PortPhoto name="terminal-above" alt="" sizes="(min-width: 1440px) 1440px, 100vw"
            className="absolute inset-0 -z-20 size-full object-cover" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-ink/70" />
          <div className="mx-auto max-w-2xl space-y-6 text-center">
            <Eyebrow tone="light">Start Today</Eyebrow>
            <h2 id="cta-heading" className="text-3xl font-semibold tracking-tight sm:text-4xl lg:text-5xl">
              Own The Containers In The Stacks
            </h2>
            <p className="text-base leading-relaxed text-on-ink/85">
              Pick your containers, choose your lease, and earn rent credited to your wallet {credit}.
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <LinkButton href="/register">Start Investing</LinkButton>
              <a href="#contact" className="inline-flex h-11 items-center px-2 text-sm text-on-ink/90 underline-offset-4 hover:underline">
                Talk to us first
              </a>
            </div>
          </div>
        </section>

        {/* -------------------------------------------------------------------- faq */}
        <section id="faq" className="scroll-mt-8 px-5 py-20 sm:px-10 lg:px-14 lg:py-28">
          <div className="mx-auto max-w-3xl">
            <Eyebrow>FAQ</Eyebrow>
            <h2 className="mt-5 text-3xl font-semibold tracking-tight sm:text-4xl">Questions Investors Ask Us</h2>
            <div className="mt-10 divide-y divide-border border-y border-border">
              {faqs(terms, credit).map((faq) => (
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
        <section id="contact" className="relative grid scroll-mt-8 gap-10 border-t border-border px-5 py-20 sm:px-10 lg:grid-cols-[1fr_1.4fr] lg:px-14 lg:py-28">
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
