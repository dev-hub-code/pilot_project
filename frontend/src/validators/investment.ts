import { z } from "zod";

/** Client-side mirrors of the backend's container/offering validation; the backend is authoritative. */
const money = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");
const optionalMoney = z.preprocess((v) => (v === "" ? undefined : v), money.optional());
const optionalText = (max: number) => z.preprocess((v) => (typeof v === "string" && v.trim() === "" ? undefined : v), z.string().trim().max(max).optional());
/** datetime-local values, interpreted as UTC. */
const optionalUtc = z.preprocess(
  (v) => (typeof v === "string" && v !== "" ? `${v.length === 16 ? `${v}:00` : v}Z` : undefined),
  z.iso.datetime("Enter a valid date and time").optional(),
);

export const containerSchema = z.object({
  containerNumber: z.string().trim().regex(/^[A-Za-z]{3}[UJZujz][\s-]?\d{6}[\s-]?\d$/, "Format: ABCU 123456 7"),
  containerType: z.string().min(1, "Select a type"),
  condition: z.string().min(1, "Select a condition"),
  capacityCbm: z.coerce.number().positive("Enter the capacity").max(999999),
  maxGrossKg: z.coerce.number().int().positive("Enter the maximum gross weight"),
  tareKg: z.coerce.number().int().positive("Enter the tare weight"),
  manufactureYear: z.coerce.number().int().min(1960).max(2100),
  manufacturer: optionalText(100),
  currentLocation: z.string().trim().min(1, "Where is the container?").max(120),
  locationCountry: z.string().regex(/^[A-Z]{2}$/, "Select a country"),
  acquisitionCost: optionalMoney,
  acquisitionCurrency: z.preprocess((v) => (v === "" ? undefined : v), z.string().regex(/^[A-Z]{3}$/).optional()),
  notes: optionalText(1000),
}).refine((d) => d.tareKg < d.maxGrossKg, { path: ["tareKg"], message: "Tare must be below the maximum gross weight" })
  .refine((d) => (d.acquisitionCost === undefined) === (d.acquisitionCurrency === undefined), {
    path: ["acquisitionCurrency"],
    message: "Give the cost and its currency together",
  });

export const productSchema = z.object({
  containerId: z.uuid("Select a container"),
  investmentType: z.enum(["RETAIL", "HNI"]),
  title: z.string().trim().min(1, "Enter a title").max(140),
  summary: z.string().trim().min(1, "Enter a one-line summary").max(400),
  description: z.string().trim().min(1, "Describe the investment").max(10_000),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  totalAmount: money,
  minimumInvestment: optionalMoney,
  investmentIncrement: optionalMoney,
  maximumPerInvestor: optionalMoney,
  expectedRentalAmount: money,
  rentalFrequency: z.enum(["MONTHLY", "QUARTERLY"]),
  durationMonths: z.coerce.number().int().min(1).max(360),
  lesseeName: optionalText(140),
  riskLevel: z.enum(["LOW", "MEDIUM", "HIGH"]),
  riskDisclosure: z.string().trim().min(1, "Disclose the risks").max(10_000),
  termsAndConditions: z.string().trim().min(1, "Enter the terms").max(50_000),
  termsVersion: z.string().trim().min(1, "Version the terms").max(20),
  offerOpensAt: optionalUtc,
  offerClosesAt: optionalUtc,
}).refine((d) => d.investmentType === "HNI" || (d.minimumInvestment && d.investmentIncrement), {
  path: ["minimumInvestment"],
  message: "Shared offerings need a minimum and an increment",
});

export const statusSchema = z.object({
  status: z.enum(["AVAILABLE", "ON_LEASE", "MAINTENANCE", "RETIRED"]),
  reason: z.string().trim().min(3, "Give a reason").max(500),
});

export const documentSchema = z.object({
  purpose: z.enum(["CONTAINER_PHOTO", "CONTAINER_SURVEY_REPORT", "LEASE_AGREEMENT", "INSURANCE_CERTIFICATE", "OFFERING_MEMORANDUM"]),
  title: z.string().trim().min(1, "Give the document a title").max(140),
});
