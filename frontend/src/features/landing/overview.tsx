import { PortPhoto } from "@/components/brand/port-photo";
import type { PlatformStats } from "@/lib/server/platform-stats";
import { formatMoney } from "@/utils/money";
import { Heading, Pill, Section, Stat } from "./landing-ui";

/**
 * The company's record from before this platform (since 2022). Each figure on the page is this number PLUS the live
 * total from the platform's books (GET /api/v1/public/stats), so it keeps growing on its own. Enter only REAL,
 * verifiable numbers; 0 adds nothing.
 */
const COMPANY_BASELINE = {
  /** Returns credited to investors before the platform, in crore. */
  creditedCrore: 12,
  investors: 1200,
  investments: 2000,
};

const CRORE_PAISE = 100n * 10_000_000n;
const LAKH_PAISE = 100n * 100_000n;

/** "₹12.4 Cr+", "₹8.5 L+" or the full amount below a lakh. Rounded down, so the "+" stays true. */
function compactRupees(paise: bigint): string {
  const tenths = (unit: bigint) => {
    const n = (paise * 10n) / unit;
    return n % 10n === 0n ? (n / 10n).toLocaleString("en-IN") : `${n / 10n}.${n % 10n}`;
  };
  if (paise >= CRORE_PAISE) return `₹${tenths(CRORE_PAISE)} Cr+`;
  if (paise >= LAKH_PAISE) return `₹${tenths(LAKH_PAISE)} L+`;
  return formatMoney({ amount: `${paise / 100n}.${(paise % 100n).toString().padStart(2, "0")}`, currency: "INR" });
}

/** 3500 → "3.5K+", 950 → "950+". */
function thousands(count: number): string {
  if (count < 1000) return `${count.toLocaleString("en-IN")}+`;
  return `${(Math.floor(count / 100) / 10).toLocaleString("en-IN")}K+`;
}

/** "142.30" → 14230n. */
function toPaise(amount: string): bigint {
  const [whole = "0", fraction = ""] = amount.split(".");
  return BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
}

/**
 * Baseline + live for each figure. Live "paidToInvestors" is returns credited to investors' wallets (not
 * withdrawals), and is labelled so. If the backend cannot be reached, the baseline alone is shown.
 */
function trackRecord(stats: PlatformStats | null) {
  const credited = BigInt(COMPANY_BASELINE.creditedCrore * 100) * CRORE_PAISE / 100n
    + (stats ? toPaise(stats.paidToInvestors.amount) : 0n);
  const investors = COMPANY_BASELINE.investors + (stats?.investors ?? 0);
  const investments = COMPANY_BASELINE.investments + (stats?.units ?? 0);
  return [
    { label: "Returns credited to investors", value: credited > 0n ? compactRupees(credited) : null },
    { label: "Investors", value: investors > 0 ? thousands(investors) : null },
    { label: "Investments made", value: investments > 0 ? thousands(investments) : null },
  ].filter((figure): figure is { label: string; value: string } => figure.value !== null);
}

/** Who SeaLease is and where returns come from, with the track record beside it. */
export function BusinessOverview({ stats }: { stats: PlatformStats | null }) {
  const figures = trackRecord(stats);
  return (
    <Section id="about" labelledBy="about-heading">
      <div className="grid gap-12 lg:grid-cols-[1fr_1.1fr] lg:items-center lg:gap-16">
        <div className="space-y-6">
          <Pill>About SeaLease</Pill>
          <Heading id="about-heading" lead="A Leasing Business" soft="You Can Invest In" />
          <div className="max-w-xl space-y-4 text-sm leading-relaxed text-muted sm:text-base">
            <p>
              SeaLease is run by Leasefleet Solutions Pvt. Ltd. Businesses lease shipping containers from us to move
              their goods, and the lease income is what pays our investors their returns.
            </p>
            <p>
              You do not need any leasing experience. You choose a plan, we run the business, and every return
              credited to you is recorded in our ledger and shown in your account.
            </p>
          </div>
        </div>

        <div className="relative isolate overflow-hidden rounded-[2rem] bg-ink text-on-ink shadow-2xl shadow-ink/25">
          <PortPhoto name="yard-stacks" alt="" sizes="(min-width: 1024px) 640px, 100vw"
            className="absolute inset-0 -z-20 size-full object-cover opacity-25" />
          <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-br from-ink via-ink/90 to-ink/70" />
          <div className="space-y-6 p-6 sm:p-10">
            <div className="space-y-2">
              <Pill tone="light">Since 2022</Pill>
              <p className="max-w-md text-sm leading-relaxed text-on-ink/75">
                Investors have been receiving returns from SeaLease since 2022.
                {figures.length > 0 && " The figures below cover our business since then and grow live as returns are credited."}
              </p>
            </div>
            {figures.length > 0 && (
              <div className="@container">
              <dl className={`grid gap-3 ${figures.length === 3 ? "@lg:grid-cols-3" : "@lg:grid-cols-2"}`}>
                {figures.map((figure) => <Stat key={figure.label} label={figure.label} value={figure.value} />)}
              </dl>
              </div>
            )}
          </div>
          <p className="border-t border-on-ink/10 px-6 py-4 text-xs leading-relaxed text-on-ink/60 sm:px-10">
            Past returns are not a guarantee of future returns.
          </p>
        </div>
      </div>
    </Section>
  );
}
