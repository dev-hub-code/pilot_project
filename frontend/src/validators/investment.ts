import { z } from "zod";

/** Client-side mirrors of the backend's container/plan validation; the backend is authoritative. */
const money = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");
const optionalMoney = z.preprocess((v) => (v === "" ? undefined : v), money.optional());
const optionalText = (max: number) => z.preprocess((v) => (typeof v === "string" && v.trim() === "" ? undefined : v), z.string().trim().max(max).optional());

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
  // The currency is fixed (rupees); it is only sent along with a cost.
  .transform((d) => ({ ...d, acquisitionCurrency: d.acquisitionCost === undefined ? undefined : d.acquisitionCurrency }));

export const productSchema = z.object({
  containerType: z.string().min(1, "Select the container type"),
  title: z.string().trim().min(1, "Enter a title").max(140),
  summary: z.string().trim().min(1, "Enter a one-line summary").max(400),
  description: z.string().trim().min(1, "Describe the plan").max(10_000),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  price: money,
  monthlyRentPercent: z.string().trim().regex(/^\d{1,2}(\.\d{1,2})?$/, "Enter a percentage with up to 2 decimals")
    .refine((v) => Number(v) > 0 && Number(v) <= 20, "Monthly rent must be above 0% and at most 20%"),
  tenureMonths: z.coerce.number({ message: "Enter the lease tenure in months" }).int("Enter whole months")
    .min(1, "The lease is at least 1 month").max(120, "The lease is at most 120 months"),
  riskDisclosure: z.string().trim().min(1, "Disclose the risks").max(10_000),
  termsAndConditions: z.string().trim().min(1, "Enter the terms").max(50_000),
});

export const statusSchema = z.object({
  status: z.enum(["AVAILABLE", "MAINTENANCE", "RETIRED"]),
  reason: z.string().trim().min(3, "Give a reason").max(500),
});

export const documentSchema = z.object({
  purpose: z.enum(["CONTAINER_PHOTO", "CONTAINER_SURVEY_REPORT", "LEASE_AGREEMENT", "INSURANCE_CERTIFICATE", "OFFERING_MEMORANDUM"]),
  title: z.string().trim().min(1, "Give the document a title").max(140),
});
