/** Mirrors of the backend's container, investment plan and marketplace DTOs. */

/** Amounts are decimal strings at the currency's minor unit ("50000.00"): never parse to float for maths. */
export interface Money {
  amount: string;
  currency: string;
}

export type ProductStatus = "DRAFT" | "OPEN" | "CLOSED" | "CANCELLED";
/** ON_LEASE: assigned to an investor. RESERVED: held by an order placed before manual assignment. */
export type ContainerStatus = "AVAILABLE" | "RESERVED" | "ON_LEASE" | "MAINTENANCE" | "RETIRED";
export type ContainerCondition = "NEW" | "CARGO_WORTHY" | "WIND_WATERTIGHT";
export type ContainerType =
  | "DRY_10FT" | "DRY_20FT" | "DRY_40FT" | "HIGH_CUBE_40FT" | "HIGH_CUBE_45FT" | "REEFER_20FT" | "REEFER_40FT"
  | "OPEN_TOP_20FT" | "OPEN_TOP_40FT" | "FLAT_RACK_20FT" | "FLAT_RACK_40FT" | "TANK_20FT";

/** A container as finance recorded it when assigning it; details beyond number and type may be unknown. */
export interface ContainerSummary {
  id: string;
  containerNumber: string;
  containerType: ContainerType;
  condition: ContainerCondition | null;
  status: ContainerStatus;
  capacityCbm: number | null;
  maxGrossKg: number | null;
  tareKg: number | null;
  manufactureYear: number | null;
  manufacturer: string | null;
  currentLocation: string | null;
  locationCountry: string | null;
}

/** A plan's marketplace photo; the image is served by its document id. */
export interface PlanPhoto {
  id: string;
  documentId: string;
  createdAt: string;
}

/** A container listing on the marketplace. No rent: it is set by each order's total value. */
export interface MarketplaceListing {
  id: string;
  code: string;
  title: string;
  summary: string;
  status: ProductStatus;
  containerType: ContainerType;
  /** Per container (rent is based on it). */
  price: Money;
  /** The investor chooses a lease of whole years between these. */
  minTenureYears: number;
  maxTenureYears: number;
  coverPhotoId: string | null;
}

export interface MarketplaceDetail {
  listing: MarketplaceListing;
  description: string;
  /** Photos of containers of the plan's type; the container allocated may differ. */
  photoIds: string[];
  eligibility: { eligible: boolean; reasons: string[] };
}

export interface ReturnProjection {
  containers: number;
  /** The lease the figures are for, in whole years. */
  tenureYears: number;
  minTenureYears: number;
  maxTenureYears: number;
  /** The price of the containers: what the investor pays. */
  amount: Money;
  /** The rent rate for an order of this amount (rent by order value). */
  monthlyRentPercent: number;
  monthlyRent: Money;
  /** Monthly payouts over the lease. */
  payouts: number;
  /** The rent over the whole lease (plans pay rent only). */
  totalRent: Money;
  valid: boolean;
  problems: string[];
}

/** An investment plan, as staff see it. Payout figures are per container. */
export interface Product {
  id: string;
  code: string;
  status: ProductStatus;
  containerType: ContainerType;
  title: string;
  summary: string;
  description: string;
  price: Money;
  minTenureYears: number;
  maxTenureYears: number;
  /** Containers of the plan's type in stock now (shared by plans of that type). */
  containersSold: number;
  publishedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
}

/** One band of the monthly rent by order value: orders from {@link fromAmount} to {@link toAmount} (inclusive). */
export interface RentTier {
  fromAmount: Money;
  /** The highest order total of the band; null for no upper limit. */
  toAmount: Money | null;
  /** Rent paid every month, as a percentage of each container's price. */
  monthlyRentPercent: number;
}

export interface RentTiers {
  tiers: RentTier[];
  updatedAt: string | null;
  updatedByName: string | null;
}

/** The smallest total an order may have; zero when there is none. */
export interface MinimumOrder {
  amount: Money;
  updatedAt: string | null;
  updatedByName: string | null;
}
