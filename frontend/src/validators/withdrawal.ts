import { z } from "zod";

/** Client-side mirrors of the backend's withdrawal validation; the backend is authoritative. */

export const referralModeSchema = z.object({ mode: z.enum(["BANK_TRANSFER", "OFFICE_CASH"]) });

export const financeTaskSchema = z.object({
  outcome: z.enum(["PAID", "FAILED"]),
  reference: z.string().trim().max(100).optional().transform((v) => v || undefined),
  reason: z.string().trim().max(500).optional().transform((v) => v || undefined),
});

export const resolveSchema = z.object({
  outcome: z.enum(["PAID", "RETURNED"]),
  note: z.string().trim().max(200).optional().transform((v) => v || undefined),
});


/** Rupees to the paisa, at least ₹1. */
export const withdrawalMinimumSchema = z.object({
  minimumAmount: z.string().trim()
    .regex(/^\d{1,8}(\.\d{1,2})?$/, "Enter an amount in rupees, up to 2 decimals")
    .refine((v) => Number(v) >= 1, "The minimum must be at least ₹1")
    .refine((v) => Number(v) <= 10_000_000, "The minimum can be at most ₹1,00,00,000"),
});
