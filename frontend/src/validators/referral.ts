import { z } from "zod";
import { zonedInputToIso } from "@/lib/time-zone";

/** Client-side mirror of the backend's rate validation; the backend is authoritative. */
const percent = z.string().trim().regex(/^\d{1,2}(\.\d{1,3})?$/, "Up to 3 decimals")
  .refine((v) => Number(v) <= 10, "At most 10%");

export const scheduleRatesSchema = z.object({
  level1: percent,
  level2: percent,
  level3: percent,
  level4: percent,
  /** datetime-local input, read in the display time zone; empty means "now". */
  effectiveFrom: z.string().trim().optional()
    .transform((v) => (v ? zonedInputToIso(v) : undefined))
    .pipe(z.iso.datetime("Enter a valid date and time").optional()),
  reason: z.string().trim().min(3, "Give a reason").max(500),
}).refine((d) => [d.level1, d.level2, d.level3, d.level4].reduce((sum, v) => sum + Number(v), 0) <= 20, {
  path: ["level1"],
  message: "The four levels together cannot exceed 20%",
});

/** Value of the "new referrer" choice that takes the member out of their downline. */
export const NO_REFERRER = "none";

/** Moving a downline member to another referrer, or out of the downline (super administrators); the backend checks the hierarchy. */
export const moveReferralSchema = z.object({
  memberId: z.uuid(),
  referrerId: z.union([z.uuid(), z.literal(NO_REFERRER)], "Choose the new referrer"),
  reason: z.string().trim().min(3, "Give a reason").max(500),
});
