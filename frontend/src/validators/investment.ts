import { z } from "zod";

/** Client-side mirror of the backend's plan validation; the backend is authoritative. */
const money = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

export const productSchema = z.object({
  containerType: z.string().min(1, "Select the container type"),
  /** Blank: the listing is named after its container type, e.g. "20ft Dry container". */
  title: z.string().trim().max(140),
  summary: z.string().trim().min(1, "Enter a one-line summary").max(400),
  description: z.string().trim().min(1, "Describe the container").max(10_000),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  price: money,
  minTenureYears: z.coerce.number({ message: "Enter the shortest lease in years" }).int("Enter whole years")
    .min(1, "The shortest lease is at least 1 year").max(30, "The shortest lease is at most 30 years"),
  maxTenureYears: z.coerce.number({ message: "Enter the longest lease in years" }).int("Enter whole years")
    .min(1, "The longest lease is at least 1 year").max(30, "The longest lease is at most 30 years"),
  riskDisclosure: z.string().trim().min(1, "Disclose the risks").max(10_000),
  termsAndConditions: z.string().trim().min(1, "Enter the terms").max(50_000),
}).refine((p) => p.maxTenureYears >= p.minTenureYears, {
  path: ["maxTenureYears"],
  message: "The longest lease must be at least the shortest",
});
