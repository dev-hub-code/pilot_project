import { z } from "zod";

/** Client-side mirror of the backend's plan validation; the backend is authoritative. */

export const productSchema = z.object({
  audience: z.enum(["ONLINE", "NETWORK"], { message: "Choose who the plan is for" }),
  title: z.string().trim().min(1, "Enter the plan's name").max(140),
  summary: z.string().trim().min(1, "Enter a one-line summary").max(400),
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
  tenureYears: z.coerce.number({ message: "Enter the tenure in years" }).int("Enter whole years")
    .min(1, "The tenure is at least 1 year").max(30, "The tenure is at most 30 years"),
  annualReturnPercent: z.string().trim().regex(/^\d{1,3}(\.\d{1,2})?$/, "Enter a percentage with up to 2 decimals")
    .refine((v) => Number(v) > 0 && Number(v) <= 100, "Enter a percentage above 0, up to 100"),
  minUnits: z.coerce.number({ message: "Enter the minimum units" }).int("Enter whole units")
    .min(1, "The minimum is at least 1 unit").max(1_000_000, "The minimum is at most 10,00,000 units"),
});
