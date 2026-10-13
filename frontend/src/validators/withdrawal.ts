import { z } from "zod";

/** Client-side mirrors of the backend's withdrawal validation; the backend is authoritative. */

export const referralModeSchema = z.object({ mode: z.enum(["BANK_TRANSFER", "OFFICE_CASH"]) });

export const markPaidSchema = z.object({
  payoutReference: z.string().trim().max(100).optional().transform((v) => v || undefined),
});

export const receiptSchema = z.object({
  receiptNumber: z.string().trim().min(1, "Enter the cash receipt number").max(100),
});

export const resolveSchema = z.object({
  outcome: z.enum(["PAID", "RETURNED"]),
  note: z.string().trim().max(200).optional().transform((v) => v || undefined),
});

export const withdrawalDaysSchema = z.object({
  rentWithdrawalDay: z.coerce.number().int().min(2, "Choose a day from 2 to 28").max(28, "Choose a day from 2 to 28"),
  referralWithdrawalDay: z.coerce.number().int().min(2, "Choose a day from 2 to 28").max(28, "Choose a day from 2 to 28"),
});
