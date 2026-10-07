import { z } from "zod";

/** Client-side mirror of the backend's rate validation; the backend is authoritative. */
const percent = z.string().trim().regex(/^\d{1,2}(\.\d{1,3})?$/, "Up to 3 decimals")
  .refine((v) => Number(v) <= 10, "At most 10%");

export const scheduleRatesSchema = z.object({
  level1: percent,
  level2: percent,
  level3: percent,
  level4: percent,
  /** datetime-local input, read as UTC; empty means "now". */
  effectiveFrom: z.string().trim().optional()
    .transform((v) => (v ? `${v.length === 16 ? `${v}:00` : v}Z` : undefined))
    .pipe(z.iso.datetime("Enter a valid date and time").optional()),
  reason: z.string().trim().min(3, "Give a reason").max(500),
}).refine((d) => [d.level1, d.level2, d.level3, d.level4].reduce((sum, v) => sum + Number(v), 0) <= 20, {
  path: ["level1"],
  message: "The four levels together cannot exceed 20%",
});
