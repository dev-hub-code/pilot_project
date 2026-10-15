import Link from "next/link";
import { ContainerScene } from "@/components/brand/container-scene";
import { yearsLabel } from "@/features/orders/lease-years";
import type { MarketplaceListing } from "@/types/marketplace";
import { formatMoney, formatPercent } from "@/utils/money";
import { CONTAINER_TYPE_LABEL } from "./labels";

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
          {CONTAINER_TYPE_LABEL[listing.containerType]}
        </span>
      </Link>

      <div className="flex flex-1 flex-col gap-5 p-5">
        <div className="space-y-1.5">
          <p className="font-mono text-xs text-muted">{listing.code}</p>
          <h3 className="text-lg leading-snug font-semibold tracking-tight">
            <Link href={href} className="hover:text-gold-text">{listing.title}</Link>
          </h3>
          <p className="text-sm text-muted">{listing.summary}</p>
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
          <Stat label="Monthly rent" value={formatPercent(listing.monthlyRentPercent)} emphasis />
          <Stat label={Number(listing.gstRatePercent) > 0 ? "Price (before GST)" : "Price per container"}
            value={formatMoney(listing.price)}
            note={Number(listing.gstRatePercent) > 0 ? `${formatMoney(listing.priceWithGst)} with ${formatPercent(listing.gstRatePercent)} GST` : undefined} />
          <Stat label="Rent paid monthly" value={formatMoney(listing.monthlyPayout)}
            note={`${formatPercent(listing.monthlyRentPercent)} of ${formatMoney(listing.price)}`} />
          <Stat label="Minimum lease" value={yearsLabel(listing.minTenureYears)} />
        </dl>
        <p className="text-xs text-muted">
          {formatPercent(listing.monthlyRentPercent)} of the price ({formatMoney(listing.price)}, before GST) paid to you as rent
          every month of the lease.
        </p>

        <div className="mt-auto flex items-center justify-between text-xs text-muted">
          <span>Lease of {listing.minTenureYears === listing.maxTenureYears ? yearsLabel(listing.minTenureYears)
            : `${listing.minTenureYears}–${listing.maxTenureYears} years`}, your choice · container assigned after payment</span>
        </div>
      </div>
    </article>
  );
}

function Stat({ label, value, emphasis, note }: { label: string; value: string; emphasis?: boolean; note?: string }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className={emphasis ? "font-display text-xl font-semibold text-gold-text" : "font-medium"}>{value}</dd>
      {note && <dd className="text-xs text-muted">{note}</dd>}
    </div>
  );
}
