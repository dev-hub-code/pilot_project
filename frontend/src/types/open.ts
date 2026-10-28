import type { ContainerType, Money, ProductStatus } from "@/types/marketplace";

/** A listing as the open API returns it (backend /api/v1/open): published terms and photos only. */
export interface OpenListing {
  code: string;
  title: string;
  summary: string;
  /** Null in lists. */
  description: string | null;
  status: ProductStatus;
  containerType: ContainerType;
  containerTypeLabel: string;
  price: Money;
  minTenureYears: number;
  maxTenureYears: number;
  /** The cover only, in lists. */
  photoIds: string[];
}
