/** Mirrors of the backend's Phase 4 DTOs (container, investment and marketplace modules). */

/** Amounts are decimal strings at the currency's minor unit ("50000.00"): never parse to float for maths. */
export interface Money {
  amount: string;
  currency: string;
}

export type InvestmentType = "RETAIL" | "HNI";
export type ProductStatus = "DRAFT" | "OPEN" | "FUNDED" | "ACTIVE" | "MATURED" | "CLOSED" | "CANCELLED";
export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type RentalFrequency = "MONTHLY" | "QUARTERLY";
export type ContainerStatus = "AVAILABLE" | "ON_LEASE" | "MAINTENANCE" | "RETIRED";
export type ContainerCondition = "NEW" | "CARGO_WORTHY" | "WIND_WATERTIGHT";
export type ContainerType =
  | "DRY_20FT" | "DRY_40FT" | "HIGH_CUBE_40FT" | "HIGH_CUBE_45FT" | "REEFER_20FT" | "REEFER_40FT"
  | "OPEN_TOP_20FT" | "OPEN_TOP_40FT" | "FLAT_RACK_20FT" | "FLAT_RACK_40FT" | "TANK_20FT";
export type ContainerDocumentPurpose =
  | "CONTAINER_PHOTO" | "CONTAINER_SURVEY_REPORT" | "LEASE_AGREEMENT" | "INSURANCE_CERTIFICATE" | "OFFERING_MEMORANDUM";

export interface CapacityView {
  total: Money;
  committed: Money;
  reserved: Money;
  available: Money;
  fundedPercent: number;
}

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

export interface MarketplaceListing {
  id: string;
  code: string;
  title: string;
  summary: string;
  investmentType: InvestmentType;
  status: ProductStatus;
  price: Money;
  minimumInvestment: Money;
  expectedRentalAmount: Money;
  rentalFrequency: RentalFrequency;
  expectedAnnualReturnPercent: number;
  durationMonths: number;
  riskLevel: RiskLevel;
  capacity: CapacityView;
  container: ContainerSummary;
  coverPhotoId: string | null;
  offerClosesAt: string | null;
}

export interface MarketplaceDetail {
  listing: MarketplaceListing;
  description: string;
  investmentIncrement: Money;
  maximumPerInvestor: Money | null;
  lesseeName: string | null;
  riskDisclosure: string;
  termsAndConditions: string;
  termsVersion: string;
  offerOpensAt: string | null;
  documents: ContainerDocument[];
  eligibility: { eligible: boolean; reasons: string[] };
}

export interface ReturnProjection {
  amount: Money;
  ownershipPercent: number;
  rentalPerPayment: Money;
  expectedAnnualIncome: Money;
  paymentsOverTerm: number;
  expectedIncomeOverTerm: Money;
  valid: boolean;
  problems: string[];
}

export interface Product {
  id: string;
  code: string;
  status: ProductStatus;
  investmentType: InvestmentType;
  title: string;
  summary: string;
  description: string;
  price: Money;
  minimumInvestment: Money;
  investmentIncrement: Money;
  maximumPerInvestor: Money | null;
  expectedRentalAmount: Money;
  rentalFrequency: RentalFrequency;
  expectedAnnualReturnPercent: number;
  durationMonths: number;
  lesseeName: string | null;
  riskLevel: RiskLevel;
  riskDisclosure: string;
  termsAndConditions: string;
  termsVersion: string;
  offerOpensAt: string | null;
  offerClosesAt: string | null;
  capacity: CapacityView;
  container: ContainerSummary;
  publishedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
}

export interface CapacityMovement {
  id: string;
  type: "RESERVE" | "RELEASE" | "COMMIT";
  amount: number;
  reference: string;
  investorUserId: string;
  reservedAfter: number;
  committedAfter: number;
  createdAt: string;
}
