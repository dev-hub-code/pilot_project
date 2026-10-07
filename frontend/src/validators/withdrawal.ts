import { z } from "zod";

/** Client-side mirrors of the backend's withdrawal validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

export const withdrawalSchema = z.object({
  bankAccountId: z.uuid("Choose a bank account"),
  amount,
});

export const markPaidSchema = z.object({
  payoutReference: z.string().trim().max(100).optional().transform((v) => v || undefined),
});

export const createBatchSchema = z.object({
  currency: z.string().regex(/^[A-Z]{3}$/, "Select a currency"),
});
