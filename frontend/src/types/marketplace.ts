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
  | "DRY_20FT" | "DRY_40FT" | "HIGH_CUBE_40FT" | "HIGH_CUBE_45FT" | "REEFER_20FT" | "REEFER_40FT"
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

/** A plan on the marketplace. Payout figures are per container. */
export interface MarketplaceListing {
  id: string;
  code: string;
  title: string;
  summary: string;
  status: ProductStatus;
  containerType: ContainerType;
  /** Per container, without GST (rent is based on it). */
  price: Money;
  gstRatePercent: number;
  /** What the investor pays per container: the price plus GST. */
  priceWithGst: Money;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayoutPercent: number;
  /** The investor chooses a lease of whole years between these. */
  minTenureYears: number;
  maxTenureYears: number;
  monthlyPayout: Money;
  /** The rent over the shortest lease. */
  totalPayout: Money;
  coverPhotoId: string | null;
}

export interface MarketplaceDetail {
  listing: MarketplaceListing;
  description: string;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  riskDisclosure: string;
  termsAndConditions: string;
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
  /** The price of the containers, without GST. */
  amount: Money;
  gstAmount: Money;
  /** What the investor pays: the price plus GST. */
  amountWithGst: Money;
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
  gstRatePercent: number;
  gstPerContainer: Money;
  /** What the investor pays per container: the price plus GST. */
  priceWithGst: Money;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayoutPercent: number;
  minTenureYears: number;
  maxTenureYears: number;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  monthlyPayout: Money;
  totalPayout: Money;
  riskDisclosure: string;
  termsAndConditions: string;
  /** Containers of the plan's type in stock now (shared by plans of that type). */
  containersSold: number;
  publishedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
}
