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

const TYPES = [{ value: "RETAIL", label: "Shared (retail)" }, { value: "HNI", label: "Standalone (HNI)" }];
const RISKS = [{ value: "LOW", label: "Low" }, { value: "MEDIUM", label: "Medium" }, { value: "HIGH", label: "High" }];
const STATUSES = [{ value: "OPEN", label: "Open for investment" }, { value: "FUNDED", label: "Fully funded" }];
const SORTS = [
  { value: "NEWEST", label: "Newest" },
  { value: "HIGHEST_YIELD", label: "Highest yield" },
  { value: "MOST_AVAILABLE", label: "Most available" },
  { value: "LOWEST_MINIMUM", label: "Lowest minimum" },
];

function pick(value: unknown, options: readonly { value: string }[]): string | undefined {
  return typeof value === "string" && options.some((o) => o.value === value) ? value : undefined;
}

export default async function MarketplacePage({ searchParams }: PageProps<"/marketplace">) {
  const params = await searchParams;
  const filters = {
    investmentType: pick(params.investmentType, TYPES),
    containerType: pick(params.containerType, CONTAINER_TYPE_OPTIONS) as ContainerType | undefined,
    riskLevel: pick(params.riskLevel, RISKS),
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
          Each offering is backed by a specific, identified shipping container on lease. Yields shown are expected
          gross rental yields and are not guaranteed.
        </p>
      </header>

      <form role="search" className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-6">
        <SelectField label="Type" name="investmentType" options={TYPES} placeholder="All" defaultValue={filters.investmentType ?? ""} />
        <SelectField label="Container" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="All"
          defaultValue={filters.containerType ?? ""} />
        <SelectField label="Risk" name="riskLevel" options={RISKS} placeholder="Any" defaultValue={filters.riskLevel ?? ""} />
        <SelectField label="Status" name="status" options={STATUSES} defaultValue={filters.status ?? "OPEN"} />
        <SelectField label="Sort by" name="sort" options={SORTS} defaultValue={filters.sort ?? "NEWEST"} />
        <Button type="submit">Apply</Button>
      </form>

      {listings.content.length === 0 ? (
        <EmptyState
          title="No offerings match these filters"
          description={filters.containerType ? `No ${CONTAINER_TYPE_LABEL[filters.containerType]} offerings right now.` : "New containers are listed regularly."}
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
