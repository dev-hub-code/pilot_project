import type { Metadata } from "next";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { CONTAINER_TYPE_LABEL, CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { ListingCard } from "@/features/marketplace/listing-card";
import { PublicFrame } from "@/features/marketplace/public-frame";
import { openListings, openPhotoSrc } from "@/lib/server/open-marketplace";
import type { ContainerType } from "@/types/marketplace";

export const metadata: Metadata = {
  title: "Marketplace",
  description: "Shipping containers on sale at SeaLease: price per container and lease years. Sign in to invest.",
};

const STATUSES = [{ value: "OPEN", label: "Open for investment" }, { value: "CLOSED", label: "Closed" }];
const SORTS = [
  { value: "NEWEST", label: "Newest" },
  { value: "LOWEST_PRICE", label: "Lowest price" },
];

function pick(value: unknown, options: readonly { value: string }[]): string | undefined {
  return typeof value === "string" && options.some((o) => o.value === value) ? value : undefined;
}

/**
 * The marketplace for visitors who are not signed in (they are sent here from /marketplace): the same listings,
 * read-only, from the open API. Signing in leads to the full marketplace, where containers are bought.
 */
export default async function PublicMarketplacePage({ searchParams }: PageProps<"/containers">) {
  const params = await searchParams;
  const filters = {
    containerType: pick(params.containerType, CONTAINER_TYPE_OPTIONS) as ContainerType | undefined,
    status: pick(params.status, STATUSES),
    sort: pick(params.sort, SORTS),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "12" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const listings = await openListings(query).catch(() => null);

  return (
    <PublicFrame next="/marketplace">
      <div className="space-y-10">
        <header className="space-y-4">
          <Eyebrow>Marketplace</Eyebrow>
          <h1 className="max-w-2xl text-4xl leading-tight font-bold tracking-tight sm:text-5xl">
            Container Investment Opportunities
          </h1>
          <p className="max-w-2xl text-muted">
            Buy whole shipping containers. Once your payment is confirmed, we assign each container you bought its unique
            number and lease it out for the years you chose: every month of the lease you receive rent. Sign in or
            register to invest.
          </p>
        </header>

        <form role="search" className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
          <SelectField label="Container" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="All"
            defaultValue={filters.containerType ?? ""} />
          <SelectField label="Status" name="status" options={STATUSES} defaultValue={filters.status ?? "OPEN"} />
          <SelectField label="Sort by" name="sort" options={SORTS} defaultValue={filters.sort ?? "NEWEST"} />
          <Button type="submit">Apply</Button>
        </form>

        {listings === null ? (
          <EmptyState title="The marketplace could not be loaded" description="Please try again in a moment." />
        ) : listings.content.length === 0 ? (
          <EmptyState
            title="No containers match these filters"
            description={filters.containerType ? `No ${CONTAINER_TYPE_LABEL[filters.containerType]} containers right now.` : "New containers are listed regularly."}
          />
        ) : (
          <ul className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
            {listings.content.map((listing) => (
              <li key={listing.code} className="flex">
                <ListingCard href={`/containers/${encodeURIComponent(listing.code)}`}
                  photoSrc={listing.photoIds[0] ? openPhotoSrc(listing.code, listing.photoIds[0]) : undefined}
                  listing={{ ...listing, id: listing.code, coverPhotoId: listing.photoIds[0] ?? null }} />
              </li>
            ))}
          </ul>
        )}
        {listings && <Pagination page={listings} basePath="/containers" params={filters} />}
      </div>
    </PublicFrame>
  );
}
