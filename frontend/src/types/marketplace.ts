/** Mirrors of the backend's container, investment plan and marketplace DTOs. */

/** Amounts are decimal strings at the currency's minor unit ("50000.00"): never parse to float for maths. */
export interface Money {
  amount: string;
  currency: string;
}

export type ProductStatus = "DRAFT" | "OPEN" | "CLOSED" | "CANCELLED";
/** RESERVED: held for an order awaiting payment. ON_LEASE: allocated to an investor. */
export type ContainerStatus = "AVAILABLE" | "RESERVED" | "ON_LEASE" | "MAINTENANCE" | "RETIRED";
export type ContainerCondition = "NEW" | "CARGO_WORTHY" | "WIND_WATERTIGHT";
export type ContainerType =
  | "DRY_20FT" | "DRY_40FT" | "HIGH_CUBE_40FT" | "HIGH_CUBE_45FT" | "REEFER_20FT" | "REEFER_40FT"
  | "OPEN_TOP_20FT" | "OPEN_TOP_40FT" | "FLAT_RACK_20FT" | "FLAT_RACK_40FT" | "TANK_20FT";
export type ContainerDocumentPurpose =
  | "CONTAINER_PHOTO" | "CONTAINER_SURVEY_REPORT" | "LEASE_AGREEMENT" | "INSURANCE_CERTIFICATE" | "OFFERING_MEMORANDUM";

export interface ContainerSummary {
  id: string;
  containerNumber: string;
  containerType: ContainerType;
  condition: ContainerCondition;
  status: ContainerStatus;
  capacityCbm: number;
  maxGrossKg: number;
  tareKg: number;
  manufactureYear: number;
  manufacturer: string | null;
  currentLocation: string;
  locationCountry: string;
}

export interface ContainerDocument {
  documentId: string;
  purpose: ContainerDocumentPurpose;
  title: string;
  visibleToInvestors: boolean;
  uploadedAt: string;
}

export interface ContainerDetail {
  container: ContainerSummary;
  acquisitionCost: number | null;
  acquisitionCurrency: string | null;
  notes: string | null;
  statusReason: string | null;
  createdAt: string;
  version: number;
  documents: ContainerDocument[];
}

/** A plan on the marketplace. Payout figures are per container. */
export interface MarketplaceListing {
  id: string;
  code: string;
  title: string;
  summary: string;
  status: ProductStatus;
  containerType: ContainerType;
  price: Money;
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayoutPercent: number;
  tenureMonths: number;
  monthlyPayout: Money;
  totalPayout: Money;
  availableContainers: number;
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
  amount: Money;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  monthlyPayout: Money;
  payouts: number;
  totalRent: Money;
  totalCapitalReturned: Money;
  totalPayout: Money;
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
  monthlyRentPercent: number;
  monthlyCapitalReturnPercent: number;
  monthlyPayoutPercent: number;
  tenureMonths: number;
  monthlyRent: Money;
  monthlyCapitalReturn: Money;
  monthlyPayout: Money;
  totalPayout: Money;
  riskDisclosure: string;
  termsAndConditions: string;
  /** Containers of the plan's type in stock now (shared by plans of that type). */
  availableContainers: number;
  containersSold: number;
  publishedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
}
