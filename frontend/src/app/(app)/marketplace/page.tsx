import type { Metadata } from "next";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { CONTAINER_TYPE_LABEL, CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { ListingCard } from "@/features/marketplace/listing-card";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ContainerType, MarketplaceListing } from "@/types/marketplace";

export const metadata: Metadata = { title: "Marketplace" };

const STATUSES = [{ value: "OPEN", label: "Open for investment" }, { value: "CLOSED", label: "Closed" }];
const SORTS = [
  { value: "NEWEST", label: "Newest" },
  { value: "HIGHEST_RETURN", label: "Highest rent" },
  { value: "LOWEST_PRICE", label: "Lowest price" },
];

function pick(value: unknown, options: readonly { value: string }[]): string | undefined {
  return typeof value === "string" && options.some((o) => o.value === value) ? value : undefined;
}

export default async function MarketplacePage({ searchParams }: PageProps<"/marketplace">) {
  const params = await searchParams;
  const filters = {
    containerType: pick(params.containerType, CONTAINER_TYPE_OPTIONS) as ContainerType | undefined,
    status: pick(params.status, STATUSES),
    sort: pick(params.sort, SORTS),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "12" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const listings = await authFetch<PageResponse<MarketplaceListing>>(`/api/v1/marketplace?${query}`);

  return (
    <div className="space-y-10">
      <header className="space-y-4">
        <Eyebrow>Marketplace</Eyebrow>
        <h1 className="max-w-2xl text-4xl leading-tight font-bold tracking-tight sm:text-5xl">
          Container Investment Opportunities
        </h1>
        <p className="max-w-2xl text-muted">
          Buy whole shipping containers under a plan. Once your payment is confirmed, a container is assigned to you by
          its unique number and leased for the plan&apos;s tenure: every month you receive rent plus part of your capital back.
        </p>
      </header>

      <form role="search" className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
        <SelectField label="Container" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="All"
          defaultValue={filters.containerType ?? ""} />
        <SelectField label="Status" name="status" options={STATUSES} defaultValue={filters.status ?? "OPEN"} />
        <SelectField label="Sort by" name="sort" options={SORTS} defaultValue={filters.sort ?? "NEWEST"} />
        <Button type="submit">Apply</Button>
      </form>

      {listings.content.length === 0 ? (
        <EmptyState
          title="No plans match these filters"
          description={filters.containerType ? `No ${CONTAINER_TYPE_LABEL[filters.containerType]} plans right now.` : "New plans are listed regularly."}
        />
      ) : (
        <ul className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          {listings.content.map((listing) => (
            <li key={listing.id} className="flex">
              <ListingCard listing={listing} />
            </li>
          ))}
        </ul>
      )}
      <Pagination page={listings} basePath="/marketplace" params={filters} />
    </div>
  );
}
