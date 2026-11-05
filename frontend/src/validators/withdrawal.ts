import { z } from "zod";

/** Client-side mirrors of the backend's withdrawal validation; the backend is authoritative. */

export const referralModeSchema = z.object({ mode: z.enum(["BANK_TRANSFER", "OFFICE_CASH"]) });

/** Only the amount is sent: charges, tax and what is paid are worked out again by the backend. */
export const withdrawalRequestSchema = z.object({
  balance: z.enum(["RENT_BANK", "RENT_CASH", "REFERRAL"]),
  amount: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount in rupees, up to 2 decimals")
    .refine((v) => Number(v) > 0, "Enter an amount to withdraw"),
  bankAccountId: z.string().uuid().optional().or(z.literal("").transform(() => undefined)),
  password: z.string().min(1, "Enter your password to confirm").max(200),
  confirmed: z.literal("on", { message: "Confirm the details are correct" }),
});

export const withdrawalReasonSchema = z.object({ reason: z.string().trim().min(1, "Give a reason").max(1000) });

export const withdrawalRespondSchema = z.object({ message: z.string().trim().min(1, "Write your answer").max(1000) });

export const financeTaskSchema = z.object({
  outcome: z.enum(["PAID", "FAILED"]),
  reference: z.string().trim().max(100).optional().transform((v) => v || undefined),
  reason: z.string().trim().max(500).optional().transform((v) => v || undefined),
});

export const resolveSchema = z.object({
  outcome: z.enum(["PAID", "RETURNED"]),
  note: z.string().trim().max(200).optional().transform((v) => v || undefined),
});



const rupees = (label: string) => z.string().trim()
  .regex(/^\d{1,13}(\.\d{1,2})?$/, `Enter ${label} in rupees, up to 2 decimals`);
const percent = z.string().trim()
  .regex(/^\d{1,2}(\.\d{1,4})?$/, "Enter a percentage below 100, up to 4 decimals");

/** Every withdrawal setting; fees and tax may be zero. */
export const withdrawalSettingsSchema = z.object({
  minimumAmount: rupees("the minimum").refine((v) => Number(v) >= 1, "The minimum must be at least ₹1"),
  feeFlat: rupees("the flat charge"),
  feePercent: percent,
  taxPercent: percent,
  dualApprovalThreshold: rupees("the threshold").refine((v) => Number(v) > 0, "The threshold must be above zero"),
  processingDays: z.string().trim().regex(/^\d{0,2}$/, "Enter whole days").refine((v) => v === "" || (Number(v) >= 0 && Number(v) <= 60), "Up to 60 days")
    .transform((v) => (v === "" ? 0 : Number(v))),
});
