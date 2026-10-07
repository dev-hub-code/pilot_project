import Link from "next/link";
import { ContainerScene } from "@/components/brand/container-scene";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import type { MarketplaceListing } from "@/types/marketplace";
import { formatMoney, formatPercent, FREQUENCY_LABEL } from "@/utils/money";
import { CONTAINER_TYPE_LABEL, RISK_LABEL, RISK_TONE } from "./labels";

export function ListingCard({ listing }: { listing: MarketplaceListing }) {
  const href = `/marketplace/${listing.id}`;
  return (
    <article className="group flex flex-col border border-border bg-surface transition-colors hover:border-foreground/40">
      <Link href={href} className="relative block aspect-[16/10] overflow-hidden bg-ink" tabIndex={-1} aria-hidden="true">
        {listing.coverPhotoId ? (
          // eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP
          <img
            src={`${href}/documents/${listing.coverPhotoId}`}
            alt=""
            loading="lazy"
            className="size-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
          />
        ) : (
          <ContainerScene focus="side" className="size-full" />
        )}
        <span className="absolute top-3 left-3 bg-ink/80 px-2.5 py-1 text-[11px] tracking-[0.12em] text-on-ink uppercase">
          {listing.investmentType === "HNI" ? "Standalone · HNI" : "Shared · Retail"}
        </span>
      </Link>

      <div className="flex flex-1 flex-col gap-5 p-5">
        <div className="space-y-1.5">
          <p className="font-mono text-xs text-muted">{listing.code}</p>
          <h3 className="text-lg leading-snug font-semibold tracking-tight">
            <Link href={href} className="hover:text-gold-text">{listing.title}</Link>
          </h3>
          <p className="text-sm text-muted">
            {CONTAINER_TYPE_LABEL[listing.container.containerType]} · {listing.container.currentLocation}
          </p>
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
          <Stat label="Expected yield" value={formatPercent(listing.expectedAnnualReturnPercent)} emphasis />
          <Stat label="Rental" value={`${formatMoney(listing.expectedRentalAmount)}/${FREQUENCY_LABEL[listing.rentalFrequency]}`} />
          <Stat label="Minimum" value={formatMoney(listing.minimumInvestment)} />
          <Stat label="Term" value={`${listing.durationMonths} months`} />
        </dl>

        <div className="mt-auto space-y-2">
          <ProgressBar percent={listing.capacity.fundedPercent} label={`${listing.code} funding progress`} />
          <div className="flex items-center justify-between text-xs text-muted">
            <span>
              <span className="font-medium text-foreground">{formatMoney(listing.capacity.available, { compact: true })}</span>{" "}
              available of {formatMoney(listing.price, { compact: true })}
            </span>
            <StatusBadge tone={RISK_TONE[listing.riskLevel]}>{RISK_LABEL[listing.riskLevel]}</StatusBadge>
          </div>
        </div>
      </div>
    </article>
  );
}

function Stat({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className={emphasis ? "font-display text-xl font-semibold text-gold-text" : "font-medium"}>{value}</dd>
    </div>
  );
}
