import Link from "next/link";
import { ContainerScene } from "@/components/brand/container-scene";
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
          <Stat label="Monthly payout" value={formatPercent(listing.monthlyPayoutPercent)} emphasis />
          <Stat label="Price per container" value={formatMoney(listing.price)} />
          <Stat label="Paid monthly" value={formatMoney(listing.monthlyPayout)} />
          <Stat label="Tenure" value={`${listing.tenureMonths} months`} />
        </dl>
        <p className="text-xs text-muted">
          {formatPercent(listing.monthlyRentPercent)} rent + {formatPercent(listing.monthlyCapitalReturnPercent)} of your capital back, every month.
        </p>

        <div className="mt-auto flex items-center justify-between text-xs text-muted">
          <span>
            <span className="font-medium text-foreground">{listing.availableContainers}</span>{" "}
            container{listing.availableContainers === 1 ? "" : "s"} available
          </span>
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
