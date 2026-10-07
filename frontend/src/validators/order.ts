import { z } from "zod";

/** Client-side mirrors of the backend's checkout and payment validation; the backend is authoritative. */
const amount = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

export const cartItemSchema = z.object({ amount });

export const idempotencyKeySchema = z.string().regex(/^[A-Za-z0-9_-]{8,100}$/);

export const confirmTransferSchema = z.object({
  externalReference: z.string().trim().min(1, "Enter the bank's reference").max(100),
  amountReceived: amount,
});

export const refundSchema = z.object({
  reference: z.string().trim().min(1, "Enter the refund transfer's reference").max(100),
  reason: z.string().trim().min(3, "Give a reason").max(500),
});
