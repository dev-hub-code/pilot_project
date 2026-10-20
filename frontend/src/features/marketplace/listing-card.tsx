import Link from "next/link";
import { ContainerScene } from "@/components/brand/container-scene";
import { yearsLabel } from "@/features/orders/lease-years";
import type { MarketplaceListing } from "@/types/marketplace";
import { formatMoney } from "@/utils/money";
import { CONTAINER_TYPE_LABEL } from "./labels";

/** The whole card opens the container: its title's link is stretched over it (one link for keyboards and readers). */
export function ListingCard({ listing }: { listing: MarketplaceListing }) {
  const href = `/marketplace/${listing.id}`;
  return (
    <article className="group flex flex-col border border-border bg-surface transition-colors hover:border-foreground/40 has-focus-visible:border-gold">
      <div className="relative block aspect-[16/10] overflow-hidden bg-ink">
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
      </div>

      <div className="flex flex-1 flex-col gap-5 p-5">
        <div className="space-y-1.5">
          <h3 className="text-lg leading-snug font-semibold tracking-tight">
            <Link href={href}
              className="card-link group-hover:text-gold-text">
              {listing.title}
            </Link>
          </h3>
          <p className="text-sm text-muted">{listing.summary}</p>
        </div>

        <dl className="grid grid-cols-[repeat(auto-fit,minmax(9.5rem,1fr))] gap-x-4 gap-y-3 text-sm">
          <Stat label="Price per container" value={formatMoney(listing.price)} emphasis />
          <Stat label="Minimum lease" value={yearsLabel(listing.minTenureYears)} />
        </dl>
        <p className="text-xs text-muted">
          Monthly rent is set by your order&apos;s total value; see it for your order on the container&apos;s page.
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
